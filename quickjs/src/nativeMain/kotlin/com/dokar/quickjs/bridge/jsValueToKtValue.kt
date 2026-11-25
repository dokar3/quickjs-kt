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
import quickjs.JS_FreeAtom
import quickjs.JS_FreeCString
import quickjs.JS_GPN_STRING_MASK
import quickjs.JS_GPN_SYMBOL_MASK
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
import quickjs.JS_WRITE_OBJ_BYTECODE
import quickjs.JS_WRITE_OBJ_REFERENCE
import quickjs.JsUndefined
import quickjs.JsValueGetNormTag
import quickjs.JsValueGetPtr
import quickjs.QJSValue
import quickjs.js_free
import quickjs.qjs_atom_to_value
import quickjs.qjs_call
import quickjs.qjs_free_value
import quickjs.qjs_get_array_buffer
import quickjs.qjs_get_exception
import quickjs.qjs_get_global_object
import quickjs.qjs_get_own_property_names
import quickjs.qjs_get_property
import quickjs.qjs_get_property_str
import quickjs.qjs_get_property_uint32
import quickjs.qjs_is_array
import quickjs.qjs_is_error
import quickjs.qjs_is_exception
import quickjs.qjs_is_function
import quickjs.qjs_is_string
import quickjs.qjs_is_undefined
import quickjs.qjs_json_stringify
import quickjs.qjs_to_bool
import quickjs.qjs_to_c_string
import quickjs.qjs_to_float64
import quickjs.qjs_to_int32
import quickjs.qjs_to_int64
import quickjs.qjs_value_is_nan
import quickjs.qjs_write_object

