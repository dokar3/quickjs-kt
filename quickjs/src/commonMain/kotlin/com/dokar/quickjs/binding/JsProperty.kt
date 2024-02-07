package com.dokar.quickjs.binding

/**
 * Properties of a JavaScript property.
 */
class JsProperty(
    val name: String,
    val configurable: Boolean,
    val writable: Boolean,
    val enumerable: Boolean,
)