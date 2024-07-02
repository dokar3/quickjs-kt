package com.dokar.quickjs.converter

import com.dokar.quickjs.qjsError
import kotlin.reflect.KType

@PublishedApi
internal expect fun <T : Any?> castValueOr(
    value: Any?,
    expectedType: KType,
    fallback: (value: Any) -> T
): T

internal fun safeCastToByteOrThrow(value: Long): Byte {
    if (value < Byte.MIN_VALUE || value > Byte.MAX_VALUE) {
        qjsError("Cannot cast Long($value) to Byte: value out of range.")
    }
    return value.toByte()
}

internal fun safeCastToShortOrThrow(value: Long): Short {
    if (value < Short.MIN_VALUE || value > Short.MAX_VALUE) {
        qjsError("Cannot cast Long($value) to Short: value out of range.")
    }
    return value.toShort()
}

internal fun safeCastToIntOrThrow(value: Long): Int {
    if (value < Int.MIN_VALUE || value > Int.MAX_VALUE) {
        qjsError("Cannot cast Long($value) to Int: value out of range.")
    }
    return value.toInt()
}

internal fun safeCastToFloatOrThrow(value: Double): Float {
    if (value < Float.MIN_VALUE || value > Float.MAX_VALUE) {
        qjsError("Cannot cast Double($value) to Float: value out of range.")
    }
    return value.toFloat()
}