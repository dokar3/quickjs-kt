package com.dokar.quickjs.test

import com.dokar.quickjs.converter.castValueOr
import kotlin.reflect.KType
import kotlin.reflect.typeOf
import kotlin.test.Test
import kotlin.test.assertEquals

class JvmValueCastingTest {
    @Test
    fun unboxBooleans() {
        assertEquals(
            false, castValueOrThrow(java.lang.Boolean.valueOf(false), typeOf<Boolean>())
        )
    }

    @Test
    fun unboxInts() {
        assertEquals(
            1, castValueOrThrow(Integer.valueOf(1), typeOf<Int>())
        )
    }

    @Test
    fun unboxLongs() {
        assertEquals(
            1L, castValueOrThrow(java.lang.Long.valueOf(1L), typeOf<Long>())
        )
    }

    @Test
    fun unboxFloats() {
        assertEquals(
            1f, castValueOrThrow(java.lang.Float.valueOf(1f), typeOf<Float>())
        )
    }

    @Test
    fun unboxDoubles() {
        assertEquals(
            1.0, castValueOrThrow(java.lang.Double.valueOf(1.0), typeOf<Double>())
        )
    }

    @Test
    fun longToInt() {
        assertEquals(1, castValueOrThrow(1L, typeOf<Integer>()))
        assertEquals(1, castValueOrThrow(java.lang.Long.valueOf(1L), typeOf<Int>()))
        assertEquals(
            1, castValueOrThrow(java.lang.Long.valueOf(1L), typeOf<Integer>())
        )
    }

    @Test
    fun doubleToFloat() {
        assertEquals(1f, castValueOrThrow(java.lang.Double.valueOf(1.0), typeOf<Float>()))
        assertEquals(
            1f, castValueOrThrow(java.lang.Double.valueOf(1.0), typeOf<java.lang.Float>())
        )
    }
}

private inline fun <reified T : Any?> castValueOrThrow(
    value: Any?,
    expectedType: KType,
): T {
    Result.success(2).getOrElse { error("") }
    return castValueOr<T>(
        value,
        expectedType
    ) {
        error("Failed to convert ${it::class} to $expectedType")
    }
}

