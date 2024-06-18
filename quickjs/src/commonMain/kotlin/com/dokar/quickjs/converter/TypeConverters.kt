package com.dokar.quickjs.converter

import com.dokar.quickjs.qjsError
import kotlin.reflect.KClass
import kotlin.reflect.KType

@PublishedApi
internal class TypeConverters {
    private val converters = mutableListOf<TypeConverter<*, *>>()
    private val classTypeMap = mutableMapOf<KClass<*>, KType>()

    fun addConverters(vararg serializers: TypeConverter<*, *>) {
        this.converters.addAll(serializers)
        for (converter in converters) {
            classTypeMap[converter.sourceType.classifier as KClass<*>] = converter.sourceType
            classTypeMap[converter.targetType.classifier as KClass<*>] = converter.targetType
        }
    }

    fun typeOfClass(cls: KClass<*>): KType? {
        return classTypeMap[cls]
    }

    @Suppress("UNCHECKED_CAST")
    fun <S : Any?, T : Any?> convert(source: S, sourceType: KType, targetType: KType): T {
        if (sourceType == targetType) {
            return source as T
        }
        val converter = converters
            .find {
                it.sourceType.equalsWithoutNullable(sourceType) &&
                        it.targetType.equalsWithoutNullable(targetType)
            }
                as TypeConverter<S, T>?
        if (converter != null) {
            return converter.convertToTarget(source)
        }
        // Try convertToSource()
        val backConverter = converters
            .find {
                it.sourceType.equalsWithoutNullable(targetType) &&
                        it.targetType.equalsWithoutNullable(sourceType)
            }
                as TypeConverter<T, S>?
        if (backConverter != null) {
            val result = backConverter.convertToSource(source)
            if (result != null) {
                return result
            }
        }
        qjsError("No such type converter to convert '$sourceType' to '$targetType'")
    }

    private fun KType.equalsWithoutNullable(other: KType): Boolean {
        return this.classifier == other.classifier && this.arguments == other.arguments
    }
}
