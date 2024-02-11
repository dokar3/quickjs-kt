package com.dokar.quickjs.binding

import kotlin.jvm.JvmInline

/**
 * The handle to a defined JavaScript object, can be used to define nested bindings.
 */
@JvmInline
value class JsObjectHandle(
    val nativeHandle: Long,
) {
    companion object {
        val globalThis = JsObjectHandle(globalThisNativeHandle)
    }
}

internal expect val globalThisNativeHandle: Long