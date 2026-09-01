package com.dokar.quickjs.test

import com.dokar.quickjs.binding.function
import com.dokar.quickjs.quickJs
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Verifies code-unit-preserving round trips for UTF-16 edge cases in the JVM JNI bridge.
 */
class JniUnicodeStringTest {
    /** Verifies that embedded NUL is not truncated by C-string handling. */
    @Test
    fun embeddedNullRoundTrips() = runTest {
        val text = "before\u0000after"
        quickJs {
            function("returnNull") { text }

            assertEquals(text, evaluate<String>("returnNull()"))
            assertEquals(text, evaluate<String>("'before\\u0000after'"))
            assertEquals(text.length, evaluate<Int>("returnNull().length"))
        }
    }

    /** Verifies that WTF-8 preserves unpaired UTF-16 surrogate code units. */
    @Test
    fun unpairedSurrogatesRoundTrip() = runTest {
        val highSurrogate = charArrayOf('\uD83D').concatToString()
        val lowSurrogate = charArrayOf('\uDE00').concatToString()

        quickJs {
            function("returnHigh") { highSurrogate }
            function("returnLow") { lowSurrogate }

            assertEquals(highSurrogate, evaluate<String>("returnHigh()"))
            assertEquals(lowSurrogate, evaluate<String>("returnLow()"))
            assertEquals(highSurrogate, evaluate<String>("'\\uD83D'"))
            assertEquals(lowSurrogate, evaluate<String>("'\\uDE00'"))
        }
    }
}
