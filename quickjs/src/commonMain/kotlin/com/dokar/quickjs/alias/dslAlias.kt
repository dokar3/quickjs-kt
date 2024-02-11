package com.dokar.quickjs.alias

import com.dokar.quickjs.ExperimentalQuickJsApi
import com.dokar.quickjs.QuickJs
import com.dokar.quickjs.QuickJsException
import com.dokar.quickjs.binding.AsyncFunctionBinding
import com.dokar.quickjs.binding.FunctionBinding
import com.dokar.quickjs.binding.ObjectBindingScope
import com.dokar.quickjs.binding.PropertyScope
import com.dokar.quickjs.binding.asyncFunction
import com.dokar.quickjs.binding.define
import com.dokar.quickjs.binding.function
import kotlin.coroutines.cancellation.CancellationException

/**
 * Alias for [QuickJs.define].
 */
@ExperimentalQuickJsApi
fun QuickJs.def(
    name: String,
    block: ObjectBindingScope.() -> Unit,
) {
    define(name = name, block = block)
}

/**
 * Alias for [QuickJs.function].
 */
@ExperimentalQuickJsApi
fun <R> QuickJs.func(name: String, block: FunctionBinding<R>) {
    function(name = name, block = block)
}

/**
 * Alias for [QuickJs.asyncFunction].
 */
@ExperimentalQuickJsApi
fun <R> QuickJs.asyncFunc(name: String, block: AsyncFunctionBinding<R>) {
    asyncFunction(name = name, block = block)
}

/**
 * Alias for [QuickJs.evaluate].
 */
@ExperimentalQuickJsApi
@Throws(QuickJsException::class, CancellationException::class)
suspend inline fun <reified T> QuickJs.eval(
    code: String,
    filename: String = "main.js",
    asModule: Boolean = false,
): T {
    return evaluate(code = code, filename = filename, asModule = asModule)
}

/**
 * Alias for [QuickJs.evaluate].
 */
@ExperimentalQuickJsApi
@Throws(QuickJsException::class, CancellationException::class)
suspend inline fun <reified T> QuickJs.eval(bytecode: ByteArray): T {
    return evaluate(bytecode = bytecode)
}

/**
 * Alias for [ObjectBindingScope.define].
 */
@ExperimentalQuickJsApi
fun ObjectBindingScope.def(name: String, block: ObjectBindingScope.() -> Unit) {
    define(name = name, block = block)
}

/**
 * Alias for [ObjectBindingScope.property].
 */
@ExperimentalQuickJsApi
fun <T> ObjectBindingScope.prop(name: String, block: PropertyScope<T>.() -> Unit) {
    property(name = name, block = block)
}

/**
 * Alias for [ObjectBindingScope.function].
 */
@ExperimentalQuickJsApi
fun <R> ObjectBindingScope.func(name: String, block: FunctionBinding<R>) {
    function(name = name, block = block)
}

/**
 * Alias for [ObjectBindingScope.asyncFunction].
 */
@ExperimentalQuickJsApi
fun <R> ObjectBindingScope.asyncFunc(name: String, block: AsyncFunctionBinding<R>) {
    asyncFunction(name = name, block = block)
}

