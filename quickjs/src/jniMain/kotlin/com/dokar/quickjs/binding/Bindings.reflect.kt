package com.dokar.quickjs.binding

import com.dokar.quickjs.QuickJs
import com.dokar.quickjs.QuickJsException
import com.dokar.quickjs.converter.castValueOr
import com.dokar.quickjs.converter.typeOfClass
import com.dokar.quickjs.converter.typeOfInstance
import com.dokar.quickjs.qjsError
import kotlinx.coroutines.suspendCancellableCoroutine
import java.lang.reflect.Field
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import kotlin.coroutines.Continuation
import kotlin.coroutines.intrinsics.COROUTINE_SUSPENDED
import kotlin.coroutines.resume

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
            JsFunction(name = it.name, isAsync = it.canBeCalledAsSuspend())
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
            return if (method.canBeCalledAsSuspend()) {
                invokeSuspend(method, args)
            } else {
                invokeNormal(method, args)
            }
        }

        private fun invokeNormal(method: Method, args: Array<Any?>): Any? {
            if (method.parameterCount != args.size) {
                qjsError(
                    "Parameter count mismatched on method '$name', " +
                            "js: ${args.size}, java: ${method.parameterCount}"
                )
            }
            val parameterTypes = method.parameterTypes
            val parameters = args
                .mapIndexed { index, param ->
                    val targetType = typeOfClass(typeConverters, parameterTypes[index].kotlin)
                    castValueOr<Any?>(param, targetType) {
                        typeConverters.convert(
                            source = it,
                            sourceType = typeOfInstance(typeConverters, it),
                            targetType = targetType
                        )
                    }
                }
                .toTypedArray()
            try {
                return method.invoke(instance, *parameters)
            } catch (e: InvocationTargetException) {
                throw e.targetException
            }
        }

        private fun invokeSuspend(method: Method, args: Array<Any?>): Any? {
            if (args.size < 2) {
                qjsError(
                    "Unexpected internal parameter count: ${args.size}, no promise " +
                            "handles are provided."
                )
            }
            val funcArgs = args.slice(2..args.lastIndex)
            if (method.parameterCount != funcArgs.size + 1) {
                qjsError(
                    "Parameter count mismatched on method '$name', " +
                            "js: ${funcArgs.size}, java: ${method.parameterCount - 1}"
                )
            }
            val end = method.parameterTypes.lastIndex - 1
            val parameterTypes = method.parameterTypes.slice(0..end)
            val parameters = funcArgs
                .mapIndexed { index, param ->
                    val targetType = typeOfClass(typeConverters, parameterTypes[index].kotlin)
                    castValueOr<Any?>(param, targetType) {
                        typeConverters.convert(
                            source = it,
                            sourceType = typeOfInstance(typeConverters, it),
                            targetType = targetType
                        )
                    }
                }
                .toTypedArray()
            try {
                invokeAsyncFunction(args) {
                    suspendCancellableCoroutine { continuation ->
                        val ret = method.invoke(instance, *parameters, continuation)
                        if (ret != COROUTINE_SUSPENDED) {
                            continuation.resume(ret)
                        }
                    }
                }
                return null
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

internal fun Method.canBeCalledAsSuspend(): Boolean {
    val lastArg = this.parameterTypes.lastOrNull() ?: return false
    return Continuation::class.java.isAssignableFrom(lastArg) &&
            this.returnType == Any::class.java
}
