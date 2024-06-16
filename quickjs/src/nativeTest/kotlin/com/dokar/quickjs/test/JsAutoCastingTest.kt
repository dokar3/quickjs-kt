package com.dokar.quickjs.test

import com.dokar.quickjs.typeConvertOr
import kotlin.reflect.KClass
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails

class JsAutoCastingTest {

    @Test
    fun longToFloat() {
        assertEquals(1f, typeConvertOrThrow(1L, Float::class))
    }

    @Test
    fun longToDouble() {
        assertEquals(1.0, typeConvertOrThrow(1L, Double::class))
    }

    @Test
    fun longToInt() {
        assertEquals(1, typeConvertOrThrow(1L, Int::class))
    }

    @Test
    fun doubleToFloat() {
        assertEquals(1f, typeConvertOrThrow(1.0, Float::class))
    }

    @Test
    fun unsupportedCasting() {
        assertFails { typeConvertOrThrow("", Float::class) }
        assertFails { typeConvertOrThrow(1, Float::class) }
        assertFails { typeConvertOrThrow(1, Long::class) }
    }
}

private fun <T : Any?> typeConvertOrThrow(
    value: Any?,
    expectedType: KClass<*>,
): T {
    return typeConvertOr<T>(
        value,
        expectedType
    ) {
        error("Failed to convert ${it::class} to $expectedType")
    }
}