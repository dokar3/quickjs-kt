package com.dokar.quickjs.binding

/**
 * Create a delegate and mark the map as a JavaScript object.
 */
fun Map<String, Any?>.toJsObject(): JsObject = JsObject(this)

/**
 * The delegate of a property [Map] represents a JavaScript object.
 */
class JsObject(map: Map<String, Any?>) : Map<String, Any?> by map
