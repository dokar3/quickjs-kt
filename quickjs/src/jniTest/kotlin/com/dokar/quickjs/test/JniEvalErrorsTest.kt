package com.dokar.quickjs.test

import com.dokar.quickjs.binding.asyncFunction
import com.dokar.quickjs.binding.function
import com.dokar.quickjs.quickJs
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * JVM-only JNI error conversion tests: getStackTrace() has different signatures on
 * JVM and Kotlin/Native, so the throwing-Throwable regression lives here.
 */
class JniEvalErrorsTest {

    /**
     * Regression for the strict Error.prototype.stack setter era: JNI conversion must
     * survive a Throwable whose getMessage()/getStackTrace() throw, fall back to an
     * empty message and keep the engine usable after the catch. The read flags prove
     * both throwing branches were actually exercised, not skipped.
     */
    @Test
    fun throwingMessageAndStackTraceAreConvertedWithoutKillingTheEngine() = runTest {
        quickJs {
            var caughtMessage: String? = null
            var afterCatch: Boolean? = null
            var messageRead = false
            var stackRead = false
            asyncFunction("doNothing") {}
            function("throwError") {
                throw object : Throwable() {
                    override val message: String?
                        get() {
                            messageRead = true
                            throw IllegalStateException("msg failed")
                        }
                    override fun getStackTrace(): Array<StackTraceElement> {
                        stackRead = true
                        throw IllegalStateException("stack failed")
                    }
                }
            }
            function("capture") { args -> caughtMessage = args[0] as String }
            function("markAfterCatch") { afterCatch = true }

            evaluate<Any?>(
                """
                    await doNothing();
                    try {
                        throwError();
                    } catch (e) {
                        capture(e.message);
                    }
                    markAfterCatch();
                """.trimIndent(),
                filename = "test.js",
                asModule = true,
            )

            assertEquals(true, messageRead)
            assertEquals(true, stackRead)
            assertEquals("", caughtMessage)
            assertEquals(true, afterCatch)
        }
    }
}
