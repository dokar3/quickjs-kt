package com.dokar.quickjs.binding

import com.dokar.quickjs.QuickJs
import com.dokar.quickjs.QuickJsException
import com.dokar.quickjs.jsAutoCastOrThrow
import java.lang.reflect.Field
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Modifier
import kotlin.reflect.KFunction

/**
 * Define a binding for an instance.
 * Reflection will be used to read fields and call methods, property setters are not supported.
 */
inline fun <reified T> QuickJs.define(
    name: String,
    instance: Any,
    parent: JsObjectHandle = JsObjectHandle.globalThis,
) {
    define(
        name = name,
        type = T::class.java,
        instance = instance,
        parent = parent,
    )
}

/**
 * Define a binding for an instance.
 * Reflection will be used to read fields and call methods, property setters are not supported.
 */
fun <T> QuickJs.define(
    name: String,
    type: Class<T>,
    instance: Any,
    parent: JsObjectHandle = JsObjectHandle.globalThis,
) {
    val fields = type.declaredFields
        .filter { Modifier.isPublic(it.modifiers) }
        .associateBy {
            it.isAccessible = true
            it.name
        }
    val jsFields = fields.values.map(Field::toJsProperty)
    val methods = type.declaredMethods
        .filter { Modifier.isPublic(it.modifiers) }
        .associateBy {
            it.isAccessible = true
            it.name
        }

    val binding = object : ObjectBinding {
        override val properties: List<JsProperty> = jsFields
        override val functions: List<JsFunction> = methods.values.map {
            // TODO: Support suspend/async functions?
            JsFunction(name = it.name, isAsync = false)
        }

        override fun getter(name: String): Any? {
            val field = fields[name] ?: throw QuickJsException(
                "Field '$name' not found in instance $instance"
            )
            return field.get(instance)
        }

        override fun setter(name: String, value: Any?) {
            throw QuickJsException("Setters are not available on reflection bindings.")
        }

        override fun invoke(name: String, args: Array<Any?>): Any? {
            val method = methods[name] ?: throw QuickJsException(
                "Method '$name' not found in instance $instance"
            )
            if (method.parameterCount != args.size) {
                throw QuickJsException(
                    "Parameter count mismatched on method '$name', " +
                            "expect: ${args.size}, actual: ${method.parameterCount}"
                )
            }
            val parameterTypes = method.parameterTypes
            val parameters = args
                .mapIndexed { index, param ->
                    jsAutoCastOrThrow<Any?>(param, parameterTypes[index])
                }
                .toTypedArray()
            try {
                return method.invoke(instance, *parameters)
            } catch (e: InvocationTargetException) {
                throw e.targetException
            }
        }
    }

    defineBinding(name = name, binding = binding, parent = parent)
}

private fun Field.toJsProperty(): JsProperty = JsProperty(
    name = this.name,
    configurable = true,
    writable = false,
    enumerable = true,
)
