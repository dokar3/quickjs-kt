package com.dokar.quickjs.bridge

import com.dokar.quickjs.QuickJs
import kotlinx.cinterop.COpaquePointer
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.CValue
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.StableRef
import kotlinx.cinterop.asStableRef
import kotlinx.cinterop.staticCFunction
import kotlinx.cinterop.toLong
import quickjs.JSContext
import quickjs.JSRuntime
import quickjs.JSValue
import quickjs.JS_SetHostPromiseRejectionTracker
import quickjs.JsValueGetPtr

@Suppress("UNUSED_PARAMETER")
@OptIn(ExperimentalForeignApi::class)
private fun promiseRejectionHandler(
    context: CPointer<JSContext>?,
    promise: CValue<JSValue>,
    reason: CValue<JSValue>,
    isHandled: Int,
    opaque: COpaquePointer?,
) {
    val quickJs = opaque!!.asStableRef<QuickJs>()
    val promiseId = JsValueGetPtr(promise)!!.toLong()
    if (isHandled != 1) {
        quickJs.get().setUnhandledPromiseRejection(
            promiseId = promiseId,
            reason = reason.toKtValue(context!!),
        )
    } else {
        quickJs.get().clearHandledPromiseRejection(promiseId)
    }
}

@OptIn(ExperimentalForeignApi::class)
internal fun setPromiseRejectionHandler(
    quickJs: StableRef<QuickJs>,
    runtime: CPointer<JSRuntime>,
) {
    JS_SetHostPromiseRejectionTracker(
        rt = runtime,
        cb = staticCFunction(::promiseRejectionHandler),
        opaque = quickJs.asCPointer(),
    )
}
