package com.dokar.quickjs.bridge

import com.dokar.quickjs.QuickJs
import com.dokar.quickjs.util.allocArrayOf
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.CValue
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.StableRef
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.cstr
import kotlinx.cinterop.get
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.readValue
import kotlinx.cinterop.staticCFunction
import kotlinx.cinterop.toLong
import quickjs.JSContext
import quickjs.JS_FreeAtom
import quickjs.JS_NewAtom
import quickjs.JS_PROP_CONFIGURABLE
import quickjs.JsException
import quickjs.QJSValue
import quickjs.qjs_define_property_value
import quickjs.qjs_dup_value
import quickjs.qjs_free_value
import quickjs.qjs_get_global_object
import quickjs.qjs_new_big_int64
import quickjs.qjs_new_c_function_data
import quickjs.qjs_new_promise_capability
import quickjs.qjs_new_string
import quickjs.qjs_throw

@OptIn(ExperimentalForeignApi::class)
internal fun CPointer<JSContext>.defineFunction(
    quickJsRef: StableRef<QuickJs>,
    parentHandle: Long,
    parent: CValue<QJSValue>?,
    name: String,
    isAsync: Boolean,
): Unit = memScoped {
    val context = this@defineFunction

    val quickJs = quickJsRef.get()
    val qjsVoidPtr = quickJsRef.asCPointer()
    val qjsPtrAddress = qjsVoidPtr.toLong()

    val funcDataArray = arrayOf(
        qjs_new_string(context, name.cstr),
        qjs_new_big_int64(context, qjsPtrAddress),
        qjs_new_big_int64(context, parentHandle),
    )

    // Free function data when closing
    quickJs.addManagedJsValues(*funcDataArray)

    val cFunc = if (isAsync) {
        staticCFunction(::invokeAsyncFunction)
    } else {
        staticCFunction(::invokeFunction)
    }

    val function = qjs_new_c_function_data(
        ctx = context,
        func = cFunc,
        length = 0,
        magic = 0,
        data_len = 3,
        data = allocArrayOf<QJSValue>(*funcDataArray),
    )

    val prop = JS_NewAtom(context, name)

    if (parent == null) {
        val globalThis = qjs_get_global_object(context)
        qjs_define_property_value(
            ctx = context,
            this_obj = globalThis,
            prop = prop,
            `val` = function,
            flags = JS_PROP_CONFIGURABLE
        )
        qjs_free_value(context, globalThis)
    } else {
        qjs_define_property_value(
            ctx = context,
            this_obj = parent,
            prop = prop,
            `val` = function,
            flags = JS_PROP_CONFIGURABLE
        )
    }

    JS_FreeAtom(context, prop)
}

@OptIn(ExperimentalForeignApi::class)
@Suppress("unused_parameter")
private fun invokeFunction(
    ctx: CPointer<JSContext>?,
    thisVal: CValue<QJSValue>,
    argc: Int,
    argv: CPointer<QJSValue>?,
    magic: Int,
    funcData: CPointer<QJSValue>?,
): CValue<QJSValue> = memScoped {
    ctx ?: return@memScoped JsException()
    funcData ?: return@memScoped JsException()

    val (funcName, quickJs, objectHandle) = BindingFunctionData.fromJsValues(ctx, funcData)
    if (quickJs.checkInterruptOrThrow(ctx)) {
        return@memScoped JsException()
    }

    val result = try {
        val invokeArgs = Array(argc) { argv!![it].readValue().toKtValue(ctx) }
        quickJs
            .onCallBindingFunction(
                name = funcName,
                parentHandle = objectHandle,
                args = invokeArgs
            )
            .toJsValue(context = ctx)
    } catch (e: Throwable) {
        qjs_throw(ctx, ktErrorToJsError(ctx, e))
        JsException()
    }
    quickJs.checkInterruptResult(ctx, result)
}

@OptIn(ExperimentalForeignApi::class)
@Suppress("unused_parameter")
private fun invokeAsyncFunction(
    ctx: CPointer<JSContext>?,
    thisVal: CValue<QJSValue>,
    argc: Int,
    argv: CPointer<QJSValue>?,
    magic: Int,
    funcData: CPointer<QJSValue>?,
): CValue<QJSValue> = memScoped {
    ctx ?: return@memScoped JsException()
    funcData ?: return@memScoped JsException()

    val (funcName, quickJs, objectHandle) = BindingFunctionData.fromJsValues(ctx, funcData)
    if (quickJs.checkInterruptOrThrow(ctx)) {
        return@memScoped JsException()
    }

    val functions = allocArray<QJSValue>(2)
    val promise = qjs_new_promise_capability(ctx, functions)

    val resolveFunc = functions[0].readValue()
    val rejectFunc = functions[1].readValue()

    // Free these functions when closing
    quickJs.addManagedJsValues(resolveFunc, rejectFunc, promise)

    val args: Array<Any?> = Array(2 + argc) { null }
    args[0] = resolveFunc
    args[1] = rejectFunc

    try {
        val invokeArgs = Array(argc) { argv!![it].readValue().toKtValue(ctx) }
        for (i in invokeArgs.indices) {
            args[i + 2] = invokeArgs[i]
        }
        // Invoke binding
        quickJs.onCallBindingFunction(
            name = funcName,
            parentHandle = objectHandle,
            args = args
        )
    } catch (e: Throwable) {
        qjs_throw(ctx, ktErrorToJsError(ctx, e))
        return@memScoped quickJs.checkInterruptResult(ctx, JsException())
    }

    quickJs.checkInterruptResult(ctx, qjs_dup_value(ctx, promise))
}
