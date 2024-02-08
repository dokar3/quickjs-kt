package com.dokar.quickjs.test

import com.dokar.quickjs.binding.define
import com.dokar.quickjs.binding.function
import com.dokar.quickjs.quickJs
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFails

class EvalErrorsTest {
    @Test
    fun evalWithSyntaxError() = runTest {
        assertFails {
            quickJs {
                evaluate<Any?>("fn test() {}")
            }
        }
    }

    @Test
    fun evalThrowArbitrary() = runTest {
        assertFails {
            quickJs {
                evaluate<Any?>("throw 'Bad'")
            }
        }.also {
            assertEquals("Bad", it.message)
        }
        assertFails {
            quickJs {
                evaluate<Any?>("throw 1")
            }
        }.also {
            assertEquals("1", it.message)
        }
    }

    @Test
    fun evalThrowCustomErrors() = runTest {
        assertFails {
            quickJs {
                evaluate<Any?>("throw new Error('Something wrong')")
            }
        }.also {
            assertContains(it.message!!, "Something wrong")
        }
    }

    @Test
    fun evalAfterClosed() = runTest {
        assertFails {
            val quickJs = quickJs { this }
            quickJs.evaluate("1 + 2")
        }
    }

    @Test
    fun evalWithFunctionCallErrors() = runTest {
        val exception = assertFails {
            quickJs {
                function("call") { error("Something wrong") }
                evaluate<Any?>("call()")
            }
        }
        assertContains(exception.message!!, "Something wrong")
    }

    @Test
    fun evalWithObjectBindingErrors() = runTest {
        quickJs {
            define("app") {
                property<String>("version") {
                    getter { error("Get not allowed") }
                    setter { error("Set not allowed") }
                }

                function("launch") { error("Call not allowed") }
            }

            assertFails { evaluate<Any?>("app.version") }.also {
                assertContains(it.message!!, "Get not allowed")
            }
            assertFails { evaluate<Any?>("app.version = '1.0.0'") }.also {
                assertEquals(it.message!!, "Set not allowed")
            }
            assertFails { evaluate<Any?>("app.launch()") }.also {
                assertContains(it.message!!, "Call not allowed")
            }
        }
    }
}