package com.dokar.quickjs.converter

import com.dokar.quickjs.QuickJs
import kotlin.reflect.KClass

/**
 * The type converter for custom classes used by [QuickJs]'s functions.
 */
interface TypeConverter<Source : Any?, Target : Any?> {
    /**
     * The source type class.
     */
    val sourceType: KClass<*>

    /**
     * The target type class.
     */
    val targetType: KClass<*>

    /**
     * Convert a value of type [Source] to type [Target].
     */
    fun convertToTarget(value: Source): Target

    /**
     * Convert a value of type [Target] back to type [Source].
     */
    fun convertToSource(value: Target): Source? = null
}
