package com.dokar.quickjs.test

import com.dokar.quickjs.quickJs
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals

class QuickJsPropertiesTest {
    @Test
    fun version() = runTest {
        quickJs {
            assertContains(version, "20")
        }
    }

    @Test
    fun memoryLimit() =runTest {
        quickJs {
            assertEquals(-1, memoryLimit)
            val newLimit = 10 * 1024 * 1024L
            memoryLimit = newLimit
            assertEquals(newLimit, memoryUsage.mallocLimit)
        }
    }
}