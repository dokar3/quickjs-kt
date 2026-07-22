package com.dokar.quickjs.test

import com.dokar.quickjs.QuickJs
import com.dokar.quickjs.QuickJsInterruptedException
import com.dokar.quickjs.binding.function
import com.dokar.quickjs.quickJs
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class EvaluationInterruptTest {
    @Test
    fun withTimeoutInterruptsInfiniteLoop() = runTest {
        withContext(Dispatchers.Default) {
            quickJs {
                assertFailsWith<TimeoutCancellationException> {
                    withTimeout(500) { evaluate<Unit>("while(true){}", "test.js") }
                }
                assertEquals(2, evaluate<Int>("1 + 1"))
            }
        }
    }

    @Test
    fun evaluationTimeoutInterruptsInfiniteLoop() = runTest {
        withContext(Dispatchers.Default) {
            quickJs {
                evaluationTimeoutMillis = 500
                // Fast scripts still complete
                assertEquals(
                    499500,
                    evaluate<Int>("let s = 0; for (let i = 0; i < 1000; i++) s += i; s")
                )
                assertFailsWith<QuickJsInterruptedException> {
                    evaluate<Unit>("while(true){}", "test.js")
                }
                assertEquals(2, evaluate<Int>("1 + 1"))
            }
        }
    }

    @Test
    fun cancellingCoroutineInterruptsInfiniteLoop() = runTest {
        withContext(Dispatchers.Default) {
            val quickJs = QuickJs.create(Dispatchers.Default)
            try {
                val started = CompletableDeferred<Unit>()
                quickJs.function("started") { started.complete(Unit) }
                val evalJob = launch(Dispatchers.Default) {
                    quickJs.evaluate<Unit>("started(); while(true){}", "test.js")
                }
                started.await()
                evalJob.cancel()
                // Fails if cancellation didn't stop the native evaluation
                withTimeout(5_000) { evalJob.join() }
                assertTrue(evalJob.isCancelled)
            } finally {
                quickJs.close()
            }
        }
    }

    @Test
    fun interruptEvaluationStopsInfiniteLoop() = runTest {
        withContext(Dispatchers.Default) {
            val quickJs = QuickJs.create(Dispatchers.Default)
            try {
                val started = CompletableDeferred<Unit>()
                quickJs.function("started") { started.complete(Unit) }
                val evalJob = launch(Dispatchers.Default) {
                    assertFailsWith<QuickJsInterruptedException> {
                        quickJs.evaluate<Unit>("started(); while(true){}", "test.js")
                    }
                }
                started.await()
                quickJs.interruptEvaluation()
                withTimeout(5_000) { evalJob.join() }
                assertEquals(2, quickJs.evaluate<Int>("1 + 1"))
            } finally {
                quickJs.close()
            }
        }
    }
}
