package com.dokar.quickjs.conveter

import com.dokar.quickjs.binding.JsObject
import com.dokar.quickjs.binding.toJsObject
import com.dokar.quickjs.converter.JsObjectConverter
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.adapter
import kotlin.reflect.KClass

/**
 * Returns a [JsObjectConverter] for class [T].
 *
 * Required [T] to be annotated with moshi's @[JsonClass].
 */
@OptIn(ExperimentalStdlibApi::class)
@Suppress("FunctionName", "UNCHECKED_CAST")
inline fun <reified T : Any> JsonClassConverter(
    moshi: Moshi = Moshi.Builder().build()
): JsObjectConverter<T> {
    val adapter = moshi.adapter<T>()
    return object : JsObjectConverter<T> {
        override val targetType: KClass<*> = T::class

        override fun convertToTarget(value: JsObject): T {
            return adapter.fromJsonValue(value)
                ?: throw IllegalStateException("Cannot convert js object to type '${T::class}'")
        }

        override fun convertToSource(value: T): JsObject {
            return (adapter.toJsonValue(value) as Map<String, Any?>).toJsObject()
        }
    }
}
