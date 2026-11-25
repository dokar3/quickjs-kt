package com.dokar.quickjs.test

import com.dokar.quickjs.binding.asyncFunction
import com.dokar.quickjs.binding.function
import com.dokar.quickjs.quickJs
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class JniErrorStackTest {
    @Test
    fun hostExceptionPreservesJoinedStackFrames() = runTest {
        val frames = arrayOf(
            StackTraceElement("com.example.Demo", "first", "Demo.kt", 12),
            StackTraceElement("com.example.Demo", "second", "Demo.kt", 34),
        )
        val expectedStack = frames.joinToString("\n") { it.toString() }

        quickJs {
            function("throwSync") {
                throw IllegalStateException("sync boom").apply { stackTrace = frames }
            }
            asyncFunction("throwAsync") {
                throw IllegalStateException("async boom").apply { stackTrace = frames }
            }

            val syncStack = evaluate<String>(
                """
                    try {
                        throwSync();
                    } catch (e) {
                        e.stack;
                    }
                """.trimIndent(),
            )
            assertEquals(expectedStack, syncStack)

            val asyncStack = evaluate<String>(
                """
                    await throwAsync().catch(e => e.stack);
                """.trimIndent(),
            )
            assertEquals(expectedStack, asyncStack)
        }
    }

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

            // VM exception dispatch attaches a JS backtrace when no host stack property was defined.
            val syncResult = evaluate<String>(
                """
                    try {
                        throwEmptyStack();
                    } catch (e) {
                        e.message + ":" + typeof e.stack;
                    }
                """.trimIndent(),
            )
            assertEquals("empty stack:string", syncResult)

            val awaitResult = evaluate<String>(
                """
                    try {
                        await throwEmptyStackAsync();
                    } catch (e) {
                        e.message + ":" + typeof e.stack;
                    }
                """.trimIndent(),
            )
            assertEquals("empty stack async:string", awaitResult)

            // Direct Promise.catch receives the rejection object without passing through VM throw.
            val catchResult = evaluate<String>(
                """
                    await throwEmptyStackAsync().catch(e => e.message + ":" + typeof e.stack);
                """.trimIndent(),
            )
            assertEquals("empty stack async:undefined", catchResult)
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
