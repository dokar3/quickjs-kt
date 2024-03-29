package com.dokar.quickjs

import kotlin.reflect.KClass

@Suppress("unchecked_cast")
@PublishedApi
internal fun <T : Any?> jsAutoCastOrThrow(value: Any?, expectedType: KClass<*>): T {
    val nonNull = value ?: return null as T
    if (expectedType.isInstance(nonNull)) {
        return nonNull as T
    }
    val paramType = nonNull::class
    when (expectedType) {
        Int::class -> {
            when (value) {
                is Long -> {
                    // Long -> int
                    return value.toInt() as T
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
                    return value.toFloat() as T
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
    qjsError("Type mismatch: expected ${expectedType}, found ${paramType.simpleName}")
}
