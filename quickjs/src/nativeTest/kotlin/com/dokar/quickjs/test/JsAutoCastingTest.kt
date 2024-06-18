package com.dokar.quickjs.test

import com.dokar.quickjs.typeConvertOr
import kotlin.reflect.KType
import kotlin.reflect.typeOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails

class JsAutoCastingTest {

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
    }

    @Test
    fun doubleToFloat() {
        assertEquals(1f, typeConvertOrThrow(1.0, typeOf<Float>()))
    }

    @Test
    fun unsupportedCasting() {
        assertFails { typeConvertOrThrow("", typeOf<Float>()) }
        assertFails { typeConvertOrThrow(1, typeOf<Float>()) }
        assertFails { typeConvertOrThrow(1, typeOf<Long>()) }
    }
}

private fun <T : Any?> typeConvertOrThrow(
    value: Any?,
    expectedType: KType,
): T {
    return typeConvertOr<T>(
        value,
        expectedType
    ) {
        error("Failed to convert ${it::class} to $expectedType")
    }
}