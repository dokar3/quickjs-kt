package com.dokar.quickjs.converter

import com.dokar.quickjs.binding.JsObject
import kotlin.reflect.KType
import kotlin.reflect.typeOf

/**
 * Converts [JsObject] to the target type [T] and vice versa.
 */
interface JsObjectConverter<T : Any?> : TypeConverter<JsObject, T> {
    override val sourceType: KType get() = typeOf<JsObject>()
}