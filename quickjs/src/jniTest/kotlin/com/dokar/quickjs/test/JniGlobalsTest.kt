package com.dokar.quickjs.test

import com.dokar.quickjs.QuickJs
import com.dokar.quickjs.binding.function
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class JniGlobalsTest {
    @Test
    fun testUnitReturn() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        QuickJs.create(dispatcher).use { quickJs ->
            quickJs.function("testUnit") { }
            val result = quickJs.evaluate<Any?>("testUnit()")
            assertEquals(null, result)
        }
    }

    @OptIn(ExperimentalUnsignedTypes::class)
    @Test
    fun testUByteArrayReturn() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        QuickJs.create(dispatcher).use { quickJs ->
            val bytes = ubyteArrayOf(1u, 2u, 3u)
            quickJs.function("testUByteArray") { bytes }
            val result = quickJs.evaluate<UByteArray>("testUByteArray()")
            assertEquals(3, result.size)
            assertEquals(1u, result[0])
            assertEquals(2u, result[1])
            assertEquals(3u, result[2])
        }
    }
}
