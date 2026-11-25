package com.dokar.quickjs.bridge

import com.dokar.quickjs.binding.JsObject
import com.dokar.quickjs.qjsError
import com.dokar.quickjs.util.allocArrayOf
import com.dokar.quickjs.util.freeJsValues
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.CValue
import kotlinx.cinterop.CValues
import kotlinx.cinterop.CValuesRef
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.cstr
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.staticCFunction
import kotlinx.cinterop.toCValues
import platform.posix.free
import platform.posix.malloc
import platform.posix.memcpy
import platform.posix.uint8_tVar
import quickjs.JSContext
import quickjs.JSRuntime
import quickjs.JS_PROP_C_W_E
import quickjs.JsNull
import quickjs.JsUndefined
import quickjs.QJSValue
import quickjs.qjs_call_constructor
import quickjs.qjs_define_property_value_str
import quickjs.qjs_free_value
import quickjs.qjs_get_exception
import quickjs.qjs_get_global_object
import quickjs.qjs_get_property_str
import quickjs.qjs_is_exception
import quickjs.qjs_is_null
import quickjs.qjs_is_undefined
import quickjs.qjs_new_array
import quickjs.qjs_new_array_buffer
import quickjs.qjs_new_bool
import quickjs.qjs_new_error
import quickjs.qjs_new_float64
import quickjs.qjs_new_int32
import quickjs.qjs_new_int64
import quickjs.qjs_new_object
import quickjs.qjs_new_string
import quickjs.qjs_set_property_str
import quickjs.qjs_set_property_uint32
import kotlin.experimental.ExperimentalNativeApi
import kotlin.native.identityHashCode

@OptIn(ExperimentalForeignApi::class)
internal fun <T : Any?> T.toJsValue(
    context: CPointer<JSContext>,
    visited: MutableSet<Int>? = null,
): CValue<QJSValue> {
    val value = this ?: return JsNull()
    return when (value) {
        Unit -> JsUndefined()
        is Boolean -> qjs_new_bool(context, if (value) 1 else 0)
        is Byte -> qjs_new_int32(context, value.toInt())
        is Short -> qjs_new_int32(context, value.toInt())
        is Int -> qjs_new_int32(context, value)
        is Long -> qjs_new_int64(context, value)
        is Float -> qjs_new_float64(context, value.toDouble())
        is Double -> qjs_new_float64(context, value)
        is String -> qjs_new_string(context, value.cstr)
        is ByteArray -> ktByteArrayToJsInt8Array(context, value)
        is UByteArray -> ktUByteArrayToJsUint8Array(context, value)
        is Array<*> -> ktArrayToJsArray(context, value, visited ?: mutableSetOf())
        is Set<*> -> ktSetToJsSet(context, value, visited ?: mutableSetOf())
        is JsObject -> ktMapToJsObject(context, value, visited ?: mutableSetOf())
        is Map<*, *> -> ktMapToJsMap(context, value, visited ?: mutableSetOf())
        is Iterable<*> -> ktIterableToJsArray(context, value, visited ?: mutableSetOf())
        is Throwable -> ktErrorToJsError(context, value)
        else -> qjsError("Cannot convert kotlin type '${value::class.qualifiedName}' to a js value.")
    }
}

@OptIn(ExperimentalForeignApi::class, ExperimentalNativeApi::class)
internal fun ktErrorToJsError(context: CPointer<JSContext>, error: Throwable): CValue<QJSValue> {
    val jsError = qjs_new_error(context)

    val name = qjs_new_string(context, error::class.qualifiedName!!.cstr)
    qjs_set_property_str(context, jsError, "name", name)

    val ktMessage = error.message
    if (ktMessage != null) {
        val message = qjs_new_string(context, ktMessage.cstr)
        qjs_set_property_str(context, jsError, "message", message)
    }

    val ktStack = error.getStackTrace()
    if (ktStack.isNotEmpty()) {
        val stack = qjs_new_string(context, ktStack.joinToString("\n").cstr)
        if (qjs_is_exception(stack) == 1) {
            qjs_free_value(context, qjs_get_exception(context))
        } else if (qjs_define_property_value_str(context, jsError, "stack", stack, JS_PROP_C_W_E) < 0) {
            qjs_free_value(context, qjs_get_exception(context))
        }
    }

    return jsError
}

