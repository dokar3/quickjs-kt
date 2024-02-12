package com.dokar.quickjs.bridge

import com.dokar.quickjs.QuickJsException
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
import quickjs.JS_FreeValue
import quickjs.JS_GetException
import quickjs.JS_GetPropertyStr
import quickjs.JS_IsError
import quickjs.JS_IsException
import quickjs.JS_IsNull
import quickjs.JS_PromiseResult
import quickjs.JS_PromiseState

@OptIn(ExperimentalForeignApi::class)
internal value class JsPromise(
    private val value: CValue<JSValue>
) {
    fun result(context: CPointer<JSContext>): Any? {
        val ctxException = JS_GetException(context)
        if (JS_IsNull(ctxException) != 1) {
            ctxException.use(context) {
                if (JS_IsError(context, this) == 1) {
                    throw jsErrorToKtError(context, this)
                } else {
                    throw Error(toKtString(context))
                }
            }
        }

        return when (JS_PromiseState(context, value)) {
            JSPromiseStateEnum.JS_PROMISE_FULFILLED -> {
                JS_PromiseResult(context, value).use(context) {
                    val result = JS_GetPropertyStr(context, this, "value")
                    if (result.isPromise(context)) {
                        val state = when (JS_PromiseState(context, result)) {
                            JSPromiseStateEnum.JS_PROMISE_PENDING -> STATE_PENDING
                            JSPromiseStateEnum.JS_PROMISE_FULFILLED -> STATE_FULFILLED
                            JSPromiseStateEnum.JS_PROMISE_REJECTED -> STATE_REJECTED
                        }
                        JS_FreeValue(context, result)
                        state
                    } else if (JS_IsException(result) != 1) {
                        result.use(context) { toKtValue(context) }
                    } else {
                        // Is it safe to ignore the exception?
                        // This happens when executing a compiled module.
                        null
                    }
                }
            }

            JSPromiseStateEnum.JS_PROMISE_REJECTED -> {
                val result = JS_PromiseResult(context, value).use(context) { toKtValue(context) }
                if (result is Throwable) {
                    throw result
                } else {
                    throw Error(result?.toString())
                }
            }

            JSPromiseStateEnum.JS_PROMISE_PENDING -> STATE_PENDING
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

sealed interface ExecuteJobResult {
    data object Success : ExecuteJobResult
    data object NoJobs : ExecuteJobResult
    class Failure(val error: Throwable) : ExecuteJobResult
}

@OptIn(ExperimentalForeignApi::class)
internal fun executePendingJob(runtime: CPointer<JSRuntime>): ExecuteJobResult = memScoped {
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

