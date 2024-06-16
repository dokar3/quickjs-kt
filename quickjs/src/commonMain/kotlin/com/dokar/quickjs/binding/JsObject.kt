package com.dokar.quickjs.binding

/**
 * Create a delegate and mark the map as a JavaScript object.
 */
fun Map<String, Any?>.toJsObject(): JsObject = JsObject(this)

/**
 * The delegate of a property [Map] represents a JavaScript object.
 */
class JsObject(private val map: Map<String, Any?>) : Map<String, Any?> by map {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is JsObject) return false

        if (map != other.map) return false

        return true
    }

    override fun hashCode(): Int {
        return map.hashCode()
    }
}
