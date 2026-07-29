
package com.dokar.quickjs.test

import com.dokar.quickjs.QuickJs
import com.dokar.quickjs.QuickJsException
import com.dokar.quickjs.binding.asyncFunction
import com.dokar.quickjs.binding.function
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

// https://github.com/dokar3/quickjs-kt/issues/131
class CloseWhileEvaluatingTest {
    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun closeWhileEvaluating() = runTest {
        repeat(REPEAT_COUNT) {
            val quickJs = QuickJs.create(Dispatchers.Default)
            quickJs.asyncFunction("delay") { args ->
                delay(args[0] as Long)
            }

            val evalJob = launch(Dispatchers.Default) {
                try {
                    quickJs.evaluate<Any?>(
                        """
                        for (let i = 0; i < 1000; i++) {
                            await delay(1);
                        }
                        """.trimIndent()
                    )
                } catch (_: QuickJsException) {
                }
            }

            delay((10..100).random().toLong())
            quickJs.close()
            evalJob.join()
        }
    }

    @Test
    fun closeImmediatelyAfterEvaluate() = runTest {
        repeat(REPEAT_COUNT) {
            val quickJs = QuickJs.create(Dispatchers.Default)
            val evalJob = launch(Dispatchers.Default) {
                try {
                    quickJs.evaluate<Any?>("for(let i=0; i<1000000; i++) {}")
                } catch (_: QuickJsException) {
                }
            }
            quickJs.close()
            evalJob.join()
        }
    }

    @Test
    fun closeWithMultipleEvaluations() = runTest {
        repeat(REPEAT_COUNT) {
            val quickJs = QuickJs.create(Dispatchers.Default)
            quickJs.asyncFunction("delay") { args ->
                delay(args[0] as Long)
            }
            val jobs = List(10) {
                launch(Dispatchers.Default) {
                    try {
                        quickJs.evaluate<Any?>("await delay(100);")
                    } catch (_: QuickJsException) {
                    }
                }
            }
            delay((10..50).random().toLong())
            quickJs.close()
            jobs.joinAll()
        }
    }

    @Test
    fun closeWhileAsyncFunctionCallback() = runTest {
        repeat(REPEAT_COUNT) {
            val quickJs = QuickJs.create(Dispatchers.Default)
            quickJs.asyncFunction("getWithDelay") {
                delay(10)
                "done"
            }
            val evalJob = launch(Dispatchers.Default) {
                try {
                    quickJs.evaluate<Any?>("await getWithDelay();")
                } catch (_: QuickJsException) {
                }
            }
            delay(15) // Try to close exactly during the suspension of the async function returning
            quickJs.close()
            evalJob.join()
        }
    }

    @Test
    fun closeWhileBusyEvaluating() = runTest {
        repeat(REPEAT_COUNT) {
            val quickJs = QuickJs.create(Dispatchers.Default)
            val running = CompletableDeferred<Unit>()
            // Tells us JavaScript is really running before we close
            quickJs.function("notifyRunning") { running.complete(Unit) }

            val evalJob = launch(Dispatchers.Default) {
                try {
                    quickJs.evaluate<Any?>("notifyRunning(); while(true) {}")
                } catch (_: QuickJsException) {
                }
            }

            running.await()
            // close() blocks its caller, so it has to be watched from another
            // scope, one that we are not waiting on when it gets stuck.
            val closeJob = CoroutineScope(Dispatchers.Default).launch { quickJs.close() }
            val closed = withContext(Dispatchers.Default) {
                withTimeoutOrNull(10.seconds) { closeJob.join() } != null
            }
            assertTrue(closed, "close() did not return while JavaScript was busy")
            evalJob.join()
        }
    }

    companion object {
        private const val REPEAT_COUNT = 20
    }
}
