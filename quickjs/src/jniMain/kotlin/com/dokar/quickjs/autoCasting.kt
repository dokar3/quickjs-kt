package com.dokar.quickjs

@Suppress("unchecked_cast")
@PublishedApi
internal fun <T : Any?> jsAutoCastOrThrow(value: Any?, expectedType: Class<*>): T {
    val nonNull = value ?: return null as T
    if (expectedType.isInstance(nonNull)) {
        return nonNull as T
    }
    val paramType = nonNull::class.java
    when (expectedType) {
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

        java.lang.Integer::class.java,
        Int::class.java -> {
            when (value) {
                is Long -> {
                    // Long -> int
                    return value.toInt() as T
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
                    return value.toFloat() as T
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
    qjsError("Type mismatch: expected ${expectedType}, found ${paramType.simpleName}")
}
