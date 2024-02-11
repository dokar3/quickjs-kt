package com.dokar.quickjs.bridge

import com.dokar.quickjs.JSContext
import com.dokar.quickjs.JSValue
import com.dokar.quickjs.JS_FreeValue
import com.dokar.quickjs.JS_GetPropertyStr
import com.dokar.quickjs.JS_GetPropertyUint32
import com.dokar.quickjs.JS_GetPrototype
import com.dokar.quickjs.JS_IsError
import com.dokar.quickjs.JS_IsString
import com.dokar.quickjs.JS_IsUndefined
import com.dokar.quickjs.JS_TAG_BOOL
import com.dokar.quickjs.JS_TAG_FLOAT64
import com.dokar.quickjs.JS_TAG_FUNCTION_BYTECODE
import com.dokar.quickjs.JS_TAG_INT
import com.dokar.quickjs.JS_TAG_MODULE
import com.dokar.quickjs.JS_TAG_NULL
import com.dokar.quickjs.JS_TAG_OBJECT
import com.dokar.quickjs.JS_TAG_STRING
import com.dokar.quickjs.JS_TAG_UNDEFINED
import com.dokar.quickjs.JS_ToCString
import com.dokar.quickjs.JS_ToFloat64
import com.dokar.quickjs.JS_ToInt32
import com.dokar.quickjs.JS_ToInt64
import com.dokar.quickjs.JS_VALUE_IS_NAN
import com.dokar.quickjs.JS_WRITE_OBJ_BYTECODE
import com.dokar.quickjs.JS_WRITE_OBJ_REFERENCE
import com.dokar.quickjs.JS_WriteObject
import com.dokar.quickjs.JsTrue
import com.dokar.quickjs.JsValueGetNormTag
import com.dokar.quickjs.QuickJsException
import com.dokar.quickjs.js_free
import com.dokar.quickjs.qjsError
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.CValue
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.readBytes
import kotlinx.cinterop.toKStringFromUtf8
import kotlinx.cinterop.value
import platform.posix.double_tVar
import platform.posix.int32_tVar
import platform.posix.int64_tVar
import platform.posix.size_tVar

@OptIn(ExperimentalForeignApi::class)
internal fun CValue<JSValue>.toKtValue(context: CPointer<JSContext>): Any? {
    val tag = JsValueGetNormTag(this)
    if (tag == JS_TAG_NULL || tag == JS_TAG_UNDEFINED) {
        return null
    } else if (tag == JS_TAG_BOOL) {
        return this == JsTrue()
    } else if (tag == JS_TAG_INT) {
        memScoped {
            val out = alloc<int64_tVar>()
            JS_ToInt64(context, out.ptr, this@toKtValue)
            return out.value
        }
    } else if (tag == JS_TAG_FLOAT64) {
        if (JS_VALUE_IS_NAN(this) == 1) {
            return Double.NaN
        }
        memScoped {
            val out = alloc<double_tVar>()
            JS_ToFloat64(context, out.ptr, this@toKtValue)
            return out.value
        }
    } else if (tag == JS_TAG_STRING) {
        val buffer = JS_ToCString(context, this) ?: return null
        return buffer.toKStringFromUtf8()
    } else if (tag == JS_TAG_FUNCTION_BYTECODE || tag == JS_TAG_MODULE) {
        return memScoped {
            val flags = JS_WRITE_OBJ_BYTECODE or JS_WRITE_OBJ_REFERENCE
            val length = alloc<size_tVar>()
            val bufferPtr = JS_WriteObject(
                ctx = context,
                psize = length.ptr,
                obj = this@toKtValue,
                flags = flags
            ) ?: qjsError("Cannot write bytecode from the result.")
            val buffer = bufferPtr.readBytes(length.value.toInt())
            js_free(ctx = context, ptr = bufferPtr)
            buffer
        }
    } else if (JS_IsError(context, this) == 1) {
        return jsErrorToKtError(context, this)
    } else if (tag == JS_TAG_OBJECT) {
        val prototype = JS_GetPrototype(context, this)
        return when (prototype.use(context) { toKtString(context) }) {
            "[object Promise]" -> JsPromise(value = this)
            else -> qjsError("Mapping an unsupported js object: ${toKtString(context)}")
        }
    } else {
        qjsError("Cannot convert js type to kotlin type. JS value tag: $tag")
    }
}

@OptIn(ExperimentalForeignApi::class)
internal fun CValue<JSValue>.toKtString(context: CPointer<JSContext>): String? {
    val tag = JsValueGetNormTag(this)
    if (tag == JS_TAG_NULL || tag == JS_TAG_UNDEFINED) return null
    val buffer = JS_ToCString(context, this)
    return buffer?.toKStringFromUtf8()
}

@OptIn(ExperimentalForeignApi::class)
internal fun jsErrorToKtError(context: CPointer<JSContext>, error: CValue<JSValue>): Throwable {
    val name = JS_GetPropertyStr(context, error, "name")
        .use(context = context) { toKtString(context) }
        ?: return QuickJsException(error.toKtString(context) ?: "<NULL>")
    val message = JS_GetPropertyStr(context, error, "message")
        .use(context) { toKtString(context) }
    val stack = JS_GetPropertyStr(context, error, "stack")
    if (JS_IsUndefined(stack) == 1) {
        JS_FreeValue(context, stack)
        return QuickJsException("$name: $message")
    }
    if (JS_IsString(stack) == 1) {
        return stack.use(context) {
            QuickJsException("$name: $message\n\b${toKtString(context)}")
        }
    }
    val stackLineCount = JS_GetPropertyStr(context, stack, "length").use(context) {
        if (JsValueGetNormTag(this) == JS_TAG_INT) {
            memScoped {
                val stackLineCount = alloc<int32_tVar>()
                JS_ToInt32(context, stackLineCount.ptr, this@use)
                stackLineCount.value
            }
        } else {
            null
        }
    }
    if (stackLineCount == null) {
        JS_FreeValue(context, stack)
        return QuickJsException("$name: $message")
    }
    return memScoped {
        val lines = Array(stackLineCount) {
            val line = JS_GetPropertyUint32(context, stack, it.toUInt())
            line.use(context) { toKtString(context) }
        }
        JS_FreeValue(context, stack)
        QuickJsException("$name: $message\n${lines.joinToString("\n")}")
    }
}