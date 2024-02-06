package com.dokar.quickjs.test

import com.dokar.quickjs.jsAutoCastOrThrow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails

class JsAutoCastingTest {
    @Test
    fun unboxBooleans() {
        assertEquals(
            false, jsAutoCastOrThrow(java.lang.Boolean.valueOf(false), Boolean::class.java)
        )
    }

    @Test
    fun unboxInts() {
        assertEquals(
            1, jsAutoCastOrThrow(Integer.valueOf(1), Int::class.java)
        )
    }

    @Test
    fun unboxLongs() {
        assertEquals(
            1L, jsAutoCastOrThrow(java.lang.Long.valueOf(1L), Long::class.java)
        )
    }

    @Test
    fun unboxFloats() {
        assertEquals(
            1f, jsAutoCastOrThrow(java.lang.Float.valueOf(1f), Float::class.java)
        )
    }

    @Test
    fun unboxDoubles() {
        assertEquals(
            1.0, jsAutoCastOrThrow(java.lang.Double.valueOf(1.0), Double::class.java)
        )
    }

    @Test
    fun longToFloat() {
        assertEquals(1f, jsAutoCastOrThrow(1L, Float::class.java))
    }

    @Test
    fun longToDouble() {
        assertEquals(1.0, jsAutoCastOrThrow(1L, Double::class.java))
    }

    @Test
    fun longToInt() {
        assertEquals(1, jsAutoCastOrThrow(1L, Int::class.java))
        assertEquals(1, jsAutoCastOrThrow(1L, java.lang.Integer::class.java))
        assertEquals(1, jsAutoCastOrThrow(java.lang.Long.valueOf(1L), Int::class.java))
        assertEquals(
            1, jsAutoCastOrThrow(java.lang.Long.valueOf(1L), java.lang.Integer::class.java)
        )
    }

    @Test
    fun doubleToFloat() {
        assertEquals(1f, jsAutoCastOrThrow(1.0, Float::class.java))
        assertEquals(1f, jsAutoCastOrThrow(1.0, java.lang.Float::class.java))
        assertEquals(1f, jsAutoCastOrThrow(java.lang.Double.valueOf(1.0), Float::class.java))
        assertEquals(
            1f, jsAutoCastOrThrow(java.lang.Double.valueOf(1.0), java.lang.Float::class.java)
        )
    }

    @Test
    fun unsupportedCasting() {
        assertFails { jsAutoCastOrThrow("", Float::class.java) }
        // Not needed since jni won't pass ints
        assertFails { jsAutoCastOrThrow(1, Float::class.java) }
        assertFails { jsAutoCastOrThrow(1, Long::class.java) }
    }
}