@OptIn(ExperimentalForeignApi::class)
private inline fun ktByteArrayToJsInt8Array(
    context: CPointer<JSContext>,
    array: ByteArray,
): CValue<QJSValue> {
    return ktByteBufferToJsByteArray(
        context = context,
        buffer = array.toCValues(),
        size = array.size.toULong(),
        arrayType = "Int8Array",
    )
}

@OptIn(ExperimentalForeignApi::class)
private inline fun ktUByteArrayToJsUint8Array(
    context: CPointer<JSContext>,
    array: UByteArray,
): CValue<QJSValue> {
    return ktByteBufferToJsByteArray(
        context = context,
        buffer = array.toCValues(),
        size = array.size.toULong(),
        arrayType = "Uint8Array",
    )
}

@OptIn(ExperimentalForeignApi::class)
private fun ktByteBufferToJsByteArray(
    context: CPointer<JSContext>,
    buffer: CValues<*>,
    size: ULong,
    arrayType: String,
): CValue<QJSValue> = memScoped {
    val allocSize = if (size > 0uL) size else 1uL
    val cBuffer = malloc(allocSize)?.reinterpret<uint8_tVar>()
        ?: qjsError("Cannot alloc buffer for byte array.")
    if (size > 0uL) {
        memcpy(cBuffer, buffer, size)
    }
    val arrayBuffer = qjs_new_array_buffer(
        ctx = context,
        buf = cBuffer,
        len = size,
        free_func = staticCFunction(::freeJsArrayBuffer),
        opaque = null,
        is_shared = 0,
    )
    if (qjs_is_exception(arrayBuffer) == 1) {
        free(cBuffer)
        return@memScoped arrayBuffer
    }
    try {
        newJsObjectFromConstructor(context, arrayType, 1, allocArrayOf(arrayBuffer))
    } finally {
        qjs_free_value(context, arrayBuffer)
    }
}

@Suppress("UNUSED_PARAMETER")
@OptIn(ExperimentalForeignApi::class)
private fun freeJsArrayBuffer(
    runtime: CPointer<JSRuntime>?,
    opaque: kotlinx.cinterop.COpaquePointer?,
    ptr: kotlinx.cinterop.COpaquePointer?
) {
    free(ptr)
}

@OptIn(ExperimentalForeignApi::class)
private fun ktArrayToJsArray(
    context: CPointer<JSContext>,
    array: Array<*>,
    visited: MutableSet<Int>,
): CValue<QJSValue> {
    if (visited.isEmpty()) visit(visited, array)
    val jsArray = qjs_new_array(context)
    try {
        for (i in array.indices) {
            val element = array[i]
            visitOrCircularRefError(visited, element)
            val jsElement = element.toJsValue(context, visited)
            qjs_set_property_uint32(context, jsArray, i.toUInt(), jsElement)
        }
    } catch (e: Throwable) {
        freeJsValues(context, jsArray)
        throw e
    }
    return jsArray
}

@OptIn(ExperimentalForeignApi::class)
private fun ktIterableToJsArray(
    context: CPointer<JSContext>,
    iterable: Iterable<*>,
    visited: MutableSet<Int>,
): CValue<QJSValue> {
    if (visited.isEmpty()) visit(visited, iterable)
    val jsArray = qjs_new_array(context)
    try {
        for ((i, element) in iterable.withIndex()) {
            visitOrCircularRefError(visited, element)
            val jsElement = element.toJsValue(context, visited)
            qjs_set_property_uint32(context, jsArray, i.toUInt(), jsElement)
        }
    } catch (e: Throwable) {
        freeJsValues(context, jsArray)
        throw e
    }
    return jsArray
}