@OptIn(ExperimentalForeignApi::class)
internal fun CValue<QJSValue>.toKtValue(context: CPointer<JSContext>): Any? {
    val tag = JsValueGetNormTag(this)
    return when {
        tag == JS_TAG_NULL || tag == JS_TAG_UNDEFINED || tag == JS_TAG_UNINITIALIZED -> null

        tag == JS_TAG_EXCEPTION -> {
            throw qjs_get_exception(context).use(context) { jsErrorToKtError(context, this) }
        }

        tag == JS_TAG_BOOL -> {
            qjs_to_bool(context, this) == 1
        }

        tag == JS_TAG_INT -> memScoped {
            val out = alloc<int64_tVar>()
            qjs_to_int64(context, out.ptr, this@toKtValue)
            out.value
        }

        tag == JS_TAG_FLOAT64 -> {
            if (qjs_value_is_nan(this) == 1) {
                Double.NaN
            } else {
                memScoped {
                    val out = alloc<double_tVar>()
                    qjs_to_float64(context, out.ptr, this@toKtValue)
                    out.value
                }
            }
        }

        qjs_is_string(this) == 1 -> {
            val buffer = qjs_to_c_string(context, this) ?: return null
            val string = buffer.toKStringFromUtf8()
            JS_FreeCString(context, buffer)
            string
        }

        tag == JS_TAG_FUNCTION_BYTECODE || tag == JS_TAG_MODULE -> {
            memScoped {
                val flags = JS_WRITE_OBJ_BYTECODE or JS_WRITE_OBJ_REFERENCE
                val length = alloc<size_tVar>()
                val bufferPtr = qjs_write_object(
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

        qjs_is_array(context, this) == 1 -> {
            jsArrayToKtList(context, this)
        }

        qjs_is_error(context, this) == 1 -> {
            jsErrorToKtError(context, this)
        }

        tag == JS_TAG_OBJECT -> {
            val globalThis = qjs_get_global_object(context)
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
                qjs_free_value(context, globalThis)
            }
        }

        else -> {
            qjsError("Cannot convert js type to kotlin type. JS value tag: $tag")
        }
    }
}

@OptIn(ExperimentalForeignApi::class)
internal fun CValue<QJSValue>.toKtString(context: CPointer<JSContext>): String? {
    val tag = JsValueGetNormTag(this)
    if (tag == JS_TAG_NULL || tag == JS_TAG_UNDEFINED) return null
    val buffer = qjs_to_c_string(context, this) ?: return null
    val string = buffer.toKStringFromUtf8()
    JS_FreeCString(context, buffer)
    return string
}

@OptIn(ExperimentalForeignApi::class)
internal fun jsErrorToKtError(context: CPointer<JSContext>, error: CValue<QJSValue>): Throwable {
    val name = qjs_get_property_str(context, error, "name")
        .use(context = context) { toKtString(context) }
        ?: return QuickJsException(error.toKtString(context) ?: "<NULL>")
    val message = qjs_get_property_str(context, error, "message")
        .use(context) { toKtString(context) }
    val stackValue = qjs_get_property_str(context, error, "stack")
    val stack = when {
        qjs_is_undefined(stackValue) == 1 -> {
            qjs_free_value(context, stackValue)
            null
        }

        qjs_is_string(stackValue) == 1 -> stackValue.use(context) { toKtString(context) }
        // Fallback for array-shaped custom stacks
        else -> stackValue.use(context) { joinStackFrames(context, this) }
    }
    return newKtError(name, message, stack)
}

@OptIn(ExperimentalForeignApi::class)
private fun joinStackFrames(context: CPointer<JSContext>, stack: CValue<QJSValue>): String? {
    val frameCount = qjs_get_property_str(context, stack, "length").use(context) {
        if (JsValueGetNormTag(this) == JS_TAG_INT) {
            memScoped {
                val count = alloc<int32_tVar>()
                qjs_to_int32(context, count.ptr, this@use)
                count.value
            }
        } else {
            null
        }
    } ?: return null
    val frames = (0 until frameCount).mapNotNull { index ->
        qjs_get_property_uint32(context, stack, index.toUInt()).use(context) { toKtString(context) }
    }
    return frames.joinToString("\n").takeIf { it.isNotEmpty() }
}

private fun newKtError(name: String, message: String?, stack: String?): Throwable {
    val messageWithStack = if (!stack.isNullOrEmpty()) "$message\n$stack" else message
    // Try to restore the error class from the js error name
    @Suppress("DEPRECATION")
    return when (name) {
        Throwable::class.qualifiedName -> Throwable(message)
        Error::class.qualifiedName -> Error(message)
        Exception::class.qualifiedName -> Exception(message)
        RuntimeException::class.qualifiedName -> RuntimeException(message)
        NullPointerException::class.qualifiedName -> NullPointerException(message)
        NoSuchElementException::class.qualifiedName -> NoSuchElementException(message)
        IllegalArgumentException::class.qualifiedName -> IllegalArgumentException(message)
        IllegalStateException::class.qualifiedName -> IllegalStateException(message)
        UnsupportedOperationException::class.qualifiedName -> UnsupportedOperationException(message)
        IndexOutOfBoundsException::class.qualifiedName -> IndexOutOfBoundsException(message)
        ClassCastException::class.qualifiedName -> ClassCastException(message)
        ArithmeticException::class.qualifiedName -> ArithmeticException(message)
        AssertionError::class.qualifiedName -> AssertionError(message)
        OutOfMemoryError::class.qualifiedName -> OutOfMemoryError(message)
        NumberFormatException::class.qualifiedName -> NumberFormatException(message)
        ConcurrentModificationException::class.qualifiedName -> ConcurrentModificationException(message)
        NotImplementedError::class.qualifiedName -> NotImplementedError(message ?: "")
        QuickJsException::class.qualifiedName -> QuickJsException(messageWithStack, stack)
        // Unknown error, add the name back
        else -> QuickJsException("$name: $messageWithStack", stack)
    }
}

@OptIn(ExperimentalForeignApi::class)
private inline fun jsUint8ArrayToKtUByteArray(
    context: CPointer<JSContext>,
    array: CValue<QJSValue>
): UByteArray {
    return jsInt8ArrayToKtByteArray(context, array).asUByteArray()
}

@OptIn(ExperimentalForeignApi::class)
private fun jsInt8ArrayToKtByteArray(
    context: CPointer<JSContext>,
    array: CValue<QJSValue>
): ByteArray = memScoped {
    val length = alloc<size_tVar>()
    val arrayBuffer = qjs_get_property_str(context, array, "buffer")
    val cBuffer = qjs_get_array_buffer(context, length.ptr, arrayBuffer)
    if (cBuffer == null) {
        qjs_free_value(context, arrayBuffer)
        qjsError("Cannot read array buffer.")
    }
    try {
        cBuffer.readBytes(length.value.toInt())
    } finally {
        qjs_free_value(context, arrayBuffer)
    }
}

@OptIn(ExperimentalForeignApi::class)
private fun jsArrayToKtList(
    context: CPointer<JSContext>,
    array: CValue<QJSValue>
): List<Any?> = memScoped {
    val length = alloc<int64_tVar>()
    qjs_get_property_str(context, array, "length").use(context) {
        qjs_to_int64(context, length.ptr, this)
    }

    List(length.value.toInt()) {
        qjs_get_property_uint32(context, array, it.toUInt()).use(context) {
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
    set: CValue<QJSValue>
): Set<Any?> {
    val result = mutableSetOf<Any?>()

    val keysFunc = qjs_get_property_str(context, set, "keys")
    val iterator = qjs_call(context, keysFunc, set, 0, null)
    val nextFunc = qjs_get_property_str(context, iterator, "next")
    while (true) {
        val entry = qjs_call(context, nextFunc, iterator, 0, null)
        val done = qjs_get_property_str(context, entry, "done")
        if (qjs_to_bool(context, done) == 1) {
            freeJsValues(context, done, entry)
            break
        }
        val key = qjs_get_property_str(context, entry, "value")

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
    set: CValue<QJSValue>
): Map<Any?, Any?> {
    val result = mutableMapOf<Any?, Any?>()

    val entriesFunc = qjs_get_property_str(context, set, "entries")
    val iterator = qjs_call(context, entriesFunc, set, 0, null)
    val nextFunc = qjs_get_property_str(context, iterator, "next")
    while (true) {
        val entry = qjs_call(context, nextFunc, iterator, 0, null)
        val done = qjs_get_property_str(context, entry, "done")
        if (qjs_to_bool(context, done) == 1) {
            freeJsValues(context, done, entry)
            break
        }
        val entryVal = qjs_get_property_str(context, entry, "value")
        val key = qjs_get_property_uint32(context, entryVal, 0.toUInt())
        val value = qjs_get_property_uint32(context, entryVal, 1.toUInt())

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
    jsObject: CValue<QJSValue>
): JsObject = memScoped {
    // Check circular refs
    val json = qjs_json_stringify(context, jsObject, JsUndefined(), JsUndefined())
    if (qjs_is_exception(json) == 1) {
        val jsError = qjs_get_exception(context)
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
    qjs_get_own_property_names(
        ctx = context,
        ptab = props,
        plen = propLen.ptr,
        obj = jsObject,
        flags = JS_GPN_STRING_MASK or JS_GPN_SYMBOL_MASK
    )

    val propsPointer = props.pointed.value!!

    for (i in 0..<propLen.value.toInt()) {
        val atom = propsPointer[i].atom
        val jsKey = qjs_atom_to_value(context, atom)
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

        val jsValue = qjs_get_property(context, jsObject, atom)
        JS_FreeAtom(context, atom)
        val value = if (jsValue.isTheSameObject(jsObject)) {
            jsValue.use(context) { toKtString(context) }
        } else if (qjs_is_function(context, jsValue) == 1) {
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
private fun CValue<QJSValue>.isTheSameObject(other: CValue<QJSValue>): Boolean {
    return JsValueGetNormTag(this) == JS_TAG_OBJECT &&
            JsValueGetNormTag(other) == JS_TAG_OBJECT &&
            JsValueGetPtr(this) == JsValueGetPtr(other)
}
