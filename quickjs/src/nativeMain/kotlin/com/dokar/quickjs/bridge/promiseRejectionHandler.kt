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
import quickjs.JsValueGetPtr
import quickjs.QJSValue
import quickjs.qjs_free_value
import quickjs.qjs_get_exception
import quickjs.qjs_promise_rejection_tracker_install

@Suppress("UNUSED_PARAMETER")
@OptIn(ExperimentalForeignApi::class)
private fun promiseRejectionHandler(
    context: CPointer<JSContext>?,
    promise: CValue<QJSValue>,
    reason: CValue<QJSValue>,
    isHandled: Int,
    opaque: COpaquePointer?,
) {
    val quickJs = opaque!!.asStableRef<QuickJs>().get()
    val promiseId = JsValueGetPtr(promise)!!.toLong()
    if (isHandled != 1) {
        // An interrupt takes precedence over any rejection, don't run more JS for it.
        if (quickJs.pollInterrupt()) return
        val ktReason = try {
            reason.toKtValue(context!!)
        } catch (e: Throwable) {
            // Exceptions must not cross this C callback: report the conversion failure
            // as the rejection reason and drop any JS exception it left behind.
            qjs_free_value(context, qjs_get_exception(context))
            e
        }
        if (quickJs.pollInterrupt()) return
        quickJs.setUnhandledPromiseRejection(promiseId = promiseId, reason = ktReason)
    } else {
        quickJs.clearHandledPromiseRejection(promiseId)
    }
}

@OptIn(ExperimentalForeignApi::class)
internal fun installPromiseRejectionHandler(
    quickJs: StableRef<QuickJs>,
    runtime: CPointer<JSRuntime>,
): COpaquePointer? {
    return qjs_promise_rejection_tracker_install(
        rt = runtime,
        cb = staticCFunction(::promiseRejectionHandler),
        opaque = quickJs.asCPointer(),
    )
}
