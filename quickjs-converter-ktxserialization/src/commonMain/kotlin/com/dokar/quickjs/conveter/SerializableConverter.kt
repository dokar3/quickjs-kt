package com.dokar.quickjs.conveter

import com.dokar.quickjs.binding.JsObject
import com.dokar.quickjs.binding.toJsObject
import com.dokar.quickjs.converter.JsObjectConverter
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.properties.Properties
import kotlinx.serialization.properties.decodeFromMap
import kotlinx.serialization.properties.encodeToMap
import kotlin.reflect.KClass

/**
 * Returns a [JsObjectConverter] for class [T].
 *
 * Required [T] to be annotated with @[Serializable].
 */
@OptIn(ExperimentalSerializationApi::class)
@Suppress("FunctionName")
inline fun <reified T : Any> SerializableConverter(
    properties: Properties = Properties
): JsObjectConverter<T> {
    return object : JsObjectConverter<T> {
        override val targetType: KClass<*> = T::class

        override fun convertToTarget(value: JsObject): T {
            val map = value.filterValues { it != null }.mapValues { it.value!! }
            return properties.decodeFromMap<T>(map)
        }

        override fun convertToSource(value: T): JsObject {
            return properties.encodeToMap(value).toJsObject()
        }
    }
}
