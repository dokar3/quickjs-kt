package com.dokar.quickjs.test

import com.dokar.quickjs.QuickJsException
import com.dokar.quickjs.binding.define
import com.dokar.quickjs.binding.function
import com.dokar.quickjs.quickJs
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
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
        }.also {
            assertEquals(QuickJsException::class, it::class)
            assertContains(it.message!!, "SyntaxError")
        }
    }

    @Test
    fun evalWithTypeError() = runTest {
        assertFails {
            quickJs {
                evaluate<Any?>("const x = 0; x();")
            }
        }.also {
            assertEquals(QuickJsException::class, it::class)
            assertContains(it.message!!, "TypeError")
        }
    }

    @Test
    fun evalWithReferenceError() = runTest {
        assertFails {
            quickJs {
                evaluate<Any?>("x + y")
            }
        }.also {
            assertEquals(QuickJsException::class, it::class)
            assertContains(it.message!!, "ReferenceError")
        }
    }

    @Test
    fun evalThrowArbitrary() = runTest {
        assertFails {
            quickJs {
                evaluate<Any?>("throw 'Bad'")
            }
        }.also {
            assertEquals(QuickJsException::class, it::class)
            assertEquals("Bad", it.message)
        }
        assertFails {
            quickJs {
                evaluate<Any?>("throw 1")
            }
        }.also {
            assertEquals(QuickJsException::class, it::class)
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
            assertEquals(QuickJsException::class, it::class)
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
        assertFails {
            quickJs {
                function("call") { error("Something wrong") }
                evaluate<Any?>("call()")
            }
        }.also {
            assertContains(it.message!!, "Something wrong")
        }
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
                assertContains(it.message!!, "Set not allowed")
            }
            assertFails { evaluate<Any?>("app.launch()") }.also {
                assertContains(it.message!!, "Call not allowed")
            }
        }
    }
}