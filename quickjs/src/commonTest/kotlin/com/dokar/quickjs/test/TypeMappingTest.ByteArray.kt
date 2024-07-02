package com.dokar.quickjs.test

import com.dokar.quickjs.binding.function
import com.dokar.quickjs.quickJs
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals

class UInt8ArrayMapping {
    @Test
    fun byteArrays() = runTest {
        val array = byteArrayOf(0, 1, 10, 127)
        quickJs {
            function("getBuffer") { array }

            assertContentEquals(array, evaluate<ByteArray>("getBuffer()"))
        }
    }

    @OptIn(ExperimentalUnsignedTypes::class)
    @Test
    fun uByteArrays() = runTest {
        val array = ubyteArrayOf(0u, 10u, 100u, 255u)
        quickJs {
            function("getBuffer") { array }

            assertContentEquals(array, evaluate<UByteArray>("getBuffer()"))
        }
    }
}