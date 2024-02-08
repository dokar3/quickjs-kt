package com.dokar.quickjs.binding

import com.dokar.quickjs.QuickJs
import com.dokar.quickjs.qjsError

internal class DslObjectBinding(
    private val scope: ObjectBindingScopeImpl,
    private val quickJs: QuickJs,
) : ObjectBinding {
    private val propertiesDef = scope.properties.associateBy { it.name }
    private val functionsDef = scope.functions.associateBy { it.name }

    override val properties: List<JsProperty> = scope.properties.toJsProperties()

    override val functions: List<JsFunction> = scope.functions.toJsFunctions()

    override fun getter(name: String): Any? {
        val prop = propertiesDef[name]
            ?: qjsError("Property '$name' not found on object '${scope.name}'")
        val propGetter = prop.getter ?: qjsError("The getter of property '$name' is null")
        return propGetter()
    }

    override fun setter(name: String, value: Any?) {
        val prop = propertiesDef[name]
            ?: qjsError("Property '$name' not found on object '${scope.name}'")
        val propSetter = prop.setter ?: qjsError("The setter of property '$name' is null")
        propSetter(value)
    }

    override fun invoke(name: String, args: Array<Any?>): Any? {
        val func = functionsDef[name]
            ?: qjsError("Function '$name' not found on object '${scope.name}'")
        return when (val call = func.call) {
            is AsyncFunctionBinding<*> -> quickJs.invokeAsyncFunction(args) { call.invoke(it) }
            is FunctionBinding<*> -> call.invoke(args)
            is ObjectBinding -> qjsError("Object cannot be invoked!")
        }
    }
}

private fun List<DslProperty<*>>.toJsProperties(): List<JsProperty> = map {
    JsProperty(
        name = it.name,
        configurable = it.configurable,
        writable = (it.writable ?: it.setter) != null,
        enumerable = it.enumerable,
    )
}

private fun List<DslFunction>.toJsFunctions(): List<JsFunction> = map {
    JsFunction(
        name = it.name,
        isAsync = it.call is AsyncFunctionBinding<*>,
    )
}

internal class ObjectBindingScopeImpl(
    val name: String,
) : ObjectBindingScope {
    val properties = mutableListOf<DslProperty<*>>()

    val functions = mutableListOf<DslFunction>()

    val subScopes = mutableListOf<ObjectBindingScopeImpl>()

    override fun define(name: String, block: ObjectBindingScope.() -> Unit) {
        subScopes.add(ObjectBindingScopeImpl(name = name).also(block))
    }

    override fun <T> property(name: String, block: PropertyScope<T>.() -> Unit) {
        val prop = DslProperty<T>(name = name).also(block)
        if (prop.getter == null) {
            qjsError("property($name) requires a getter {}.")
        }
        properties.add(prop)
    }

    override fun <R> function(name: String, block: FunctionBinding<R>) {
        functions.add(DslFunction(name = name, call = block))
    }

    override fun <R> asyncFunction(name: String, block: AsyncFunctionBinding<R>) {
        functions.add(DslFunction(name = name, call = block))
    }
}

internal class DslProperty<T>(
    val name: String,
    override var configurable: Boolean = true,
    override var writable: Boolean? = null,
    override var enumerable: Boolean = true,
) : PropertyScope<T> {
    var getter: (() -> Any?)? = null
    var setter: ((Any?) -> Unit)? = null

    override fun getter(block: () -> T) {
        this.getter = { block() as Any }
    }

    @Suppress("unchecked_cast")
    override fun setter(block: (value: T) -> Unit) {
        this.setter = { block(it as T) }
    }
}

internal class DslFunction(
    val name: String,
    val call: Binding,
)
