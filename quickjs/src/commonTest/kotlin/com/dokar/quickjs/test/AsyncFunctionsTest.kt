package com.dokar.quickjs.test

import com.dokar.quickjs.QuickJs
import com.dokar.quickjs.binding.asyncFunction
import com.dokar.quickjs.binding.function
import com.dokar.quickjs.quickJs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
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

            function("cancelDelay") { delays[it.first()]?.cancel() }

            asyncFunction("delay") {
                coroutineScope {
                    val job = async { delay(it.first() as Long) }
                    delays[it[1] as Long] = job
                    job.await()
                }
            }

            evaluate<Any?>(
                """
                    function setTimeout(callback, millis, id) {
                        delay(millis, id).then(() => callback()).catch(() => {});
                    }
                    
                    function clearTimeout(id) {
                        cancelDelay(id)
                    }
                    
                    setTimeout(() => delayed(), 2000, 1);
                    setTimeout(() => { delayed(); clearTimeout(1); }, 1000, 2);
                """.trimIndent()
            )
            assertEquals(1, delayedCount)
        }
    }
}