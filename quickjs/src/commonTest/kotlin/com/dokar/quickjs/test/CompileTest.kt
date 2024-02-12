package com.dokar.quickjs.test

import com.dokar.quickjs.quickJs
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails

class CompileTest {
    @Test
    fun compileAndEvaluate() = runTest {
        quickJs {
            val buffer = compile("1 + 2")
            assertEquals(3, evaluate(buffer))
        }
    }

    @Test
    fun evaluateMalformedBytecode() = runTest {
        quickJs {
            assertFails { evaluate(byteArrayOf(0, 1, 2, 3, 4)) }
        }
    }
}