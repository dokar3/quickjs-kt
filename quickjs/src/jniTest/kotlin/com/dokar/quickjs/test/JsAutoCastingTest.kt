package com.dokar.quickjs.test

import com.dokar.quickjs.typeConvertOr
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails

class JsAutoCastingTest {
    @Test
    fun unboxBooleans() {
        assertEquals(
            false, typeConvertOrThrow(java.lang.Boolean.valueOf(false), Boolean::class.java)
        )
    }

    @Test
    fun unboxInts() {
        assertEquals(
            1, typeConvertOrThrow(Integer.valueOf(1), Int::class.java)
        )
    }

    @Test
    fun unboxLongs() {
        assertEquals(
            1L, typeConvertOrThrow(java.lang.Long.valueOf(1L), Long::class.java)
        )
    }

    @Test
    fun unboxFloats() {
        assertEquals(
            1f, typeConvertOrThrow(java.lang.Float.valueOf(1f), Float::class.java)
        )
    }

    @Test
    fun unboxDoubles() {
        assertEquals(
            1.0, typeConvertOrThrow(java.lang.Double.valueOf(1.0), Double::class.java)
        )
    }

    @Test
    fun longToFloat() {
        assertEquals(1f, typeConvertOrThrow(1L, Float::class.java))
    }

    @Test
    fun longToDouble() {
        assertEquals(1.0, typeConvertOrThrow(1L, Double::class.java))
    }

    @Test
    fun longToInt() {
        assertEquals(1, typeConvertOrThrow(1L, Int::class.java))
        assertEquals(1, typeConvertOrThrow(1L, java.lang.Integer::class.java))
        assertEquals(1, typeConvertOrThrow(java.lang.Long.valueOf(1L), Int::class.java))
        assertEquals(
            1, typeConvertOrThrow(java.lang.Long.valueOf(1L), java.lang.Integer::class.java)
        )
    }

    @Test
    fun doubleToFloat() {
        assertEquals(1f, typeConvertOrThrow(1.0, Float::class.java))
        assertEquals(1f, typeConvertOrThrow(1.0, java.lang.Float::class.java))
        assertEquals(1f, typeConvertOrThrow(java.lang.Double.valueOf(1.0), Float::class.java))
        assertEquals(
            1f, typeConvertOrThrow(java.lang.Double.valueOf(1.0), java.lang.Float::class.java)
        )
    }

    @Test
    fun unsupportedCasting() {
        assertFails { typeConvertOrThrow("", Float::class.java) }
        // Not needed since jni won't pass ints
        assertFails { typeConvertOrThrow(1, Float::class.java) }
        assertFails { typeConvertOrThrow(1, Long::class.java) }
    }
}

private inline fun <reified T : Any?> typeConvertOrThrow(
    value: Any?,
    expectedType: Class<*>,
): T {
    Result.success(2).getOrElse { error("") }
    return typeConvertOr<T>(
        value,
        expectedType
    ) {
        error("Failed to convert ${it::class} to $expectedType")
    }
}

