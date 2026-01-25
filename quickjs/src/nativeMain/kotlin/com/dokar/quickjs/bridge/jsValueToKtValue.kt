package com.dokar.quickjs.bridge

import com.dokar.quickjs.QuickJsException
import com.dokar.quickjs.binding.JsObject
import com.dokar.quickjs.binding.toJsObject
import com.dokar.quickjs.qjsError
import com.dokar.quickjs.util.freeJsValues
import com.dokar.quickjs.util.isInt8Array
import com.dokar.quickjs.util.isMap
import com.dokar.quickjs.util.isPromise
import com.dokar.quickjs.util.isSet
import com.dokar.quickjs.util.isUint8Array
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.CValue
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.alloc
import kotlinx.cinterop.allocArrayOfPointersTo
import kotlinx.cinterop.get
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.pointed
import kotlinx.cinterop.ptr
import kotlinx.cinterop.readBytes
import kotlinx.cinterop.toKStringFromUtf8
import kotlinx.cinterop.value
import platform.posix.double_tVar
import platform.posix.int32_tVar
import platform.posix.int64_tVar
import platform.posix.size_tVar
import platform.posix.uint32_tVar
import quickjs.JSContext
import quickjs.JSPropertyEnum
import quickjs.JSValue
import quickjs.JS_AtomToValue
import quickjs.JS_Call
import quickjs.JS_FreeAtom
import quickjs.JS_FreeCString
import quickjs.JS_FreeValue
import quickjs.JS_GPN_STRING_MASK
import quickjs.JS_GPN_SYMBOL_MASK
import quickjs.JS_GetArrayBuffer
import quickjs.JS_GetException
import quickjs.JS_GetGlobalObject
import quickjs.JS_GetOwnPropertyNames
import quickjs.JS_GetProperty
import quickjs.JS_GetPropertyStr
import quickjs.JS_GetPropertyUint32
import quickjs.JS_IsArray
import quickjs.JS_IsError
import quickjs.JS_IsException
import quickjs.JS_IsFunction
import quickjs.JS_IsString
import quickjs.JS_IsUndefined
import quickjs.JS_JSONStringify
import quickjs.JS_TAG_BOOL
import quickjs.JS_TAG_EXCEPTION
import quickjs.JS_TAG_FLOAT64
import quickjs.JS_TAG_FUNCTION_BYTECODE
import quickjs.JS_TAG_INT
import quickjs.JS_TAG_MODULE
import quickjs.JS_TAG_NULL
import quickjs.JS_TAG_OBJECT
import quickjs.JS_TAG_UNDEFINED
import quickjs.JS_TAG_UNINITIALIZED
import quickjs.JS_ToBool
import quickjs.JS_ToCString
import quickjs.JS_ToFloat64
import quickjs.JS_ToInt32
import quickjs.JS_ToInt64
import quickjs.JS_VALUE_IS_NAN
import quickjs.JS_WRITE_OBJ_BYTECODE
import quickjs.JS_WRITE_OBJ_REFERENCE
import quickjs.JS_WriteObject
import quickjs.JsUndefined
import quickjs.JsValueGetNormTag
import quickjs.JsValueGetPtr
import quickjs.js_free

