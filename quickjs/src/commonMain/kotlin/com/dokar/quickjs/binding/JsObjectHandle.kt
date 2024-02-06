package com.dokar.quickjs.binding

@JvmInline
value class JsObjectHandle(
    val nativeHandle: Long,
) {
    companion object {
        val globalThis = JsObjectHandle(-1L)
    }
}