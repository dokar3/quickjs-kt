package com.dokar.quickjs.binding

/**
 * The base binding type.
 */
sealed interface Binding

/**
 * The JavaScript object binding. Properties and functions can be defined on the object.
 */
interface ObjectBinding : Binding {
    val properties: List<JsProperty>

    val functions: List<JsFunction>

    fun getter(name: String): Any?

    fun setter(name: String, value: Any?)

    fun invoke(name: String, args: Array<Any?>): Any?
}

/**
 * The JavaScript function binding.
 */
fun interface FunctionBinding<R> : Binding {
    fun invoke(args: Array<Any?>): R
}

/**
 * The JavaScript async function binding. This provides a suspend callback.
 */
fun interface AsyncFunctionBinding<R> : Binding {
    suspend fun invoke(args: Array<Any?>): R
}
