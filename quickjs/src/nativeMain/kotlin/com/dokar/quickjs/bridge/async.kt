package com.dokar.quickjs.bridge

import com.dokar.quickjs.JSContext
import com.dokar.quickjs.JSPromiseStateEnum
import com.dokar.quickjs.JSRuntime
import com.dokar.quickjs.JSValue
import com.dokar.quickjs.JS_ExecutePendingJob
import com.dokar.quickjs.JS_FreeValue
import com.dokar.quickjs.JS_GetException
import com.dokar.quickjs.JS_GetPropertyStr
import com.dokar.quickjs.JS_IsException
import com.dokar.quickjs.JS_PromiseResult
import com.dokar.quickjs.JS_PromiseState
import com.dokar.quickjs.QuickJsException
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.CPointerVar
import kotlinx.cinterop.CValue
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.value

@OptIn(ExperimentalForeignApi::class)
internal value class JsPromise(
    private val value: CValue<JSValue>
) {
    fun state(context: CPointer<JSContext>): JSPromiseStateEnum {
        return JS_PromiseState(context, value)
    }

    fun result(context: CPointer<JSContext>): Any? {
        return when (state(context)) {
            JSPromiseStateEnum.JS_PROMISE_FULFILLED -> {
                JS_PromiseResult(context, value).use(context) {
                    val result = JS_GetPropertyStr(context, this, "value")
                    if (JS_IsException(result) != 1) {
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
                }
                result
            }

            JSPromiseStateEnum.JS_PROMISE_PENDING -> {
                """Promise { <state>: "pending" }"""
            }
        }
    }

    fun free(context: CPointer<JSContext>) {
        JS_FreeValue(context, value)
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

