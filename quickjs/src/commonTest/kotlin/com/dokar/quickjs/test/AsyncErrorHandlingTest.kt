package com.dokar.quickjs.test

import com.dokar.quickjs.binding.asyncFunction
import com.dokar.quickjs.quickJs
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AsyncErrorHandlingTest {
    @Test
    fun uncaughtWithAwait() = runTest {
        quickJs {
            asyncFunction("fetch") { error("Error occurred") }

            assertFails { evaluate("await fetch()") }.also {
                assertContains(it.message!!, "Error occurred")
            }
        }
    }

    @Test
    fun uncaughtWithNoAwait() = runTest {
        quickJs {
            asyncFunction("fetch") { error("Error occurred") }

            assertFails { evaluate("fetch()") }.also {
                assertContains(it.message!!, "Error occurred")
            }
        }
    }

    @Test
    fun withTryCatch() = runTest {
        quickJs {
            asyncFunction("fetch") { error("Error occurred") }

            val result = evaluate<String>(
                """
                    let result = null;
                    try {
                        await fetch()
                    } catch(e) {
                        result = "Caught";
                    }
                    result;
                """.trimIndent()
            )
            assertEquals("Caught", result)
        }
    }

    @Test
    fun withPromiseCatchNoAwait() = runTest {
        quickJs {
            asyncFunction("fetch") { error("Error occurred") }

            val result = evaluate<String>("fetch().catch((e) => {}); 'Caught';")
            assertEquals("Caught", result)
        }
    }

    @Test
    fun withPromiseCatch() = runTest {
        quickJs {
            asyncFunction("fetch") { error("Error occurred") }

            val result = evaluate<String>("await fetch().catch((e) => {}); 'Caught';")
            assertEquals("Caught", result)
        }
    }

    @Test
    fun jobCancelingWithUncaughtNoAwait() = runTest {
        quickJs {
            var isDelayed = false
            asyncFunction("fetch") { error("Error occurred") }

            asyncFunction("delay") {
                delay(500)
                isDelayed = true
            }

            assertFails {
                evaluate("delay(); fetch();")
            }.also {
                assertContains(it.message!!, "Error occurred")
            }
            assertFalse(isDelayed)
        }
    }

    @Test
    fun jobCancelingWithUncaughtAndAwait() = runTest {
        quickJs {
            var isDelayed = false
            asyncFunction("fetch") { error("Error occurred") }

            asyncFunction("delay") {
                delay(500)
                isDelayed = true
            }

            assertFails {
                evaluate("delay(); await fetch();")
            }.also {
                assertContains(it.message!!, "Error occurred")
            }
            assertTrue(isDelayed)
        }
    }

    @Test
    fun jobCancelingWithUncaughtAndPromiseAll() = runTest {
        quickJs {
            var isDelayed = false
            asyncFunction("fetch") { error("Error occurred") }

            asyncFunction("delay") {
                delay(500)
                isDelayed = true
            }

            assertFails {
                evaluate("await Promise.all([delay(), fetch()]);")
            }.also {
                assertContains(it.message!!, "Error occurred")
            }
            assertTrue(isDelayed)
        }
    }

    @Test
    fun jobExecutingWithCaught() = runTest {
        quickJs {
            var isDelayed = false
            asyncFunction("fetch") { error("Error occurred") }

            asyncFunction("delay") {
                delay(500)
                isDelayed = true
            }

            evaluate<Any?>("delay(); fetch().catch((e) => {});")
            assertTrue(isDelayed)
        }
    }
}