@OptIn(ExperimentalForeignApi::class)
internal fun CValue<JSValue>.toKtValue(context: CPointer<JSContext>): Any? {
    val tag = JsValueGetNormTag(this)
    return when {
        tag == JS_TAG_NULL || tag == JS_TAG_UNDEFINED || tag == JS_TAG_UNINITIALIZED -> null

        tag == JS_TAG_EXCEPTION -> {
            throw JS_GetException(context).use(context) { jsErrorToKtError(context, this) }
        }

        tag == JS_TAG_BOOL -> {
            JS_ToBool(context, this) == 1
        }

        tag == JS_TAG_INT -> memScoped {
            val out = alloc<int64_tVar>()
            JS_ToInt64(context, out.ptr, this@toKtValue)
            out.value
        }

        tag == JS_TAG_FLOAT64 -> {
            if (JS_VALUE_IS_NAN(this) == 1) {
                Double.NaN
            } else {
                memScoped {
                    val out = alloc<double_tVar>()
                    JS_ToFloat64(context, out.ptr, this@toKtValue)
                    out.value
                }
            }
        }

        JS_IsString(this) == 1 -> {
            val buffer = JS_ToCString(context, this) ?: return null
            val string = buffer.toKStringFromUtf8()
            JS_FreeCString(context, buffer)
            string
        }

        tag == JS_TAG_FUNCTION_BYTECODE || tag == JS_TAG_MODULE -> {
            memScoped {
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
        }

        JS_IsArray(context, this) == 1 -> {
            jsArrayToKtList(context, this)
        }

        JS_IsError(context, this) == 1 -> {
            jsErrorToKtError(context, this)
        }

        tag == JS_TAG_OBJECT -> {
            val globalThis = JS_GetGlobalObject(context)
            try {
                when {
                    isPromise(context, globalThis) -> JsPromise(value = this)
                    isUint8Array(context, globalThis) -> jsUint8ArrayToKtUByteArray(context, this)
                    isInt8Array(context, globalThis) -> jsInt8ArrayToKtByteArray(context, this)
                    isSet(context, globalThis) -> jsSetToKtSet(context, this)
                    isMap(context, globalThis) -> jsMapToKtMap(context, this)
                    else -> jsObjectToKtJsObject(context, this)
                }
            } finally {
                JS_FreeValue(context, globalThis)
            }
        }

        else -> {
            qjsError("Cannot convert js type to kotlin type. JS value tag: $tag")
        }
    }
}

@OptIn(ExperimentalForeignApi::class)
internal fun CValue<JSValue>.toKtString(context: CPointer<JSContext>): String? {
    val tag = JsValueGetNormTag(this)
    if (tag == JS_TAG_NULL || tag == JS_TAG_UNDEFINED) return null
    val buffer = JS_ToCString(context, this) ?: return null
    val string = buffer.toKStringFromUtf8()
    JS_FreeCString(context, buffer)
    return string
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
        return newKtError(name, message, null)
    }
    if (JS_IsString(stack) == 1) {
        return stack.use(context) {
            newKtError(name, "$message\n\b${toKtString(context)}", null)
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
        return newKtError(name, message, null)
    }
    return memScoped {
        val lines = Array(stackLineCount) {
            val line = JS_GetPropertyUint32(context, stack, it.toUInt())
            line.use(context) { toKtString(context) }
        }
        JS_FreeValue(context, stack)
        newKtError(name, message, lines)
    }
}

private fun newKtError(name: String, message: String?, stack: Array<String?>?): Throwable {
    val m = if (!stack.isNullOrEmpty()) "$message\n${stack.joinToString("\n")}" else message
    // Try to restore the error class from the js error name
    @Suppress("DEPRECATION")
    return when (name) {
        Throwable::class.qualifiedName -> Throwable(m)
        Error::class.qualifiedName -> Error(m)
        Exception::class.qualifiedName -> Exception(m)
        RuntimeException::class.qualifiedName -> RuntimeException(m)
        NullPointerException::class.qualifiedName -> NullPointerException(m)
        NoSuchElementException::class.qualifiedName -> NoSuchElementException(m)
        IllegalArgumentException::class.qualifiedName -> IllegalStateException(m)
        IllegalStateException::class.qualifiedName -> IllegalStateException(m)
        UnsupportedOperationException::class.qualifiedName -> UnsupportedOperationException(m)
        IndexOutOfBoundsException::class.qualifiedName -> IndexOutOfBoundsException(m)
        ClassCastException::class.qualifiedName -> ClassCastException(m)
        ArithmeticException::class.qualifiedName -> ArithmeticException(m)
        AssertionError::class.qualifiedName -> AssertionError(m)
        OutOfMemoryError::class.qualifiedName -> OutOfMemoryError(m)
        NumberFormatException::class.qualifiedName -> NumberFormatException(m)
        ConcurrentModificationException::class.qualifiedName -> ConcurrentModificationException(m)
        NotImplementedError::class.qualifiedName -> NotImplementedError(m ?: "")
        QuickJsException::class.qualifiedName -> QuickJsException(m)
        else -> QuickJsException("$name: $m") // Unknown error, add the name back
    }
}

@OptIn(ExperimentalForeignApi::class)
private inline fun jsUint8ArrayToKtUByteArray(
    context: CPointer<JSContext>,
    array: CValue<JSValue>
): UByteArray {
    return jsInt8ArrayToKtByteArray(context, array).asUByteArray()
}

@OptIn(ExperimentalForeignApi::class)
private fun jsInt8ArrayToKtByteArray(
    context: CPointer<JSContext>,
    array: CValue<JSValue>
): ByteArray = memScoped {
    val length = alloc<size_tVar>()
    val arrayBuffer = JS_GetPropertyStr(context, array, "buffer")
    val cBuffer = JS_GetArrayBuffer(context, length.ptr, arrayBuffer)
    if (cBuffer == null) {
        JS_FreeValue(context, arrayBuffer)
        qjsError("Cannot read array buffer.")
    }
    try {
        cBuffer.readBytes(length.value.toInt())
    } finally {
        JS_FreeValue(context, arrayBuffer)
    }
}

@OptIn(ExperimentalForeignApi::class)
private fun jsArrayToKtList(
    context: CPointer<JSContext>,
    array: CValue<JSValue>
): List<Any?> = memScoped {
    val length = alloc<int64_tVar>()
    JS_GetPropertyStr(context, array, "length").use(context) {
        JS_ToInt64(context, length.ptr, this)
    }

    List(length.value.toInt()) {
        JS_GetPropertyUint32(context, array, it.toUInt()).use(context) {
            if (this.isTheSameObject(array)) {
                circularRefError()
            }
            toKtValue(context)
        }
    }
}

@OptIn(ExperimentalForeignApi::class)
private fun jsSetToKtSet(
    context: CPointer<JSContext>,
    set: CValue<JSValue>
): Set<Any?> {
    val result = mutableSetOf<Any?>()

    val keysFunc = JS_GetPropertyStr(context, set, "keys")
    val iterator = JS_Call(context, keysFunc, set, 0, null)
    val nextFunc = JS_GetPropertyStr(context, iterator, "next")
    while (true) {
        val entry = JS_Call(context, nextFunc, iterator, 0, null)
        val done = JS_GetPropertyStr(context, entry, "done")
        if (JS_ToBool(context, done) == 1) {
            freeJsValues(context, done, entry)
            break
        }
        val key = JS_GetPropertyStr(context, entry, "value")

        if (key.isTheSameObject(set)) {
            freeJsValues(context, key, done, entry)
            freeJsValues(context, nextFunc, iterator, keysFunc)
            circularRefError()
        }

        result.add(key.use(context) { toKtValue(context) })
        freeJsValues(context, done, entry)
    }

    freeJsValues(context, nextFunc, iterator, keysFunc)
    return result
}

@OptIn(ExperimentalForeignApi::class)
private fun jsMapToKtMap(
    context: CPointer<JSContext>,
    set: CValue<JSValue>
): Map<Any?, Any?> {
    val result = mutableMapOf<Any?, Any?>()

    val entriesFunc = JS_GetPropertyStr(context, set, "entries")
    val iterator = JS_Call(context, entriesFunc, set, 0, null)
    val nextFunc = JS_GetPropertyStr(context, iterator, "next")
    while (true) {
        val entry = JS_Call(context, nextFunc, iterator, 0, null)
        val done = JS_GetPropertyStr(context, entry, "done")
        if (JS_ToBool(context, done) == 1) {
            freeJsValues(context, done, entry)
            break
        }
        val entryVal = JS_GetPropertyStr(context, entry, "value")
        val key = JS_GetPropertyUint32(context, entryVal, 0.toUInt())
        val value = JS_GetPropertyUint32(context, entryVal, 1.toUInt())

        if (key.isTheSameObject(set) || value.isTheSameObject(set)) {
            freeJsValues(context, value, key, entryVal, done, entry)
            freeJsValues(context, nextFunc, iterator, entriesFunc)
            circularRefError()
        }

        result[key.use(context) { toKtValue(context) }] = value.use(context) { toKtValue(context) }
        freeJsValues(context, entryVal, done, entry)
    }

    freeJsValues(context, nextFunc, iterator, entriesFunc)
    return result
}

@OptIn(ExperimentalForeignApi::class)
private fun jsObjectToKtJsObject(
    context: CPointer<JSContext>,
    jsObject: CValue<JSValue>
): JsObject = memScoped {
    // Check circular refs
    val json = JS_JSONStringify(context, jsObject, JsUndefined(), JsUndefined())
    if (JS_IsException(json) == 1) {
        val jsError = JS_GetException(context)
        val jsErrorTag = JsValueGetNormTag(jsError)
        val error = if (jsErrorTag != JS_TAG_NULL && jsErrorTag != JS_TAG_UNINITIALIZED) {
            jsErrorToKtError(context, jsError)
        } else {
            null
        }
        freeJsValues(context, jsError, json)
        if (error != null) {
            throw error
        }
    } else {
        freeJsValues(context, json)
    }

    val result = mutableMapOf<String, Any?>()

    val props = allocArrayOfPointersTo<JSPropertyEnum>()
    val propLen = alloc<uint32_tVar>()
    JS_GetOwnPropertyNames(
        ctx = context,
        ptab = props,
        plen = propLen.ptr,
        obj = jsObject,
        flags = JS_GPN_STRING_MASK or JS_GPN_SYMBOL_MASK
    )

    val propsPointer = props.pointed.value!!

    for (i in 0..<propLen.value.toInt()) {
        val atom = propsPointer[i].atom
        val jsKey = JS_AtomToValue(context, atom)
        if (jsKey.isTheSameObject(jsObject)) {
            freeJsValues(context, jsKey)
            JS_FreeAtom(context, atom)
            continue
        }

        val key = jsKey.use(context) { toKtString(context) }
        if (key == null) {
            JS_FreeAtom(context, atom)
            continue
        }

        val jsValue = JS_GetProperty(context, jsObject, atom)
        JS_FreeAtom(context, atom)
        val value = if (jsValue.isTheSameObject(jsObject)) {
            jsValue.use(context) { toKtString(context) }
        } else if (JS_IsFunction(context, jsValue) == 1) {
            freeJsValues(context, jsValue)
            "[Function]"
        } else {
            jsValue.use(context) { toKtValue(context) }
        }
        result[key] = value
    }

    js_free(context, propsPointer)

    result.toJsObject()
}

@OptIn(ExperimentalForeignApi::class)
private fun CValue<JSValue>.isTheSameObject(other: CValue<JSValue>): Boolean {
    return JsValueGetNormTag(this) == JS_TAG_OBJECT &&
            JsValueGetNormTag(other) == JS_TAG_OBJECT &&
            JsValueGetPtr(this) == JsValueGetPtr(other)
}
