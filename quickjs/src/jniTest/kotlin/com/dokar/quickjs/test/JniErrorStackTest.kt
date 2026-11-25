package com.dokar.quickjs.test

import com.dokar.quickjs.binding.asyncFunction
import com.dokar.quickjs.binding.function
import com.dokar.quickjs.quickJs
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class JniErrorStackTest {
    @Test
    fun throwableWithEmptyStackTraceIsCaughtCleanly() = runTest {
        quickJs {
            function("throwEmptyStack") {
                throw IllegalStateException("empty stack").apply {
                    stackTrace = emptyArray()
                }
            }
            asyncFunction("throwEmptyStackAsync") {
                throw IllegalStateException("empty stack async").apply {
                    stackTrace = emptyArray()
                }
            }

            val syncResult = evaluate<String>(
                """
                    try {
                        throwEmptyStack();
                    } catch (e) {
                        e.message + ":" + typeof e.stack;
                    }
                """.trimIndent(),
            )
            // JS_NewError always initializes stack as a string (JS backtrace or empty) when no host stack is set.
            assertEquals("empty stack:string", syncResult)

            val asyncResult = evaluate<String>(
                """
                    try {
                        await throwEmptyStackAsync();
                    } catch (e) {
                        e.message + ":" + typeof e.stack;
                    }
                """.trimIndent(),
            )
            assertEquals("empty stack async:string", asyncResult)
        }
    }

    @Test
    fun throwingGetStackTraceClearsSecondaryJniException() = runTest {
        quickJs {
            function("throwBrokenStack") {
                throw object : RuntimeException("broken stack") {
                    override fun getStackTrace(): Array<StackTraceElement> {
                        throw IllegalStateException("getStackTrace failed")
                    }
                }
            }
            function("ping") { "pong" }

            val result = evaluate<String>(
                """
                    let msg = "";
                    try {
                        throwBrokenStack();
                    } catch (e) {
                        msg = e.message;
                    }
                    msg + ":" + ping();
                """.trimIndent(),
            )
            assertEquals("broken stack:pong", result)
        }
    }
}
