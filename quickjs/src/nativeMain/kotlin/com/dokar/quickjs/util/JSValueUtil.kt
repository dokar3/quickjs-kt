package com.dokar.quickjs.util

import kotlinx.cinterop.CPointer
import kotlinx.cinterop.CValue
import kotlinx.cinterop.ExperimentalForeignApi
import quickjs.JSContext
import quickjs.QJSValue
import quickjs.qjs_free_value
import quickjs.qjs_get_global_object
import quickjs.qjs_get_property_str
import quickjs.qjs_is_instance_of
import quickjs.qjs_is_undefined

@OptIn(ExperimentalForeignApi::class)
internal inline fun freeJsValues(context: CPointer<JSContext>, vararg values: CValue<QJSValue>) {
    if (values.size == 1) {
        qjs_free_value(context, values[0])
    } else {
        for (value in values) {
            qjs_free_value(context, value)
        }
    }
}

@OptIn(ExperimentalForeignApi::class)
internal fun CValue<QJSValue>.isPromise(context: CPointer<JSContext>): Boolean {
    val globalThis = qjs_get_global_object(context)
    val result = isPromise(context, globalThis)
    qjs_free_value(context, globalThis)
    return result
}

@OptIn(ExperimentalForeignApi::class)
internal inline fun CValue<QJSValue>.isPromise(
    context: CPointer<JSContext>,
    globalThis: CValue<QJSValue>,
): Boolean {
    return isInstanceOf(context, globalThis, "Promise")
}

@OptIn(ExperimentalForeignApi::class)
internal inline fun CValue<QJSValue>.isSet(
    context: CPointer<JSContext>,
    globalThis: CValue<QJSValue>,
): Boolean {
    return isInstanceOf(context, globalThis, "Set")
}

@OptIn(ExperimentalForeignApi::class)
internal inline fun CValue<QJSValue>.isMap(
    context: CPointer<JSContext>,
    globalThis: CValue<QJSValue>,
): Boolean {
    return isInstanceOf(context, globalThis, "Map")
}

@OptIn(ExperimentalForeignApi::class)
internal inline fun CValue<QJSValue>.isUint8Array(
    context: CPointer<JSContext>,
    globalThis: CValue<QJSValue>,
): Boolean {
    return isInstanceOf(context, globalThis, "Uint8Array")
}

@OptIn(ExperimentalForeignApi::class)
internal inline fun CValue<QJSValue>.isInt8Array(
    context: CPointer<JSContext>,
    globalThis: CValue<QJSValue>,
): Boolean {
    return isInstanceOf(context, globalThis, "Int8Array")
}

@OptIn(ExperimentalForeignApi::class)
internal fun CValue<QJSValue>.isInstanceOf(
    context: CPointer<JSContext>,
    globalThis: CValue<QJSValue>,
    constructorName: String,
): Boolean {
    val constructor = qjs_get_property_str(context, globalThis, constructorName)
    if (qjs_is_undefined(constructor) == 1) {
        qjs_free_value(context, constructor)
        return false
    }
    val result = qjs_is_instance_of(context, this, constructor) == 1
    qjs_free_value(context, constructor)
    return result
}
