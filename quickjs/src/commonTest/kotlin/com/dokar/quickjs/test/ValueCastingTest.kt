package com.dokar.quickjs.test

import com.dokar.quickjs.converter.castValueOr
import kotlin.reflect.KType
import kotlin.reflect.typeOf
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFails

class ValueCastingTest {
    @Test
    fun longToFloat() {
        assertEquals(1f, castValueOrThrow(1L, typeOf<Float>()))
    }

    @Test
    fun longToDouble() {
        assertEquals(1.0, castValueOrThrow(1L, typeOf<Double>()))
    }

    @Test
    fun longToInt() {
        assertEquals(1, castValueOrThrow(1L, typeOf<Int>()))
    }

    @Test
    fun longToIntOutOfRange() {
        assertFails { castValueOrThrow(Int.MIN_VALUE.toLong() - 1, typeOf<Int>()) }
            .also { assertContains(it.message!!, "value out of range.") }
        assertFails { castValueOrThrow(Int.MAX_VALUE.toLong() + 1, typeOf<Int>()) }
            .also { assertContains(it.message!!, "value out of range.") }
    }

    @Test
    fun longToShort() {
        assertEquals(1.toShort(), castValueOrThrow(1L, typeOf<Short>()))
    }

    @Test
    fun longToShortOutOfRange() {
        assertFails { castValueOrThrow(Short.MIN_VALUE.toLong() - 1, typeOf<Short>()) }
            .also { assertContains(it.message!!, "value out of range.") }
        assertFails { castValueOrThrow(Short.MAX_VALUE.toLong() + 1, typeOf<Short>()) }
            .also { assertContains(it.message!!, "value out of range.") }
    }

    @Test
    fun longToByte() {
        assertEquals(1.toByte(), castValueOrThrow(1L, typeOf<Byte>()))
    }

    @Test
    fun longToByteOutOfRange() {
        assertFails { castValueOrThrow(Byte.MIN_VALUE.toLong() - 1, typeOf<Byte>()) }
            .also { assertContains(it.message!!, "value out of range.") }
        assertFails { castValueOrThrow(Byte.MAX_VALUE.toLong() + 1, typeOf<Byte>()) }
            .also { assertContains(it.message!!, "value out of range.") }
    }

    @Test
    fun doubleToFloat() {
        assertEquals(1f, castValueOrThrow(1.0, typeOf<Float>()))
    }

    @Test
    fun doubleToFloatOutOfRange() {
        assertFails { castValueOrThrow(Double.MIN_VALUE, typeOf<Float>()) }
            .also { assertContains(it.message!!, "value out of range.") }
        // Note: Float.MAX_VALUE.toDouble() + 1 > Float.MAX_VALUE won't work
        assertFails { castValueOrThrow(Double.MAX_VALUE, typeOf<Float>()) }
            .also { assertContains(it.message!!, "value out of range.") }
    }

    @Test
    fun unsupportedCasting() {
        assertFails { castValueOrThrow("", typeOf<Float>()) }
        assertFails { castValueOrThrow(1, typeOf<Float>()) }
        assertFails { castValueOrThrow(1, typeOf<Long>()) }
    }
}

private fun <T : Any?> castValueOrThrow(
    value: Any?,
    expectedType: KType,
): T {
    return castValueOr<T>(
        value,
        expectedType
    ) {
        error("Failed to convert ${it::class} to $expectedType")
    }
}