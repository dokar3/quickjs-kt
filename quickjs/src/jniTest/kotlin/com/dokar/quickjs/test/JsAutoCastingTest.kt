package com.dokar.quickjs.test

import com.dokar.quickjs.typeConvertOr
import kotlin.reflect.KType
import kotlin.reflect.typeOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails

class JsAutoCastingTest {
    @Test
    fun unboxBooleans() {
        assertEquals(
            false, typeConvertOrThrow(java.lang.Boolean.valueOf(false), typeOf<Boolean>())
        )
    }

    @Test
    fun unboxInts() {
        assertEquals(
            1, typeConvertOrThrow(Integer.valueOf(1), typeOf<Int>())
        )
    }

    @Test
    fun unboxLongs() {
        assertEquals(
            1L, typeConvertOrThrow(java.lang.Long.valueOf(1L), typeOf<Long>())
        )
    }

    @Test
    fun unboxFloats() {
        assertEquals(
            1f, typeConvertOrThrow(java.lang.Float.valueOf(1f), typeOf<Float>())
        )
    }

    @Test
    fun unboxDoubles() {
        assertEquals(
            1.0, typeConvertOrThrow(java.lang.Double.valueOf(1.0), typeOf<Double>())
        )
    }

    @Test
    fun longToFloat() {
        assertEquals(1f, typeConvertOrThrow(1L, typeOf<Float>()))
    }

    @Test
    fun longToDouble() {
        assertEquals(1.0, typeConvertOrThrow(1L, typeOf<Double>()))
    }

    @Test
    fun longToInt() {
        assertEquals(1, typeConvertOrThrow(1L, typeOf<Int>()))
        assertEquals(1, typeConvertOrThrow(1L, typeOf<java.lang.Integer>()))
        assertEquals(1, typeConvertOrThrow(java.lang.Long.valueOf(1L), typeOf<Int>()))
        assertEquals(
            1, typeConvertOrThrow(java.lang.Long.valueOf(1L), typeOf<java.lang.Integer>())
        )
    }

    @Test
    fun doubleToFloat() {
        assertEquals(1f, typeConvertOrThrow(1.0, typeOf<Float>()))
        assertEquals(1f, typeConvertOrThrow(1.0, typeOf<Float>()))
        assertEquals(1f, typeConvertOrThrow(java.lang.Double.valueOf(1.0), typeOf<Float>()))
        assertEquals(
            1f, typeConvertOrThrow(java.lang.Double.valueOf(1.0), typeOf<java.lang.Float>())
        )
    }

    @Test
    fun unsupportedCasting() {
        assertFails { typeConvertOrThrow("", typeOf<Float>()) }
        // Not needed since jni won't pass ints
        assertFails { typeConvertOrThrow(1, typeOf<Float>()) }
        assertFails { typeConvertOrThrow(1, typeOf<Long>()) }
    }
}

private inline fun <reified T : Any?> typeConvertOrThrow(
    value: Any?,
    expectedType: KType,
): T {
    Result.success(2).getOrElse { error("") }
    return typeConvertOr<T>(
        value,
        expectedType
    ) {
        error("Failed to convert ${it::class} to $expectedType")
    }
}

