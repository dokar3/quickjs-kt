package com.dokar.quickjs.bridge

import com.dokar.quickjs.JSContext
import com.dokar.quickjs.JSValue
import com.dokar.quickjs.JS_DefinePropertyValue
import com.dokar.quickjs.JS_DupValue
import com.dokar.quickjs.JS_FreeAtom
import com.dokar.quickjs.JS_FreeValue
import com.dokar.quickjs.JS_GetGlobalObject
import com.dokar.quickjs.JS_NewAtom
import com.dokar.quickjs.JS_NewCFunctionData
import com.dokar.quickjs.JS_NewInt64
import com.dokar.quickjs.JS_NewPromiseCapability
import com.dokar.quickjs.JS_NewString
import com.dokar.quickjs.JS_PROP_CONFIGURABLE
import com.dokar.quickjs.JS_Throw
import com.dokar.quickjs.JsException
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

@OptIn(ExperimentalForeignApi::class)
internal fun CPointer<JSContext>.defineFunction(
    quickJsRef: StableRef<QuickJs>,
    parentHandle: Long,
    parent: CValue<JSValue>?,
    name: String,
    isAsync: Boolean,
): Unit = memScoped {
    val context = this@defineFunction

    val quickJs = quickJsRef.get()
    val qjsVoidPtr = quickJsRef.asCPointer()
    val qjsPtrAddress = qjsVoidPtr.toLong()

    val funcDataArray = arrayOf(
        JS_NewString(context, name.cstr),
        JS_NewInt64(context, qjsPtrAddress),
        JS_NewInt64(context, parentHandle),
    )

    // Free function data when closing
    quickJs.addManagedJsValues(*funcDataArray)

    val cFunc = if (isAsync) {
        staticCFunction(::invokeAsyncFunction)
    } else {
        staticCFunction(::invokeFunction)
    }

    val function = JS_NewCFunctionData(
        ctx = context,
        func = cFunc,
        length = 0,
        magic = 0,
        data_len = 3,
        data = allocArrayOf<JSValue>(*funcDataArray),
    )

    val prop = JS_NewAtom(context, name)

    if (parent == null) {
        val globalThis = JS_GetGlobalObject(context)
        JS_DefinePropertyValue(
            ctx = context,
            this_obj = globalThis,
            prop = prop,
            `val` = function,
            flags = JS_PROP_CONFIGURABLE
        )
        JS_FreeValue(context, globalThis)
    } else {
        JS_DefinePropertyValue(
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
    thisVal: CValue<JSValue>,
    argc: Int,
    argv: CPointer<JSValue>?,
    magic: Int,
    funcData: CPointer<JSValue>?,
): CValue<JSValue> = memScoped {
    ctx ?: return@memScoped JsException()
    funcData ?: return@memScoped JsException()

    val (funcName, quickJs, objectHandle) = BindingFunctionData.fromJsValues(ctx, funcData)

    try {
        val invokeArgs = Array(argc) { argv!![it].readValue().toKtValue(ctx) }
        quickJs
            .onCallBindingFunction(
                name = funcName,
                parentHandle = objectHandle,
                args = invokeArgs
            )
            .toJsValue(context = ctx)
    } catch (e: Throwable) {
        JS_Throw(ctx, ktErrorToJsError(ctx, e))
        JsException()
    }
}

@OptIn(ExperimentalForeignApi::class)
@Suppress("unused_parameter")
private fun invokeAsyncFunction(
    ctx: CPointer<JSContext>?,
    thisVal: CValue<JSValue>,
    argc: Int,
    argv: CPointer<JSValue>?,
    magic: Int,
    funcData: CPointer<JSValue>?,
): CValue<JSValue> = memScoped {
    ctx ?: return@memScoped JsException()
    funcData ?: return@memScoped JsException()

    val (funcName, quickJs, objectHandle) = BindingFunctionData.fromJsValues(ctx, funcData)

    val functions = allocArray<JSValue>(2)
    val promise = JS_NewPromiseCapability(ctx, functions)

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
        JS_Throw(ctx, ktErrorToJsError(ctx, e))
        return@memScoped JsException()
    }

    JS_DupValue(ctx, promise)
}
