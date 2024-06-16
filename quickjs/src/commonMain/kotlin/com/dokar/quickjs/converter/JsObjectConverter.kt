package com.dokar.quickjs.converter

import com.dokar.quickjs.binding.JsObject
import kotlin.reflect.KClass

/**
 * Converts [JsObject] to the target type [T] and vice versa.
 */
interface JsObjectConverter<T : Any?> : TypeConverter<JsObject, T> {
    override val sourceType: KClass<*> get() = JsObject::class
}