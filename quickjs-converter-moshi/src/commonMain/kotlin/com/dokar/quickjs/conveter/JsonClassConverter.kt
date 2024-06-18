package com.dokar.quickjs.conveter

import com.dokar.quickjs.binding.JsObject
import com.dokar.quickjs.binding.toJsObject
import com.dokar.quickjs.converter.JsObjectConverter
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.adapter
import java.lang.reflect.Type
import kotlin.reflect.KType
import kotlin.reflect.typeOf

/**
 * Returns a [JsObjectConverter] for class [T].
 *
 * Required [T] to be annotated with moshi's @[JsonClass].
 */
@OptIn(ExperimentalStdlibApi::class)
@Suppress("FunctionName")
inline fun <reified T : Any?> JsonClassConverter(
    moshi: Moshi = Moshi.Builder().build()
): JsObjectConverter<T> {
    return newConverter(adapter = moshi.adapter())
}

/**
 * Returns a [JsObjectConverter] for class [T].
 *
 * Required [T] to be annotated with moshi's @[JsonClass].
 */
@Suppress("FunctionName")
inline fun <reified T : Any?> JsonClassConverter(
    type: Type,
    moshi: Moshi = Moshi.Builder().build()
): JsObjectConverter<T> {
    return newConverter(adapter = moshi.adapter(type))
}

@Suppress("UNCHECKED_CAST")
@PublishedApi
internal inline fun <reified T : Any?> newConverter(adapter: JsonAdapter<T>): JsObjectConverter<T> {
    return object : JsObjectConverter<T> {
        override val targetType: KType = typeOf<T>()

        override fun convertToTarget(value: JsObject): T {
            return adapter.fromJsonValue(value)
                ?: throw IllegalStateException("Cannot convert js object to type '${T::class}'")
        }

        override fun convertToSource(value: T): JsObject {
            return (adapter.toJsonValue(value) as Map<String, Any?>).toJsObject()
        }
    }
}