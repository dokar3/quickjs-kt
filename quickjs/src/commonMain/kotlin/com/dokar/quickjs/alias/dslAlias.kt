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
import kotlin.jvm.JvmName

/**
 * Alias for [QuickJs.define].
 */
@ExperimentalQuickJsApi
inline fun QuickJs.def(
    name: String,
    noinline block: ObjectBindingScope.() -> Unit,
) {
    define(name = name, block = block)
}

/**
 * Alias for [QuickJs.function].
 */
@ExperimentalQuickJsApi
inline fun <reified R : Any?> QuickJs.func(
    name: String,
    crossinline block: (args: Array<Any?>) -> R
) {
    function(name = name, block = block)
}

/**
 * Alias for [QuickJs.function].
 */
@ExperimentalQuickJsApi
@JvmName("funcWithTypedArg")
inline fun <reified T : Any?, reified R : Any?> QuickJs.func(
    name: String,
    crossinline block: (T) -> R
) {
    function<T, R>(name = name, block = block)
}

/**
 * Alias for [QuickJs.asyncFunction].
 */
@ExperimentalQuickJsApi
inline fun <reified R : Any?> QuickJs.asyncFunc(
    name: String,
    crossinline block: suspend (args: Array<Any?>) -> R
) {
    asyncFunction(name = name, block = block)
}

/**
 * Alias for [QuickJs.asyncFunction].
 */
@ExperimentalQuickJsApi
@JvmName("asyncFuncWithTypedArg")
inline fun <reified T : Any?, reified R : Any?> QuickJs.asyncFunc(
    name: String,
    crossinline block: suspend (T) -> R
) {
    asyncFunction<T, R>(name = name, block = block)
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
inline fun ObjectBindingScope.def(name: String, noinline block: ObjectBindingScope.() -> Unit) {
    define(name = name, block = block)
}

/**
 * Alias for [ObjectBindingScope.property].
 */
@ExperimentalQuickJsApi
inline fun <T> ObjectBindingScope.prop(name: String, noinline block: PropertyScope<T>.() -> Unit) {
    property(name = name, block = block)
}

/**
 * Alias for [ObjectBindingScope.function].
 */
@ExperimentalQuickJsApi
inline fun <R> ObjectBindingScope.func(name: String, block: FunctionBinding<R>) {
    function(name = name, block = block)
}

/**
 * Alias for [ObjectBindingScope.function].
 */
@ExperimentalQuickJsApi
inline fun <reified T : Any?, reified R : Any?> ObjectBindingScope.func(
    name: String,
    crossinline block: (T) -> R
) {
    function(name = name, block = block)
}

/**
 * Alias for [ObjectBindingScope.asyncFunction].
 */
@ExperimentalQuickJsApi
inline fun <R> ObjectBindingScope.asyncFunc(name: String, block: AsyncFunctionBinding<R>) {
    asyncFunction(name = name, block = block)
}

/**
 * Alias for [ObjectBindingScope.asyncFunction].
 */
@ExperimentalQuickJsApi
inline fun <reified T : Any?, reified R : Any?> ObjectBindingScope.asyncFunc(
    name: String,
    crossinline block: suspend (T) -> R
) {
    asyncFunction(name = name, block = block)
}
