package com.dokar.quickjs.bridge

import cnames.structs.JSModuleDef
import com.dokar.quickjs.QuickJsException
import com.dokar.quickjs.qjsError
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.CValue
import kotlinx.cinterop.CValues
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.UByteVar
import kotlinx.cinterop.cstr
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.toCValues
import quickjs.JSContext
import quickjs.JS_EVAL_FLAG_ASYNC
import quickjs.JS_EVAL_FLAG_COMPILE_ONLY
import quickjs.JS_EVAL_TYPE_MODULE
import quickjs.JS_FreeAtom
import quickjs.JS_GetModuleName
import quickjs.JS_GetRuntime
import quickjs.JS_READ_OBJ_BYTECODE
import quickjs.JS_READ_OBJ_REFERENCE
import quickjs.JS_TAG_FUNCTION_BYTECODE
import quickjs.JS_TAG_MODULE
import quickjs.JS_TAG_NULL
import quickjs.JS_TAG_UNINITIALIZED
import quickjs.JS_UpdateStackTop
import quickjs.JsValueGetNormTag
import quickjs.JsValueGetPtr
import quickjs.QJSValue
import quickjs.qjs_atom_to_string
import quickjs.qjs_eval
import quickjs.qjs_eval_function
import quickjs.qjs_free_value
import quickjs.qjs_get_exception
import quickjs.qjs_read_object
import quickjs.qjs_resolve_module

@OptIn(ExperimentalForeignApi::class)
@Suppress("UNCHECKED_CAST")
private fun ByteArray.toBytecodeBuffer(): CValues<UByteVar> =
    toCValues() as CValues<UByteVar>

@OptIn(ExperimentalForeignApi::class)
@Throws(QuickJsException::class)
internal fun CPointer<JSContext>.compile(
    code: String,
    filename: String,
    asModule: Boolean
): ByteArray {
    var evalFlags = JS_EVAL_FLAG_COMPILE_ONLY or JS_EVAL_FLAG_ASYNC
    if (asModule) {
        evalFlags = evalFlags or JS_EVAL_TYPE_MODULE
    }
    val cStr = code.cstr
    JS_UpdateStackTop(JS_GetRuntime(this))
    val result = qjs_eval(
        ctx = this,
        input = cStr,
        input_len = (cStr.size - 1).toULong(),
        filename = filename.cstr,
        eval_flags = evalFlags,
    )
    return result.use(context = this) {
        checkContextException(this@compile)
        val tag = JsValueGetNormTag(result)
        if (tag != JS_TAG_FUNCTION_BYTECODE && tag != JS_TAG_MODULE) {
            qjsError("Failed to compile code, unsupported result type with tag: $tag")
        }
        toKtValue(context = this@compile)
    } as? ByteArray ?: qjsError("Failed to read bytecode.")
}

/**
 * Resolves an ES module bytecode graph without evaluating it.
 *
 * @return The module name stored in the entry bytecode.
 */
@OptIn(ExperimentalForeignApi::class)
@Throws(QuickJsException::class)
internal fun CPointer<JSContext>.resolveModuleGraph(bytecode: ByteArray): String = memScoped {
    val buffer = bytecode.toBytecodeBuffer()
    JS_UpdateStackTop(JS_GetRuntime(this@resolveModuleGraph))
    val entry = qjs_read_object(
        ctx = this@resolveModuleGraph,
        buf = buffer,
        buf_len = buffer.size.toULong(),
        flags = JS_READ_OBJ_BYTECODE or JS_READ_OBJ_REFERENCE,
    )
    entry.use(this@resolveModuleGraph) {
        checkContextException(this@resolveModuleGraph)
        if (JsValueGetNormTag(this) != JS_TAG_MODULE) {
            qjsError("Bytecode is not an ES module.")
        }
        val moduleDefinition = JsValueGetPtr(this)?.reinterpret<JSModuleDef>()
            ?: qjsError("Cannot read the ES module definition.")
        val nameAtom = JS_GetModuleName(this@resolveModuleGraph, moduleDefinition)
        val nameValue = qjs_atom_to_string(this@resolveModuleGraph, nameAtom)
        JS_FreeAtom(this@resolveModuleGraph, nameAtom)
        val name = nameValue.use(this@resolveModuleGraph) {
            checkContextException(this@resolveModuleGraph)
            toKtValue(this@resolveModuleGraph) as? String
                ?: qjsError("Cannot read the ES module name.")
        }
        if (qjs_resolve_module(this@resolveModuleGraph, this) < 0) {
            checkContextException(this@resolveModuleGraph)
            qjsError("Cannot resolve ES module entry bytecode.")
        }
        name
    }
}

@OptIn(ExperimentalForeignApi::class)
@Throws(QuickJsException::class)
internal fun CPointer<JSContext>.evaluate(
    code: String,
    filename: String,
    asModule: Boolean,
): JsPromise {
    val context = this@evaluate
    var evalFlags = JS_EVAL_FLAG_ASYNC
    if (asModule) {
        evalFlags = evalFlags or JS_EVAL_TYPE_MODULE
    }
    val cStr = code.cstr
    JS_UpdateStackTop(JS_GetRuntime(context))
    val result = qjs_eval(
        ctx = this,
        input = cStr,
        input_len = (cStr.size - 1).toULong(),
        filename = filename.cstr,
        eval_flags = evalFlags,
    )
    return handleEvalResult(context, result)
}

@OptIn(ExperimentalForeignApi::class)
@Throws(QuickJsException::class)
internal fun CPointer<JSContext>.evaluate(
    bytecode: ByteArray
): JsPromise = memScoped {
    val context = this@evaluate

    val buffer = bytecode.toBytecodeBuffer()
    JS_UpdateStackTop(JS_GetRuntime(context))
    val jsValue = qjs_read_object(
        ctx = context,
        buf = buffer,
        buf_len = buffer.size.toULong(),
        flags = JS_READ_OBJ_BYTECODE or JS_READ_OBJ_REFERENCE,
    )
    try {
        checkContextException(context)
        if (JsValueGetNormTag(jsValue) == JS_TAG_MODULE &&
            qjs_resolve_module(context, jsValue) < 0
        ) {
            checkContextException(context)
            qjsError("Cannot resolve module bytecode.")
        }
    } catch (error: Throwable) {
        qjs_free_value(context, jsValue)
        throw error
    }
    val result = qjs_eval_function(
        ctx = context,
        fun_obj = jsValue,
    )
    handleEvalResult(context, result)
}

@OptIn(ExperimentalForeignApi::class)
private fun handleEvalResult(
    context: CPointer<JSContext>,
    result: CValue<QJSValue>
): JsPromise {
    try {
        JS_UpdateStackTop(JS_GetRuntime(context))
        checkContextException(context)
        val ktValue = result.toKtValue(context)
        if (ktValue !is JsPromise) {
            qjsError("Missing async flag to eval")
        }
        return ktValue
    } catch (e: Throwable) {
        qjs_free_value(context, result)
        throw e
    }
}

@OptIn(ExperimentalForeignApi::class)
internal fun checkContextException(context: CPointer<JSContext>) {
    qjs_get_exception(context).use(context) {
        val tag = JsValueGetNormTag(this)
        if (tag != JS_TAG_NULL && tag != JS_TAG_UNINITIALIZED) {
            throw jsErrorToKtError(context, this)
        }
    }
}
