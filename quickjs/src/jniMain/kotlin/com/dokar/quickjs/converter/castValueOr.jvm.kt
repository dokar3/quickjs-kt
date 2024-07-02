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
    val expectedCls = expectedType.classifier as KClass<*>
    if ((expectedCls).isInstance(value)) {
        return nonNull as T
    }
    when (expectedCls.java) {
        Boolean::class.java -> {
            if (value is Boolean) {
                // Boolean -> boolean
                return (value == true) as T
            }
        }

        Long::class.java -> {
            if (value is Long) {
                // Long -> long
                return value as T
            }
        }

        Byte::class.java -> {
            if (value is Long) {
                return safeCastToByteOrThrow(value) as T
            }
        }

        Short::class.java -> {
            if (value is Long) {
                return safeCastToShortOrThrow(value) as T
            }
        }

        java.lang.Integer::class.java,
        Int::class.java -> {
            when (value) {
                is Long -> {
                    // Long -> int
                    return safeCastToIntOrThrow(value) as T
                }

                is Int -> {
                    // Int -> int
                    return value as T
                }

                is Double -> {
                    // Double -> int
                    return value.toInt() as T
                }
            }
        }

        java.lang.Float::class.java,
        Float::class.java -> {
            when (value) {
                is Double -> {
                    // Double -> float
                    return safeCastToFloatOrThrow(value) as T
                }

                is Float -> {
                    // Float -> float
                    return value as T
                }

                is Long -> {
                    // Long -> float
                    return value.toFloat() as T
                }
            }
        }

        java.lang.Double::class.java,
        Double::class.java -> {
            if (value is Double) {
                // Double -> double
                return value as T
            } else if (value is Long) {
                // Long -> double
                return value.toDouble() as T
            }
        }
    }
    return fallback(value)
}
