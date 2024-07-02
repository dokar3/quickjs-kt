package com.dokar.quickjs.converter

import kotlin.reflect.KClass
import kotlin.reflect.KType

@Suppress("unchecked_cast")
@PublishedApi
internal actual fun <T : Any?> castValueOr(
    value: Any?,
    expectedType: KType,
    fallback: (value: Any) -> T
): T {
    val nonNull = value ?: return null as T
    val expectedClass = expectedType.classifier as KClass<*>
    if (expectedClass.isInstance(value)) {
        return nonNull as T
    }
    when (expectedClass) {
        Byte::class -> {
            if (value is Long) {
                return safeCastToByteOrThrow(value) as T
            }
        }

        Short::class -> {
            if (value is Long) {
                return safeCastToShortOrThrow(value) as T
            }
        }

        Int::class -> {
            when (value) {
                is Long -> {
                    // Long -> int
                    return safeCastToIntOrThrow(value) as T
                }

                is Double -> {
                    // Double -> int
                    return value.toInt() as T
                }
            }
        }

        Float::class -> {
            when (value) {
                is Double -> {
                    // Double -> float
                    return safeCastToFloatOrThrow(value) as T
                }

                is Long -> {
                    // Long -> float
                    return value.toFloat() as T
                }
            }
        }

        Double::class -> {
            if (value is Long) {
                // Long -> double
                return value.toDouble() as T
            }
        }
    }
    return fallback(value)
}
