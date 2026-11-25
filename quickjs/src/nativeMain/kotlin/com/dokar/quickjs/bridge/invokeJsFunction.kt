package com.dokar.quickjs.bridge

import com.dokar.quickjs.util.allocArrayOf
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.CValue
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.memScoped
import quickjs.JSContext
import quickjs.JS_GetRuntime
import quickjs.JS_UpdateStackTop
import quickjs.JsNull
import quickjs.QJSValue
import quickjs.qjs_call
import quickjs.qjs_free_value

@OptIn(ExperimentalForeignApi::class)
internal fun CPointer<JSContext>.invokeJsFunction(
    func: CValue<QJSValue>,
    args: Array<Any?>,
): Unit = memScoped {
    val context = this@invokeJsFunction

    JS_UpdateStackTop(JS_GetRuntime(context))

    val jsArgs = Array(args.size) { JsNull() }
    for (i in jsArgs.indices) {
        try {
            jsArgs[i] = args[i].toJsValue(context)
        } catch (e: Throwable) {
            for (j in 0..<i) {
                qjs_free_value(context, jsArgs[j])
            }
            throw e
        }
    }

    val argv = allocArrayOf(*jsArgs)

    val result = qjs_call(
        ctx = context,
        func_obj = func,
        this_obj = JsNull(),
        argc = jsArgs.size,
        argv = argv,
    )

    jsArgs.forEach { qjs_free_value(context, it) }

    qjs_free_value(context, result)
}
