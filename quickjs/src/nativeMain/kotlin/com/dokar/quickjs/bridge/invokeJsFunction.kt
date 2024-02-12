package com.dokar.quickjs.bridge

import com.dokar.quickjs.util.allocArrayOf
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.CValue
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.memScoped
import quickjs.JSContext
import quickjs.JSValue
import quickjs.JS_Call
import quickjs.JS_FreeValue
import quickjs.JsNull

@OptIn(ExperimentalForeignApi::class)
internal fun CPointer<JSContext>.invokeJsFunction(
    func: CValue<JSValue>,
    args: Array<Any?>,
): Unit = memScoped {
    val context = this@invokeJsFunction

    val jsArgs = Array(args.size) { JsNull() }
    for (i in jsArgs.indices) {
        try {
            jsArgs[i] = args[i].toJsValue(context)
        } catch (e: Throwable) {
            for (j in 0..<i) {
                JS_FreeValue(context, jsArgs[j])
            }
            throw e
        }
    }

    val argv = allocArrayOf(*jsArgs)

    val result = JS_Call(
        ctx = context,
        func_obj = func,
        this_obj = JsNull(),
        argc = jsArgs.size,
        argv = argv,
    )

    jsArgs.forEach { JS_FreeValue(context, it) }

    JS_FreeValue(context, result)
}