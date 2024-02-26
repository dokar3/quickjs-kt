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
import quickjs.JSValue
import quickjs.JS_CallConstructor
import quickjs.JS_FreeValue
import quickjs.JS_GetGlobalObject
import quickjs.JS_GetPropertyStr
import quickjs.JS_IsNull
import quickjs.JS_IsUndefined
import quickjs.JS_NewArray
import quickjs.JS_NewArrayBuffer
import quickjs.JS_NewBool
import quickjs.JS_NewError
import quickjs.JS_NewFloat64
import quickjs.JS_NewInt32
import quickjs.JS_NewInt64
import quickjs.JS_NewObject
import quickjs.JS_NewString
import quickjs.JS_SetPropertyStr
import quickjs.JS_SetPropertyUint32
import quickjs.JsNull
import quickjs.JsUndefined
import kotlin.experimental.ExperimentalNativeApi
import kotlin.native.identityHashCode

@OptIn(ExperimentalForeignApi::class)
internal fun <T : Any?> T.toJsValue(
    context: CPointer<JSContext>,
    visited: MutableSet<Int>? = null,
): CValue<JSValue> {
    val value = this ?: return JsNull()
    return when (value) {
        Unit -> JsUndefined()
        is Boolean -> JS_NewBool(context, if (value) 1 else 0)
        is Int -> JS_NewInt32(context, value)
        is Long -> JS_NewInt64(context, value)
        is Float -> JS_NewFloat64(context, value.toDouble())
        is Double -> JS_NewFloat64(context, value)
        is String -> JS_NewString(context, value.cstr)
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
internal fun ktErrorToJsError(context: CPointer<JSContext>, error: Throwable): CValue<JSValue> {
    val jsError = JS_NewError(context)

    val name = JS_NewString(context, error::class.qualifiedName!!.cstr)
    JS_SetPropertyStr(context, jsError, "name", name)

    val ktMessage = error.message
    if (ktMessage != null) {
        val message = JS_NewString(context, ktMessage.cstr)
        JS_SetPropertyStr(context, jsError, "message", message)
    }

    val stack = JS_NewArray(context)
    val ktStack = error.getStackTrace()
    for (i in ktStack.indices) {
        val line = JS_NewString(context, ktStack[i].cstr)
        JS_SetPropertyUint32(context, stack, i.toUInt(), line)
    }
    JS_SetPropertyStr(context, jsError, "stack", stack)

    return jsError
}

@OptIn(ExperimentalForeignApi::class)
private inline fun ktByteArrayToJsInt8Array(
    context: CPointer<JSContext>,
    array: ByteArray,
): CValue<JSValue> {
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
): CValue<JSValue> {
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
): CValue<JSValue> = memScoped {
    val cBuffer = malloc(size)?.reinterpret<uint8_tVar>()
        ?: qjsError("Cannot alloc buffer for byte array.")
    memcpy(cBuffer, buffer, size)
    val arrayBuffer = JS_NewArrayBuffer(
        ctx = context,
        buf = cBuffer,
        len = size,
        free_func = staticCFunction(::freeJsArrayBuffer),
        opaque = null,
        is_shared = 0,
    )
    try {
        newJsObjectFromConstructor(context, arrayType, 1, allocArrayOf(arrayBuffer))
    } finally {
        JS_FreeValue(context, arrayBuffer)
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
): CValue<JSValue> {
    if (visited.isEmpty()) visit(visited, array)
    val jsArray = JS_NewArray(context)
    try {
        for (i in array.indices) {
            val element = array[i]
            visitOrCircularRefError(visited, element)
            val jsElement = element.toJsValue(context, visited)
            JS_SetPropertyUint32(context, jsArray, i.toUInt(), jsElement)
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
): CValue<JSValue> {
    if (visited.isEmpty()) visit(visited, iterable)
    val jsArray = JS_NewArray(context)
    try {
        for ((i, element) in iterable.withIndex()) {
            visitOrCircularRefError(visited, element)
            val jsElement = element.toJsValue(context, visited)
            JS_SetPropertyUint32(context, jsArray, i.toUInt(), jsElement)
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
): CValue<JSValue> = memScoped {
    if (visited.isEmpty()) visit(visited, set)
    val elements = JS_NewArray(context)
    try {
        for ((i, element) in set.withIndex()) {
            visitOrCircularRefError(visited, element)
            val jsElement = element.toJsValue(context, visited)
            JS_SetPropertyUint32(context, elements, i.toUInt(), jsElement)
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
): CValue<JSValue> = memScoped {
    if (visited.isEmpty()) visit(visited, map)
    val elements = JS_NewArray(context)
    try {
        var index = 0
        for ((key, value) in map) {
            visitOrCircularRefError(visited, key)
            visitOrCircularRefError(visited, value)
            val entryArray = JS_NewArray(context)
            try {
                JS_SetPropertyUint32(context, entryArray, 0u, key.toJsValue(context, visited))
                JS_SetPropertyUint32(context, entryArray, 1u, value.toJsValue(context, visited))
                JS_SetPropertyUint32(context, elements, index.toUInt(), entryArray)
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
): CValue<JSValue> = memScoped {
    if (visited.isEmpty()) visit(visited, map)
    val jsObject = JS_NewObject(context)
    try {
        for ((key, value) in map) {
            if (key !is String) {
                qjsError("Only maps with string keys can be mapped to js objects.")
            }
            visitOrCircularRefError(visited, value)
            visitOrCircularRefError(visited, key)
            JS_SetPropertyStr(context, jsObject, key, value.toJsValue(context, visited))
        }
    } catch (e: Throwable) {
        freeJsValues(context, jsObject)
    }
    return jsObject
}

@OptIn(ExperimentalForeignApi::class)
private fun newJsObjectFromConstructor(
    context: CPointer<JSContext>,
    constructorName: String,
    argc: Int,
    argv: CValuesRef<JSValue>?,
): CValue<JSValue> {
    val globalThis = JS_GetGlobalObject(context)
    val constructor = JS_GetPropertyStr(context, globalThis, constructorName)
    if (JS_IsNull(constructor) == 1 || JS_IsUndefined(constructor) == 1) {
        qjsError("JS constructor '$constructorName' not found.")
    }
    val instance = JS_CallConstructor(context, constructor, argc, argv)
    freeJsValues(context, constructor, globalThis)
    return instance
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
