package com.dokar.quickjs.binding

import com.dokar.quickjs.QuickJs

/**
 * Define an object and attach to 'globalThis'.
 */
fun QuickJs.define(
    name: String,
    block: ObjectBindingScope.() -> Unit,
) {
    val scope = ObjectBindingScopeImpl(typeConverters = typeConverters, name = name)
    scope.block()
    define(scope, parent = JsObjectHandle.globalThis)
}

/**
 * Define a function and attach to 'globalThis'.
 */
fun <R> QuickJs.function(name: String, block: FunctionBinding<R>) {
    defineBinding(name = name, binding = block)
}

/**
 * Define an `async` function and attach to 'globalThis'.
 */
fun <R> QuickJs.asyncFunction(name: String, block: AsyncFunctionBinding<R>) {
    defineBinding(name = name, binding = block)
}

/**
 * The binding scope.
 */
interface ObjectBindingScope {
    /**
     * Define an object on parent.
     */
    fun define(name: String, block: ObjectBindingScope.() -> Unit)

    /**
     * Define a property on parent.
     */
    fun <T> property(name: String, block: PropertyScope<T>.() -> Unit)

    /**
     * Define a function on parent.
     */
    fun <R> function(name: String, block: FunctionBinding<R>)

    /**
     * Define an `async` function on parent.
     */
    fun <R> asyncFunction(name: String, block: AsyncFunctionBinding<R>)
}

interface PropertyScope<T> {
    /**
     * The 'configurable' descriptor.
     */
    var configurable: Boolean

    /**
     * The 'writable' descriptor. If null it will be decided by whether the setter is set or not.
     */
    var writable: Boolean?

    /**
     * The 'enumerable' descriptor.
     */
    var enumerable: Boolean

    /**
     * Define the getter of the property.
     */
    fun getter(block: () -> T)

    /**
     * Define the getter of the property. Optional if there is no write on this property.
     */
    fun setter(block: (value: T) -> Unit)
}

private fun QuickJs.define(
    scope: ObjectBindingScopeImpl,
    parent: JsObjectHandle,
): JsObjectHandle {
    val binding = DslObjectBinding(scope, this)

    val handle = defineBinding(
        name = scope.name,
        binding = binding,
        parent = parent,
    )

    for (sub in scope.subScopes) {
        define(scope = sub, parent = handle)
    }

    return handle
}
