package com.dokar.quickjs.bridge

import cnames.structs.JSModuleDef
import com.dokar.quickjs.ModuleContent
import com.dokar.quickjs.ModuleImportType
import com.dokar.quickjs.QuickJs
import com.dokar.quickjs.qjsError
import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.COpaquePointer
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.CValues
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.StableRef
import kotlinx.cinterop.UByteVar
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.asStableRef
import kotlinx.cinterop.cstr
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.set
import kotlinx.cinterop.staticCFunction
import kotlinx.cinterop.toCValues
import kotlinx.cinterop.toKStringFromUtf8
import kotlinx.cinterop.usePinned
import quickjs.JSContext
import quickjs.JSRuntime
import quickjs.JS_EVAL_FLAG_COMPILE_ONLY
import quickjs.JS_EVAL_TYPE_MODULE
import quickjs.JS_FreeAtom
import quickjs.JS_GetModuleName
import quickjs.JS_READ_OBJ_BYTECODE
import quickjs.JS_READ_OBJ_REFERENCE
import quickjs.JS_TAG_MODULE
import quickjs.JsValueGetNormTag
import quickjs.JsValueGetPtr
import quickjs.QJS_MODULE_IMPORT_TYPE_BYTES
import quickjs.QJS_MODULE_IMPORT_TYPE_JS
import quickjs.QJS_MODULE_IMPORT_TYPE_JSON
import quickjs.QJS_MODULE_IMPORT_TYPE_TEXT
import quickjs.QjsModuleImportType
import quickjs.js_malloc
import quickjs.qjs_atom_to_string
import quickjs.qjs_eval
import quickjs.qjs_is_exception
import quickjs.qjs_module_loader_install
import quickjs.qjs_new_bytes_module
import quickjs.qjs_new_json_module
import quickjs.qjs_new_text_module
import quickjs.qjs_read_object
import quickjs.qjs_throw
import quickjs.qjs_throw_type_error

/** Compiles source returned by the runtime-scoped module loader. */
@OptIn(ExperimentalForeignApi::class)
private fun compileSourceModule(
    context: CPointer<JSContext>,
    quickJs: QuickJs,
    name: String,
    source: String,
): CPointer<JSModuleDef>? {
    val sourceString = source.cstr
    val compiled = qjs_eval(
        ctx = context,
        input = sourceString,
        input_len = (sourceString.size - 1).toULong(),
        filename = name.cstr,
        eval_flags = JS_EVAL_TYPE_MODULE or JS_EVAL_FLAG_COMPILE_ONLY,
    )
    return compiled.use(context) {
        // Preserve the syntax exception already stored on the context.
        if (qjs_is_exception(this) == 1) return@use null
        if (JsValueGetNormTag(this) != JS_TAG_MODULE) {
            qjsError("Module loader returned unsupported source for '$name'.")
        }
        val bytecode = toKtValue(context) as? ByteArray
            ?: qjsError("Cannot write bytecode for module '$name'.")
        quickJs.onModuleCompiled(name, bytecode)
        JsValueGetPtr(this)?.reinterpret<JSModuleDef>()
            ?: qjsError("Cannot read the ES module definition for '$name'.")
    }
}

/** Reads cached bytecode and verifies that it belongs to the requested name. */
@OptIn(ExperimentalForeignApi::class)
@Suppress("UNCHECKED_CAST")
private fun readBytecodeModule(
    context: CPointer<JSContext>,
    name: String,
    bytecode: ByteArray,
): CPointer<JSModuleDef>? = memScoped {
    val buffer = bytecode.toCValues() as CValues<UByteVar>
    val module = qjs_read_object(
        ctx = context,
        buf = buffer,
        buf_len = buffer.size.toULong(),
        flags = JS_READ_OBJ_BYTECODE or JS_READ_OBJ_REFERENCE,
    )
    module.use(context) {
        // Preserve the bytecode exception already stored on the context.
        if (qjs_is_exception(this) == 1) return@use null
        if (JsValueGetNormTag(this) != JS_TAG_MODULE) {
            qjsError("Bytecode for '$name' is not an ES module.")
        }
        val definition = JsValueGetPtr(this)?.reinterpret<JSModuleDef>()
            ?: qjsError("Cannot read the ES module definition for '$name'.")
        val bytecodeName = readModuleName(context, definition)
        if (bytecodeName != name) {
            qjsError("Module bytecode name '$bytecodeName' does not match '$name'.")
        }
        definition
    }
}

