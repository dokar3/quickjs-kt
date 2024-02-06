package com.dokar.quickjs.binding

sealed interface Binding

interface ObjectBinding : Binding {
    val properties: List<JsProperty>

    val functions: List<JsFunction>

    fun getter(name: String): Any?

    fun setter(name: String, value: Any?)

    fun invoke(name: String, args: Array<Any?>): Any?
}

fun interface FunctionBinding<R> : Binding {
    fun invoke(args: Array<Any?>): R
}

fun interface AsyncFunctionBinding<R> : Binding {
    suspend fun invoke(args: Array<Any?>): R
}
