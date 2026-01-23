package com.dokar.quickjs.bridge

import com.dokar.quickjs.QuickJsException
import com.dokar.quickjs.qjsError
import com.dokar.quickjs.util.isPromise
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.CPointerVar
import kotlinx.cinterop.CValue
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.value
import quickjs.JSContext
import quickjs.JSPromiseStateEnum
import quickjs.JSRuntime
import quickjs.JSValue
import quickjs.JS_ExecutePendingJob
import quickjs.JS_FreeAtom
import quickjs.JS_FreeValue
import quickjs.JS_GetException
import quickjs.JS_GetPropertyStr
import quickjs.JS_HasProperty
import quickjs.JS_IsError
import quickjs.JS_IsException
import quickjs.JS_NewAtom
import quickjs.JS_PromiseResult
import quickjs.JS_PromiseState
import quickjs.JS_TAG_NULL
import quickjs.JS_TAG_OBJECT
import quickjs.JS_TAG_UNINITIALIZED
import quickjs.JS_UpdateStackTop
import quickjs.JsValueGetNormTag

@PublishedApi
@OptIn(ExperimentalForeignApi::class)
internal value class JsPromise(
    private val value: CValue<JSValue>
) {
    fun result(context: CPointer<JSContext>): Any? {
        val ctxException = JS_GetException(context)
        val ctxExTag = JsValueGetNormTag(ctxException)
        if (ctxExTag != JS_TAG_NULL && ctxExTag != JS_TAG_UNINITIALIZED) {
            ctxException.use(context) {
                if (JS_IsError(context, this) == 1) {
                    throw jsErrorToKtError(context, this)
                } else {
                    throw QuickJsException(toKtString(context))
                }
            }
        } else {
            JS_FreeValue(context, ctxException)
        }

        return when (val state = JS_PromiseState(context, value)) {
            JSPromiseStateEnum.JS_PROMISE_FULFILLED -> {
                val realValue = JS_PromiseResult(context, value).unwrapIfWrapped(context)
                if (realValue.isPromise(context)) {
                    val stateText = when (val retState = JS_PromiseState(context, realValue)) {
                        JSPromiseStateEnum.JS_PROMISE_PENDING -> STATE_PENDING
                        JSPromiseStateEnum.JS_PROMISE_FULFILLED -> STATE_FULFILLED
                        JSPromiseStateEnum.JS_PROMISE_REJECTED -> STATE_REJECTED
                        else -> qjsError("Unknown promise state: $retState")
                    }
                    JS_FreeValue(context, realValue)
                    stateText
                } else {
                    realValue.use(context) { toKtValue(context) }
                }
            }

            JSPromiseStateEnum.JS_PROMISE_REJECTED -> {
                JS_PromiseResult(context, value).unwrapIfWrapped(context).use(context) {
                    if (JS_IsError(context, this) == 1) {
                        throw jsErrorToKtError(context, this)
                    } else {
                        throw QuickJsException(toKtString(context))
                    }
                }
            }

            JSPromiseStateEnum.JS_PROMISE_PENDING -> STATE_PENDING

            else -> qjsError("Unknown promise state: $state")
        }
    }

    fun free(context: CPointer<JSContext>) {
        JS_FreeValue(context, value)
    }

    companion object {
        private const val STATE_FULFILLED = """Promise { <state>: "fulfilled" }"""
        private const val STATE_REJECTED = """Promise { <state>: "rejected" }"""
        private const val STATE_PENDING = """Promise { <state>: "pending" }"""
    }
}

internal sealed interface ExecuteJobResult {
    data object Success : ExecuteJobResult
    data object NoJobs : ExecuteJobResult
    class Failure(val error: Throwable) : ExecuteJobResult
}

@OptIn(ExperimentalForeignApi::class)
private fun CValue<JSValue>.unwrapIfWrapped(context: CPointer<JSContext>): CValue<JSValue> {
    if (JsValueGetNormTag(this) != JS_TAG_OBJECT || this.isPromise(context)) {
        return this
    }
    // The evaluation result is wrapped in {value: ...} if it's not a module eval.
    val atom = JS_NewAtom(context, "value")
    val hasProperty = JS_HasProperty(context, this, atom)
    JS_FreeAtom(context, atom)
    if (hasProperty == 1) {
        val valueProp = JS_GetPropertyStr(context, this, "value")
        if (JS_IsException(valueProp) != 1) {
            JS_FreeValue(context, this)
            return valueProp
        } else {
            JS_FreeValue(context, JS_GetException(context))
        }
    }
    return this
}

@OptIn(ExperimentalForeignApi::class)
internal fun executePendingJob(runtime: CPointer<JSRuntime>): ExecuteJobResult = memScoped {
    JS_UpdateStackTop(runtime)
    val ctx = alloc<CPointerVar<JSContext>>()
    val ret = JS_ExecutePendingJob(runtime, ctx.ptr)
    if (ret < 0) {
        val context = ctx.value ?: return@memScoped ExecuteJobResult.Failure(
            QuickJsException("Unknown execute error.")
        )
        val jsError = JS_GetException(context)
        val error = jsError.use(context) { jsErrorToKtError(context, this) }
        return ExecuteJobResult.Failure(error)
    } else if (ret == 1) {
        return ExecuteJobResult.Success
    } else {
        return ExecuteJobResult.NoJobs
    }
}