/** Creates a text data module preserving embedded NUL and UTF-8 bytes. */
@OptIn(ExperimentalForeignApi::class)
private fun createTextModule(
    context: CPointer<JSContext>,
    name: String,
    text: String,
): CPointer<JSModuleDef>? {
    val textCstr = text.cstr
    return qjs_new_text_module(
        ctx = context,
        module_name = name.cstr,
        utf8 = textCstr,
        utf8_len = (textCstr.size - 1).toULong(),
    )
}

/** Creates a JSON data module parsed strictly by QuickJS. */
@OptIn(ExperimentalForeignApi::class)
private fun createJsonModule(
    context: CPointer<JSContext>,
    name: String,
    json: String,
): CPointer<JSModuleDef>? {
    val jsonCstr = json.cstr
    return qjs_new_json_module(
        ctx = context,
        module_name = name.cstr,
        utf8_nul_terminated = jsonCstr,
        utf8_len = (jsonCstr.size - 1).toULong(),
    )
}

/** Creates a bytes data module backed by an immutable Uint8Array copy. */
@OptIn(ExperimentalForeignApi::class)
private fun createBytesModule(
    context: CPointer<JSContext>,
    name: String,
    bytes: ByteArray,
): CPointer<JSModuleDef>? {
    if (bytes.isEmpty()) {
        return qjs_new_bytes_module(
            ctx = context,
            module_name = name,
            bytes = null,
            len = 0uL,
        )
    }
    return bytes.usePinned { pinned ->
        qjs_new_bytes_module(
            ctx = context,
            module_name = name,
            bytes = pinned.addressOf(0).reinterpret(),
            len = bytes.size.toULong(),
        )
    }
}

/** Reads the normalized name retained by a compiled module definition. */
@OptIn(ExperimentalForeignApi::class)
private fun readModuleName(
    context: CPointer<JSContext>,
    module: CPointer<JSModuleDef>,
): String {
    val nameAtom = JS_GetModuleName(context, module)
    val nameValue = qjs_atom_to_string(context, nameAtom)
    JS_FreeAtom(context, nameAtom)
    return nameValue.use(context) {
        checkContextException(context)
        toKtValue(context) as? String
            ?: qjsError("Cannot read the ES module name.")
    }
}

/** Reports a loader failure and forwards the original or callback error to QuickJS. */
@OptIn(ExperimentalForeignApi::class)
private fun throwModuleLoadFailure(
    context: CPointer<JSContext>,
    quickJs: QuickJs,
    name: String,
    type: ModuleImportType,
    error: Throwable,
    failureVersion: Long,
): CPointer<JSModuleDef>? {
    val failure = if (failureVersion == quickJs.moduleLoadFailureVersion) {
        try {
            quickJs.onModuleLoadFailed(name, type)
            error
        } catch (callbackError: Throwable) {
            callbackError
        }
    } else {
        error
    }
    qjs_throw(context, ktErrorToJsError(context, failure))
    return null
}

@OptIn(ExperimentalForeignApi::class)
private fun QjsModuleImportType.toKtImportType(): ModuleImportType = when (this) {
    QJS_MODULE_IMPORT_TYPE_JS -> ModuleImportType.Js
    QJS_MODULE_IMPORT_TYPE_TEXT -> ModuleImportType.Text
    QJS_MODULE_IMPORT_TYPE_JSON -> ModuleImportType.Json
    QJS_MODULE_IMPORT_TYPE_BYTES -> ModuleImportType.Bytes
    else -> qjsError("Unsupported module import type: $this")
}

