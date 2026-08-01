package com.dokar.quickjs.bridge

import cnames.structs.JSModuleDef
import com.dokar.quickjs.ModuleContent
import com.dokar.quickjs.QuickJs
import com.dokar.quickjs.qjsError
import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.COpaquePointer
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.CValues
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.StableRef
import kotlinx.cinterop.UByteVar
import kotlinx.cinterop.asStableRef
import kotlinx.cinterop.cstr
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.staticCFunction
import kotlinx.cinterop.toCValues
import kotlinx.cinterop.toKStringFromUtf8
import quickjs.JSContext
import quickjs.JSRuntime
import quickjs.JS_AtomToString
import quickjs.JS_EVAL_FLAG_COMPILE_ONLY
import quickjs.JS_EVAL_TYPE_MODULE
import quickjs.JS_Eval
import quickjs.JS_FreeAtom
import quickjs.JS_GetModuleName
import quickjs.JS_IsException
import quickjs.JS_READ_OBJ_BYTECODE
import quickjs.JS_READ_OBJ_REFERENCE
import quickjs.JS_ReadObject
import quickjs.JS_SetModuleLoaderFunc
import quickjs.JS_TAG_MODULE
import quickjs.JS_Throw
import quickjs.JsValueGetNormTag
import quickjs.JsValueGetPtr

/** Compiles source returned by the runtime-scoped module loader. */
@OptIn(ExperimentalForeignApi::class)
private fun compileSourceModule(
    context: CPointer<JSContext>,
    quickJs: QuickJs,
    name: String,
    source: String,
): CPointer<JSModuleDef>? {
    val sourceString = source.cstr
    val compiled = JS_Eval(
        ctx = context,
        input = sourceString,
        input_len = (sourceString.size - 1).toULong(),
        filename = name.cstr,
        eval_flags = JS_EVAL_TYPE_MODULE or JS_EVAL_FLAG_COMPILE_ONLY,
    )
    return compiled.use(context) {
        // Preserve the syntax exception already stored on the context.
        if (JS_IsException(this) == 1) return@use null
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
    val module = JS_ReadObject(
        ctx = context,
        buf = buffer,
        buf_len = buffer.size.toULong(),
        flags = JS_READ_OBJ_BYTECODE or JS_READ_OBJ_REFERENCE,
    )
    module.use(context) {
        // Preserve the bytecode exception already stored on the context.
        if (JS_IsException(this) == 1) return@use null
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

/** Reads the normalized name retained by a compiled module definition. */
@OptIn(ExperimentalForeignApi::class)
private fun readModuleName(
    context: CPointer<JSContext>,
    module: CPointer<JSModuleDef>,
): String {
    val nameAtom = JS_GetModuleName(context, module)
    val nameValue = JS_AtomToString(context, nameAtom)
    JS_FreeAtom(context, nameAtom)
    return nameValue.use(context) {
        checkContextException(context)
        toKtValue(context) as? String
            ?: qjsError("Cannot read the ES module name.")
    }
}

@OptIn(ExperimentalForeignApi::class)
private fun loadModule(
  context: CPointer<JSContext>?,
  moduleName: CPointer<ByteVar>?,
  opaque: COpaquePointer?,
): CPointer<JSModuleDef>? {
  val jsContext = context ?: return null
  val quickJs = opaque?.asStableRef<QuickJs>()?.get()
  return try {
    val name = moduleName?.toKStringFromUtf8()
      ?: qjsError("Missing ES module name.")
    val owner = quickJs ?: qjsError("Missing QuickJs module loader state.")
    when (val content = owner.loadModule(name)
      ?: qjsError("could not load module '$name'")) {
      is ModuleContent.Source -> compileSourceModule(
        context = jsContext,
        quickJs = owner,
        name = name,
        source = content.code,
      )

      is ModuleContent.Bytecode -> readBytecodeModule(
        context = jsContext,
        name = name,
        bytecode = content.bytes,
      )
    }
  } catch (error: Throwable) {
    JS_Throw(jsContext, ktErrorToJsError(jsContext, error))
    null
  }
}

/** Installs the runtime-scoped host module loader. */
@OptIn(ExperimentalForeignApi::class)
internal fun setModuleLoader(
    quickJs: StableRef<QuickJs>,
    runtime: CPointer<JSRuntime>,
) {
    JS_SetModuleLoaderFunc(
        rt = runtime,
        module_normalize = null,
        module_loader = staticCFunction(::loadModule),
        opaque = quickJs.asCPointer(),
    )
}
