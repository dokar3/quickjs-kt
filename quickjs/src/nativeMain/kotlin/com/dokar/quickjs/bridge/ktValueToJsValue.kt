package com.dokar.quickjs.bridge

import com.dokar.quickjs.JSContext
import com.dokar.quickjs.JSValue
import com.dokar.quickjs.JS_NewArray
import com.dokar.quickjs.JS_NewBool
import com.dokar.quickjs.JS_NewError
import com.dokar.quickjs.JS_NewFloat64
import com.dokar.quickjs.JS_NewInt32
import com.dokar.quickjs.JS_NewInt64
import com.dokar.quickjs.JS_NewString
import com.dokar.quickjs.JS_SetPropertyStr
import com.dokar.quickjs.JS_SetPropertyUint32
import com.dokar.quickjs.JsNull
import com.dokar.quickjs.JsUndefined
import com.dokar.quickjs.qjsError
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.CValue
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.cstr
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
