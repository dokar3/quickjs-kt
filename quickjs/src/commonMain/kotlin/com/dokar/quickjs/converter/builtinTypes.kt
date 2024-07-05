package com.dokar.quickjs.converter

import com.dokar.quickjs.binding.JsObject
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.contract
import kotlin.reflect.KClass
import kotlin.reflect.KType
import kotlin.reflect.typeOf

@OptIn(ExperimentalContracts::class)
@PublishedApi
internal fun canConvertReturnInternally(instance: Any?): Boolean {
    contract {
        returns(false) implies (instance != null)
    }
    instance ?: return true
    return typeOfInstance(instance) != null
}

@OptIn(ExperimentalUnsignedTypes::class)
@PublishedApi
internal fun typeOfClass(typeConverters: TypeConverters, cls: KClass<*>): KType {
    return when (cls) {
        Unit::class -> typeOf<Unit>()
        Int::class -> typeOf<Int>()
        Long::class -> typeOf<Long>()
        Float::class -> typeOf<Float>()
        Double::class -> typeOf<Double>()
        Boolean::class -> typeOf<Boolean>()
        String::class -> typeOf<String>()
        ByteArray::class -> typeOf<ByteArray>()
        UByteArray::class -> typeOf<UByteArray>()
        Array::class -> typeOf<Array<*>>()
        List::class -> typeOf<List<*>>()
        Set::class -> typeOf<Set<*>>()
        JsObject::class -> typeOf<JsObject>()
        Map::class -> typeOf<Map<*, *>>()
        Error::class -> typeOf<Error>()
        else -> typeConverters.typeOfClass(cls)
            ?: throw IllegalStateException(
                "Cannot find the kotlin type of class '$cls', " +
                        "did you forget to add a type converter for it?"
            )
    }
}

@PublishedApi
internal fun typeOfInstance(typeConverters: TypeConverters, instance: Any): KType {
    return typeOfInstance(instance)
        ?: typeConverters.typeOfClass(instance::class)
        ?: throw IllegalStateException(
            "Cannot find the kotlin type of object $instance (${instance::class}), " +
                    "did you forget to add a type converter for it?"
        )
}

@OptIn(ExperimentalUnsignedTypes::class)
private fun typeOfInstance(instance: Any): KType? {
    return when (instance) {
        Unit -> typeOf<Unit>()
        is Byte -> typeOf<Byte>()
        is Short -> typeOf<Short>()
        is Int -> typeOf<Int>()
        is Long -> typeOf<Long>()
        is Float -> typeOf<Float>()
        is Double -> typeOf<Double>()
        is Boolean -> typeOf<Boolean>()
        is String -> typeOf<String>()
        is ByteArray -> typeOf<ByteArray>()
        is UByteArray -> typeOf<UByteArray>()
        is Array<*> -> typeOf<Array<*>>()
        is List<*> -> typeOf<List<*>>()
        is Set<*> -> typeOf<Set<*>>()
        is JsObject -> typeOf<JsObject>()
        is Map<*, *> -> typeOf<Map<*, *>>()
        is Error -> typeOf<Error>()
        else -> null
    }
}