/** Resolves a module specifier through the runtime-scoped normalizer. */
@OptIn(ExperimentalForeignApi::class)
private fun normalizeModule(
    context: CPointer<JSContext>?,
    baseName: CPointer<ByteVar>?,
    requestedName: CPointer<ByteVar>?,
    opaque: COpaquePointer?,
): CPointer<ByteVar>? {
    val jsContext = context ?: return null
    val owner = opaque?.asStableRef<QuickJs>()?.get()
    return try {
        val base = baseName?.toKStringFromUtf8()
            ?: qjsError("Missing ES module base name.")
        val requested = requestedName?.toKStringFromUtf8()
            ?: qjsError("Missing ES module requested name.")
        val quickJs = owner ?: qjsError("Missing QuickJs module normalizer state.")
        if (quickJs.checkInterruptOrThrow(jsContext)) {
            return null
        }
        val normalized = quickJs.normalizeModule(base, requested)
        val bytes = normalized.encodeToByteArray()
        val result = js_malloc(jsContext, (bytes.size + 1).toULong())
            ?.reinterpret<ByteVar>()
            ?: return null
        for (index in bytes.indices) {
            result[index] = bytes[index]
        }
        result[bytes.size] = 0
        result
    } catch (error: Throwable) {
        qjs_throw(jsContext, ktErrorToJsError(jsContext, error))
        // An interrupt raised during the callback must stay uncatchable.
        owner?.checkInterruptOrThrow(jsContext)
        null
    }
}

@OptIn(ExperimentalForeignApi::class)
private fun loadModule(
    context: CPointer<JSContext>?,
    moduleName: CPointer<ByteVar>?,
    importType: QjsModuleImportType,
    opaque: COpaquePointer?,
): CPointer<JSModuleDef>? {
    val jsContext = context ?: return null
    val quickJs = opaque?.asStableRef<QuickJs>()?.get()
    val (name, type, owner) = try {
        val resolvedName = moduleName?.toKStringFromUtf8()
            ?: qjsError("Missing ES module name.")
        val resolvedOwner = quickJs ?: qjsError("Missing QuickJs module loader state.")
        if (resolvedOwner.checkInterruptOrThrow(jsContext)) {
            return null
        }
        Triple(resolvedName, importType.toKtImportType(), resolvedOwner)
    } catch (error: Throwable) {
        qjs_throw(jsContext, ktErrorToJsError(jsContext, error))
        return null
    }
    val failureVersion = owner.moduleLoadFailureVersion
    val definition = try {
        val content = owner.loadModule(name, type)
            ?: qjsError("could not load module '$name'")
        when {
            type == ModuleImportType.Js && content is ModuleContent.Source -> compileSourceModule(
                context = jsContext,
                quickJs = owner,
                name = name,
                source = content.code,
            )

            type == ModuleImportType.Js && content is ModuleContent.Bytecode -> readBytecodeModule(
                context = jsContext,
                name = name,
                bytecode = content.bytes,
            )

            type == ModuleImportType.Text && content is ModuleContent.Text -> createTextModule(
                context = jsContext,
                name = name,
                text = content.text,
            )

            type == ModuleImportType.Json && content is ModuleContent.Json -> createJsonModule(
                context = jsContext,
                name = name,
                json = content.json,
            )

            type == ModuleImportType.Bytes && content is ModuleContent.Bytes -> createBytesModule(
                context = jsContext,
                name = name,
                bytes = content.bytes,
            )

            else -> {
                qjs_throw_type_error(
                    jsContext,
                    "Module '$name' was imported as '${type.name.lowercase()}', but loader returned incompatible content.",
                )
                null
            }
        }
    } catch (error: Throwable) {
        // An interrupt is not a load failure: keep it uncatchable and don't notify.
        if (owner.checkInterruptOrThrow(jsContext)) return null
        return throwModuleLoadFailure(
            context = jsContext,
            quickJs = owner,
            name = name,
            type = type,
            error = error,
            failureVersion = failureVersion,
        )
    }
    if (definition == null && owner.checkInterruptOrThrow(jsContext)) return null
    if (definition == null && failureVersion == owner.moduleLoadFailureVersion) {
        try {
            owner.onModuleLoadFailed(name, type)
        } catch (error: Throwable) {
            qjs_throw(jsContext, ktErrorToJsError(jsContext, error))
        }
    }
    return definition
}

/** Installs the runtime-scoped host module loader and returns its native state handle. */
@OptIn(ExperimentalForeignApi::class)
internal fun installModuleLoader(
    quickJs: StableRef<QuickJs>,
    runtime: CPointer<JSRuntime>,
    interruptState: COpaquePointer?,
): COpaquePointer? {
    val normalize = if (quickJs.get().hasModuleNormalizer()) {
        staticCFunction(::normalizeModule)
    } else {
        null
    }
    return qjs_module_loader_install(
        rt = runtime,
        module_normalize = normalize,
        module_loader = staticCFunction(::loadModule),
        opaque = quickJs.asCPointer(),
        interrupt_state = interruptState,
    )
}
