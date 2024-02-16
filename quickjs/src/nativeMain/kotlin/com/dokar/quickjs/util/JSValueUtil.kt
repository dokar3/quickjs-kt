package com.dokar.quickjs.util

import kotlinx.cinterop.CPointer
import kotlinx.cinterop.CValue
import kotlinx.cinterop.ExperimentalForeignApi
import quickjs.JSContext
import quickjs.JSValue
import quickjs.JS_FreeValue
import quickjs.JS_GetGlobalObject
import quickjs.JS_GetPropertyStr
import quickjs.JS_IsInstanceOf
import quickjs.JS_IsUndefined

@OptIn(ExperimentalForeignApi::class)
internal inline fun freeJsValues(context: CPointer<JSContext>, vararg values: CValue<JSValue>) {
    if (values.size == 1) {
        JS_FreeValue(context, values[0])
    } else {
        for (value in values) {
            JS_FreeValue(context, value)
        }
    }
}

@OptIn(ExperimentalForeignApi::class)
internal fun CValue<JSValue>.isPromise(context: CPointer<JSContext>): Boolean {
    val globalThis = JS_GetGlobalObject(context)
    val result = isPromise(context, globalThis)
    JS_FreeValue(context, globalThis)
    return result
}

@OptIn(ExperimentalForeignApi::class)
internal inline fun CValue<JSValue>.isPromise(
    context: CPointer<JSContext>,
    globalThis: CValue<JSValue>,
): Boolean {
    return isInstanceOf(context, globalThis, "Promise")
}

@OptIn(ExperimentalForeignApi::class)
internal inline fun CValue<JSValue>.isSet(
    context: CPointer<JSContext>,
    globalThis: CValue<JSValue>,
): Boolean {
    return isInstanceOf(context, globalThis, "Set")
}

@OptIn(ExperimentalForeignApi::class)
internal inline fun CValue<JSValue>.isMap(
    context: CPointer<JSContext>,
    globalThis: CValue<JSValue>,
): Boolean {
    return isInstanceOf(context, globalThis, "Map")
}

@OptIn(ExperimentalForeignApi::class)
internal inline fun CValue<JSValue>.isUint8Array(
    context: CPointer<JSContext>,
    globalThis: CValue<JSValue>,
): Boolean {
    return isInstanceOf(context, globalThis, "Uint8Array")
}

@OptIn(ExperimentalForeignApi::class)
internal inline fun CValue<JSValue>.isInt8Array(
    context: CPointer<JSContext>,
    globalThis: CValue<JSValue>,
): Boolean {
    return isInstanceOf(context, globalThis, "Int8Array")
}

@OptIn(ExperimentalForeignApi::class)
internal fun CValue<JSValue>.isInstanceOf(
    context: CPointer<JSContext>,
    globalThis: CValue<JSValue>,
    constructorName: String,
): Boolean {
    val constructor = JS_GetPropertyStr(context, globalThis, constructorName)
    if (JS_IsUndefined(constructor) == 1) {
        JS_FreeValue(context, constructor)
        return false
    }
    val result = JS_IsInstanceOf(context, this, constructor) == 1
    JS_FreeValue(context, constructor)
    return result
}