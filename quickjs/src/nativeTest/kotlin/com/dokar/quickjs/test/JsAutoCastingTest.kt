package com.dokar.quickjs.test

import com.dokar.quickjs.jsAutoCastOrThrow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails

class JsAutoCastingTest {
    @Test
    fun longToFloat() {
        assertEquals(1f, jsAutoCastOrThrow(1L, Float::class))
    }

    @Test
    fun longToDouble() {
        assertEquals(1.0, jsAutoCastOrThrow(1L, Double::class))
    }

    @Test
    fun longToInt() {
        assertEquals(1, jsAutoCastOrThrow(1L, Int::class))
    }

    @Test
    fun doubleToFloat() {
        assertEquals(1f, jsAutoCastOrThrow(1.0, Float::class))
    }

    @Test
    fun unsupportedCasting() {
        assertFails { jsAutoCastOrThrow("", Float::class) }
        assertFails { jsAutoCastOrThrow(1, Float::class) }
        assertFails { jsAutoCastOrThrow(1, Long::class) }
    }
}