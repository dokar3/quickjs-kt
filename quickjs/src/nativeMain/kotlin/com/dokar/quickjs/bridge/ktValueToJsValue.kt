package com.dokar.quickjs.bridge

import com.dokar.quickjs.JSContext
import com.dokar.quickjs.JSValue
import com.dokar.quickjs.JS_CallConstructor
import com.dokar.quickjs.JS_GetGlobalObject
import com.dokar.quickjs.JS_GetPropertyStr
import com.dokar.quickjs.JS_IsNull
import com.dokar.quickjs.JS_IsUndefined
import com.dokar.quickjs.JS_NewArray
import com.dokar.quickjs.JS_NewBool
import com.dokar.quickjs.JS_NewError
import com.dokar.quickjs.JS_NewFloat64
import com.dokar.quickjs.JS_NewInt32
import com.dokar.quickjs.JS_NewInt64
import com.dokar.quickjs.JS_NewObject
import com.dokar.quickjs.JS_NewString
import com.dokar.quickjs.JS_SetPropertyStr
import com.dokar.quickjs.JS_SetPropertyUint32
import com.dokar.quickjs.JsNull
import com.dokar.quickjs.JsUndefined
import com.dokar.quickjs.binding.JsObject
import com.dokar.quickjs.qjsError
import com.dokar.quickjs.util.allocArrayOf
import com.dokar.quickjs.util.freeJsValues
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.CValue
import kotlinx.cinterop.CValuesRef
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.cstr
import kotlinx.cinterop.memScoped
import kotlin.experimental.ExperimentalNativeApi

@OptIn(ExperimentalForeignApi::class)
internal fun <T : Any?> T.toJsValue(context: CPointer<JSContext>): CValue<JSValue> {
    val value = this ?: return JsNull()
    return when (value) {
        Unit -> JsUndefined()
        is Boolean -> JS_NewBool(context, if (value) 1 else 0)
        is Int -> JS_NewInt32(context, value)
        is Long -> JS_NewInt64(context, value)
        is Float -> JS_NewFloat64(context, value.toDouble())
        is Double -> JS_NewFloat64(context, value)
        is String -> JS_NewString(context, value.cstr)
        is Array<*> -> ktArrayToJsArray(context, value)
        is Set<*> -> ktSetToJsSet(context, value)
        is JsObject -> ktMapToJsObject(context, value)
        is Map<*, *> -> ktMapToJsMap(context, value)
        is Iterable<*> -> ktIterableToJsArray(context, value)
        is Throwable -> ktErrorToJsError(context, value)
        else -> qjsError("Mapping an unsupported kt value to js value: $value")
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
private fun ktArrayToJsArray(context: CPointer<JSContext>, array: Array<*>): CValue<JSValue> {
    val jsArray = JS_NewArray(context)
    try {
        for (i in array.indices) {
            val element = array[i]
            if (element == array) {
                circularRefError()
            }
            JS_SetPropertyUint32(context, jsArray, i.toUInt(), element.toJsValue(context))
        }
    } catch (e: Exception) {
        freeJsValues(context, jsArray)
        throw e
    }
    return jsArray
}

@OptIn(ExperimentalForeignApi::class)
private fun ktIterableToJsArray(
    context: CPointer<JSContext>,
    iterable: Iterable<*>,
): CValue<JSValue> {
    val jsArray = JS_NewArray(context)
    try {
        for ((i, element) in iterable.withIndex()) {
            if (element == iterable) {
                circularRefError()
            }
            JS_SetPropertyUint32(context, jsArray, i.toUInt(), element.toJsValue(context))
        }
    } catch (e: Exception) {
        freeJsValues(context, jsArray)
        throw e
    }
    return jsArray
}

@OptIn(ExperimentalForeignApi::class)
private fun ktSetToJsSet(
    context: CPointer<JSContext>,
    set: Set<*>,
): CValue<JSValue> = memScoped {
    val elements = JS_NewArray(context)
    try {
        for ((i, element) in set.withIndex()) {
            if (element == set) {
                circularRefError()
            }
            JS_SetPropertyUint32(context, elements, i.toUInt(), element.toJsValue(context))
        }
    } catch (e: Exception) {
        freeJsValues(context, elements)
        throw e
    }
    val jsSet = try {
        newJsObjectFromConstructor(
            context = context,
            constructorName = "Set",
            argc = 1,
            argv = allocArrayOf(elements),
        )
    } finally {
        freeJsValues(context, elements)
    }
    return@memScoped jsSet
}

@OptIn(ExperimentalForeignApi::class)
private fun ktMapToJsMap(
    context: CPointer<JSContext>,
    map: Map<*, *>,
): CValue<JSValue> = memScoped {
    val elements = JS_NewArray(context)
    try {
        var index = 0
        for ((key, value) in map) {
            if (key == map || value == map) {
                circularRefError()
            }
            val entryArray = JS_NewArray(context)
            JS_SetPropertyUint32(context, entryArray, 0u, key.toJsValue(context))
            JS_SetPropertyUint32(context, entryArray, 1u, value.toJsValue(context))
            JS_SetPropertyUint32(context, elements, index.toUInt(), entryArray)
            index += 1
        }
    } catch (e :Exception) {
        freeJsValues(context, elements)
        throw e
    }
    val jsMap = try {
        newJsObjectFromConstructor(
            context = context,
            constructorName = "Map",
            argc = 1,
            argv = allocArrayOf(elements),
        )
    } finally {
        freeJsValues(context, elements)
    }
    return jsMap
}

@OptIn(ExperimentalForeignApi::class)
private fun ktMapToJsObject(
    context: CPointer<JSContext>,
    map: Map<*, *>,
): CValue<JSValue> = memScoped {
    val jsObject = JS_NewObject(context)
    try {
        for ((key, value) in map) {
            if (key == map || value == map) {
                circularRefError()
            }
            if (key !is String) {
                qjsError("Only maps with string keys can be mapped to js objects.")
            }
            JS_SetPropertyStr(context, jsObject, key, value.toJsValue(context))
        }
    } catch (e: Exception) {
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