@OptIn(ExperimentalForeignApi::class)
private fun ktSetToJsSet(
    context: CPointer<JSContext>,
    set: Set<*>,
    visited: MutableSet<Int>,
): CValue<QJSValue> = memScoped {
    if (visited.isEmpty()) visit(visited, set)
    val elements = qjs_new_array(context)
    try {
        for ((i, element) in set.withIndex()) {
            visitOrCircularRefError(visited, element)
            val jsElement = element.toJsValue(context, visited)
            qjs_set_property_uint32(context, elements, i.toUInt(), jsElement)
        }
    } catch (e: Throwable) {
        freeJsValues(context, elements)
        throw e
    }
    try {
        newJsObjectFromConstructor(
            context = context,
            constructorName = "Set",
            argc = 1,
            argv = allocArrayOf(elements),
        )
    } finally {
        freeJsValues(context, elements)
    }
}

@OptIn(ExperimentalForeignApi::class)
private fun ktMapToJsMap(
    context: CPointer<JSContext>,
    map: Map<*, *>,
    visited: MutableSet<Int>,
): CValue<QJSValue> = memScoped {
    if (visited.isEmpty()) visit(visited, map)
    val elements = qjs_new_array(context)
    try {
        var index = 0
        for ((key, value) in map) {
            visitOrCircularRefError(visited, key)
            visitOrCircularRefError(visited, value)
            val entryArray = qjs_new_array(context)
            try {
                qjs_set_property_uint32(context, entryArray, 0u, key.toJsValue(context, visited))
                qjs_set_property_uint32(context, entryArray, 1u, value.toJsValue(context, visited))
                qjs_set_property_uint32(context, elements, index.toUInt(), entryArray)
            } catch (e: Throwable) {
                freeJsValues(context, entryArray)
                throw e
            }
            index += 1
        }
    } catch (e: Throwable) {
        freeJsValues(context, elements)
        throw e
    }
    try {
        newJsObjectFromConstructor(
            context = context,
            constructorName = "Map",
            argc = 1,
            argv = allocArrayOf(elements),
        )
    } finally {
        freeJsValues(context, elements)
    }
}

@OptIn(ExperimentalForeignApi::class)
private fun ktMapToJsObject(
    context: CPointer<JSContext>,
    map: Map<*, *>,
    visited: MutableSet<Int>,
): CValue<QJSValue> = memScoped {
    if (visited.isEmpty()) visit(visited, map)
    val jsObject = qjs_new_object(context)
    try {
        for ((key, value) in map) {
            if (key !is String) {
                qjsError("Only maps with string keys can be mapped to js objects.")
            }
            visitOrCircularRefError(visited, value)
            visitOrCircularRefError(visited, key)
            qjs_set_property_str(context, jsObject, key, value.toJsValue(context, visited))
        }
    } catch (e: Throwable) {
        freeJsValues(context, jsObject)
        throw e
    }
    return jsObject
}

@OptIn(ExperimentalForeignApi::class)
private fun newJsObjectFromConstructor(
    context: CPointer<JSContext>,
    constructorName: String,
    argc: Int,
    argv: CValuesRef<QJSValue>?,
): CValue<QJSValue> {
    val globalThis = qjs_get_global_object(context)
    val constructor = qjs_get_property_str(context, globalThis, constructorName)
    return try {
        if (qjs_is_null(constructor) == 1 || qjs_is_undefined(constructor) == 1) {
            qjsError("JS constructor '$constructorName' not found.")
        }
        qjs_call_constructor(context, constructor, argc, argv)
    } finally {
        freeJsValues(context, constructor, globalThis)
    }
}

/**
 * [Any.identityHashCode] is used to identify objects because [Any.hashCode] will be an
 * infinite recursion on circular-referenced objects. Same for [visit].
 */
@OptIn(ExperimentalNativeApi::class)
private fun visitOrCircularRefError(
    visited: MutableSet<Int>,
    current: Any?,
) {
    current ?: return
    if (current !is Array<*> &&
        current !is Iterable<*> &&
        current !is Set<*> &&
        current !is Map<*, *> &&
        current !is JsObject
    ) {
        return
    }
    val identityHashCode = current.identityHashCode()
    if (visited.contains(identityHashCode)) {
        circularRefError()
    } else {
        visited.add(identityHashCode)
    }
}

@OptIn(ExperimentalNativeApi::class)
private fun visit(
    visited: MutableSet<Int>,
    current: Any?
) {
    current ?: return
    visited.add(current.identityHashCode())
}
