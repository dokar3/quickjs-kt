package com.dokar.quickjs.converter

import com.dokar.quickjs.qjsError
import kotlin.reflect.KClass

@PublishedApi
internal class TypeConverters {
    private val serializers = mutableListOf<TypeConverter<*, *>>()

    fun addConverters(vararg serializers: TypeConverter<*, *>) {
        this.serializers.addAll(serializers)
    }

    @Suppress("UNCHECKED_CAST")
    fun <S : Any?, T : Any?> convert(source: S, sourceType: KClass<*>, targetType: KClass<*>): T {
        if (sourceType == targetType) {
            return source as T
        }
        val converter = serializers
            .find { it.sourceType.isInstance(source) && it.targetType == targetType }
                as TypeConverter<S, T>?
        if (converter != null) {
            return converter.convertToTarget(source)
        }
        // Try convertBack()
        val backConverter = serializers
            .find { it.sourceType == targetType && it.targetType.isInstance(source) }
                as TypeConverter<T, S>?
        if (backConverter != null) {
            val result = backConverter.convertToSource(source)
            if (result != null) {
                return result
            }
        }
        qjsError("No such type converter to convert '$sourceType' to '$targetType'")
    }
}
