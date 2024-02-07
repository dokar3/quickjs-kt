package com.dokar.quickjs.binding

/**
 * The handle to a defined JavaScript object, can be used to define nested bindings.
 */
@JvmInline
value class JsObjectHandle(
    val nativeHandle: Long,
) {
    companion object {
        val globalThis = JsObjectHandle(-1L)
    }
}