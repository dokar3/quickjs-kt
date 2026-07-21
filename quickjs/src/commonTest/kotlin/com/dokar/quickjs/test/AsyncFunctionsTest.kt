package com.dokar.quickjs.test

import com.dokar.quickjs.QuickJs
import com.dokar.quickjs.binding.asyncFunction
import com.dokar.quickjs.binding.function
import com.dokar.quickjs.quickJs
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class AsyncFunctionsTest {
    @Test
    fun runAsyncResolved() = runTest {
        quickJs {
            asyncFunction("fetch") { "Hello" }

            assertEquals("Hello", evaluate("await fetch()"))
        }
    }

    @Test
    fun evaluateInsideAsyncFunction() = runTest {
        quickJs {
            asyncFunction("nestedEval") { evaluate<Int>("40 + 2") }

            assertEquals(42, evaluate("await nestedEval()"))
        }
    }

    @Test
    fun evaluateBytecodeInsideAsyncFunction() = runTest {
        quickJs {
            val bytecode = compile("21 * 2")
            asyncFunction("nestedEval") { evaluate<Int>(bytecode) }

            assertEquals(42, evaluate("await nestedEval()"))
        }
    }

    @Test
    fun evaluateAsyncCodeInsideAsyncFunction() = runTest {
        quickJs {
            asyncFunction("delay") { delay(it[0] as Long) }
            asyncFunction("nestedEval") {
                evaluate<String>("await delay(100); 'nested result'")
            }

            assertEquals("nested result", evaluate("await nestedEval()"))
        }
    }

    @Test
    fun runConcurrentNestedEvaluations() = runTest {
        quickJs {
            asyncFunction("delay") { delay(it[0] as Long) }
            asyncFunction("nestedEval") {
                val value = it[0] as Long
                evaluate<Long>("await delay(100); $value + 10")
            }

            assertEquals(
                "11,12",
                evaluate(
                    "await Promise.all([nestedEval(1), nestedEval(2)]).then(values => values.join(','))"
                )
            )
        }
    }

    @Test
    fun concurrentEvaluationsKeepAsyncJobsWithTheirOwningEvaluation() = runTest {
        quickJs(Dispatchers.Default) {
            val firstEvaluationStarted = CompletableDeferred<Unit>()
            val secondEvaluationAttempting = CompletableDeferred<Unit>()

            function("blockFirstEvaluation") {
                firstEvaluationStarted.complete(Unit)
                runBlocking {
                    secondEvaluationAttempting.await()
                    // Give the second evaluation time to queue on the runtime mutex.
                    delay(100)
                }
            }
            asyncFunction("delayAndReturn") {
                delay(100)
                it[0]
            }

            val first = async(Dispatchers.Default) {
                evaluate<String>(
                    """
                    blockFirstEvaluation();
                    await Promise.resolve().then(() => delayAndReturn('first'));
                    """.trimIndent()
                )
            }
            firstEvaluationStarted.await()

            val second = async(Dispatchers.Default) {
                secondEvaluationAttempting.complete(Unit)
                evaluate<String>(
                    "await Promise.resolve().then(() => delayAndReturn('second'))"
                )
            }

            assertEquals(
                listOf("first", "second"),
                coroutineScope {
                    listOf(first.await(), second.await())
                }
            )
        }
    }

    @Test
    fun nestedEvaluationCanAwaitPromiseCreatedByOuterEvaluation() = runTest {
        quickJs {
            asyncFunction("delay") { delay(it[0] as Long) }
            asyncFunction("nestedEval") {
                evaluate<String>("await globalThis.outerPromise; 'nested result'")
            }

            assertEquals(
                "nested result",
                evaluate(
                    """
                    globalThis.outerPromise = delay(100);
                    await nestedEval();
                    """.trimIndent()
                )
            )
        }
    }

    @Test
    fun nestedEvaluationFailureRejectsBindingPromise() = runTest {
        quickJs {
            asyncFunction("nestedEval") {
                evaluate<Any?>("throw new Error('nested failure')")
            }

            assertFails {
                evaluate<Any?>("await nestedEval()")
            }.also {
                assertContains(it.message!!, "nested failure")
            }
        }
    }

    @Test
    fun nestedEvaluationFailureDoesNotCancelSiblingBinding() = runTest {
        quickJs {
            asyncFunction("nestedFail") {
                evaluate<Any?>("throw new Error('nested failure')")
            }
            asyncFunction("nestedSlow") {
                delay(100)
                "slow result"
            }

            assertEquals(
                "rejected,fulfilled:slow result",
                withTimeout(1_000) {
                    evaluate<String>(
                        """
                        await Promise.allSettled([nestedFail(), nestedSlow()])
                            .then(results => results
                                .map(result => result.status +
                                    (result.status === 'fulfilled' ? ':' + result.value : ''))
                                .join(','));
                        """.trimIndent()
                    )
                }
            )
        }
    }

    @Test
    fun concurrentNestedEvaluationFailureStaysWithItsOwner() = runTest {
        quickJs {
            asyncFunction("delay") { delay(it[0] as Long) }
            asyncFunction("nestedSuccess") {
                evaluate<String>("await globalThis.sharedPromise; 'success'")
            }
            asyncFunction("nestedFail") {
                evaluate<Any?>(
                    "await globalThis.sharedPromise; throw new Error('nested failure')"
                )
            }

            assertEquals(
                "fulfilled:success,rejected",
                withTimeout(1_000) {
                    evaluate<String>(
                        """
                        globalThis.sharedPromise = delay(100);
                        await Promise.allSettled([nestedSuccess(), nestedFail()])
                            .then(results => results
                                .map(result => result.status +
                                    (result.status === 'fulfilled'
                                        ? ':' + result.value
                                        : ''))
                                .join(','));
                        """.trimIndent()
                    )
                }
            )
        }
    }

    @Test
    fun runAsyncRejected() = runTest {
        quickJs {
            asyncFunction("fetch") { error("Unavailable") }

            assertFails {
                evaluate<Any?>("await fetch()")
            }.also {
                assertContains(it.message!!, "Unavailable")
            }
        }
    }

    @Test
    fun runDelayAsPromise() = runTest {
        quickJs {
            asyncFunction("delay") { delay(it[0] as Long) }

            var result: String? = null
            launch {
                result = evaluate<String>("await delay(1000); 'OK'")
            }
            advanceTimeBy(500)
            assertEquals(null, result)
            advanceTimeBy(501)
            assertEquals("OK", result)
        }
    }

    @Test
    fun runMultiplePromises() = runTest {
        quickJs {
            var result: Any? = null

            function("update") { result = it[0] }

            asyncFunction("delay") { delay(it[0] as Long) }

            val evalJob = launch {
                evaluate<String>(
                    """
                        update("Started");
                        await delay(1000);
                        update("Next");
                        await delay(1000);
                        update("Done");
                    """.trimIndent()
                )
            }

            advanceTimeBy(10)
            assertEquals("Started", result)
            advanceTimeBy(1001)
            assertEquals("Next", result)
            advanceTimeBy(1001)
            assertEquals("Done", result)

            evalJob.join()
        }
    }

    @Test
    fun runPromiseDotAll() = runTest {
        quickJs {
            asyncFunction("delay") { delay(it[0] as Long) }

            var result: String? = null
            val evalJob = launch {
                result = evaluate<String>(
                    """
                        await Promise.all([delay(1000), delay(2000)]);
                        "OK";
                    """.trimIndent()
                )
            }
            advanceTimeBy(500)
            assertEquals(null, result)
            advanceTimeBy(1501)
            assertEquals("OK", result)
            evalJob.join()
        }
    }

    @Test
    fun runConcurrentJobs() = runTest {
        quickJs(Dispatchers.Default) {
            var value = 0
            val mutex = Mutex()

            asyncFunction("runJob") { mutex.withLock { value++ } }

            evaluate<Any?>(
                """
                for (let i = 0; i < 1000; i++) {
                    runJob();
                }
                """.trimIndent()
            )

            assertEquals(1000, value)
        }
    }

    @Test
    fun runWithoutAwait() = runTest {
        quickJs {
            asyncFunction("fetch") { delay(1000) }
            asyncFunction("fail") { error("Failed") }

            assertEquals("Promise { <state>: \"fulfilled\" }", evaluate("fetch()"))
            assertFails { evaluate<String>("fail()") }.also {
                assertContains(it.message!!, "Failed")
            }
        }
    }

    @Test
    fun compileAndEvalAsync() = runTest {
        quickJs {
            asyncFunction("delay") { delay(it[0] as Long) }

            val bytecode = compile(
                code = """
                    await delay(100);
                    "OK";
                """.trimIndent()
            )
            assertEquals("OK", evaluate(bytecode))
        }
    }

    @Test
    fun compileAndEvalAsyncModule() = runTest {
        quickJs {
            var result: String? = null
            function("returns") { result = it.first() as String }

            asyncFunction("delay") { delay(it[0] as Long) }

            val bytecode = compile(
                code = """
                    await delay(100);
                    returns("OK");
                """.trimIndent(),
                asModule = true,
            )
            evaluate<Any?>(bytecode)
            assertEquals("OK", result)
        }
    }

    @Test
    fun cancelParentCoroutine() = runTest {
        var instance: QuickJs? = null
        val job = launch {
            // Don't known why but the testScheduler from the test scope
            // won't work on Kotlin/Native
            quickJs(StandardTestDispatcher()) {
                instance = this

                asyncFunction("delay") {
                    delay(it[0] as Long)
                }

                evaluate<String>("delay(1000); 'OK'")

                assertTrue(false)
            }
        }

        launch {
            delay(500)
            job.cancel()
            yield()
            assertTrue(instance!!.isClosed)
        }
    }

    @Test
    fun setTimeout() = runTest {
        quickJs {
            var delayedCount = 0

            val delays = mutableMapOf<Long, Job>()

            function("delayed") { delayedCount++ }

            function<Long, Unit>("cancelDelay") { delays[it]?.cancel() }

            asyncFunction("delay") {
                coroutineScope {
                    val millis = it[0] as Long
                    val id = it[1] as Long
                    val job = async { delay(millis) }
                    delays[id] = job
                    try {
                        job.await()
                    } finally {
                        delays.remove(id)
                    }
                }
            }

            evaluate<Any?>(
                """
                    let _timeoutId = 0;
                    
                    function setTimeout(callback, millis) {
                        _timeoutId++;
                        delay(millis, _timeoutId)
                            .then(() => callback())
                            .catch(() => {}); // Delay has canceled
                        return _timeoutId;
                    }
                    
                    function clearTimeout(id) {
                        cancelDelay(id)
                    }
                    
                    const id = setTimeout(() => delayed(), 2000);
                    setTimeout(() => { delayed(); clearTimeout(id); }, 1000);
                """.trimIndent()
            )
            assertEquals(1, delayedCount)
        }
    }
}
