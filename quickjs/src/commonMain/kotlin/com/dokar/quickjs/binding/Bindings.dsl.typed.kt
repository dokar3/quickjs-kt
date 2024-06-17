package com.dokar.quickjs.binding

import com.dokar.quickjs.QuickJs
import com.dokar.quickjs.converter.TypeConverters
import com.dokar.quickjs.qjsError
import kotlin.reflect.KClass
import kotlin.reflect.typeOf

/**
 * Define a function and attach to 'globalThis'.
 *
 * This function requires a typed parameter [T].
 */
inline fun <reified T : Any?, reified R : Any?> QuickJs.function(
    name: String,
    crossinline block: (T) -> R
) {
    defineBinding(
        name = name,
        binding = FunctionBinding { args ->
            callWithTypedArg<T, R>(
                name = name,
                typeConverters = typeConverters,
                args = args,
                block = block,
            )
        },
    )
}

/**
 * Define an `async` function and attach to 'globalThis'.
 *
 * This function requires a typed parameter [T].
 */
inline fun <reified T : Any?, reified R : Any?> QuickJs.asyncFunction(
    name: String,
    crossinline block: suspend (T) -> R
) {
    defineBinding(
        name = name,
        binding = AsyncFunctionBinding { args ->
            callWithTypedArg<T, R>(
                name = name,
                typeConverters = typeConverters,
                args = args
            ) { block(it) }
        },
    )
}

/**
 * Define a function on parent.
 *
 * This function requires a typed parameter [T].
 */
inline fun <reified T : Any?, reified R : Any?> ObjectBindingScope.function(
    name: String,
    crossinline block: (T) -> R
) {
    function(
        name = name,
        block = { args ->
            callWithTypedArg<T, R>(
                name = name,
                // TODO: This is awkward, update it when the context receivers feature is ready.
                typeConverters = (this as ObjectBindingScopeImpl).typeConverters,
                args = args,
                block = block
            )
        },
    )
}

/**
 * Define an `async` function on parent.
 *
 * This function requires a typed parameter [T].
 */
inline fun <reified T : Any?, reified R : Any?> ObjectBindingScope.asyncFunction(
    name: String,
    crossinline block: suspend (T) -> R
) {
    asyncFunction(
        name = name,
        block = { args ->
            callWithTypedArg<T, R>(
                name = name,
                // TODO: This is awkward, update it when the context receivers feature is ready.
                typeConverters = (this as ObjectBindingScopeImpl).typeConverters,
                args = args
            ) { block(it) }
        },
    )
}

@PublishedApi
internal inline fun <reified T : Any?, reified R : Any?> callWithTypedArg(
    name: String,
    typeConverters: TypeConverters,
    args: Array<Any?>,
    block: (T) -> R
): Any? {
    if (args.isEmpty()) {
        qjsError("Function '$name' requires 1 parameter but none was passed.")
    } else if (args.size > 1) {
        qjsError("Function '$name' requires 1 parameter but ${args.size} were passed.")
    }

    val typedArg = args.first()?.let {
        // Get the typed argument
        typeConverters.convert<Any?, T>(
            source = it,
            sourceType = it::class,
            targetType = T::class
        )
    }

    if (typedArg == null && !typeOf<T>().isMarkedNullable) {
        qjsError("Function '$name' requires 1 non-null parameter but null was passed.")
    }

    val result = block(typedArg as T) ?: return null

    if (canConvertInternally(result::class)) {
        return result
    }

    // Convert result to JsObject
    return typeConverters.convert<R, Any?>(
        source = result,
        sourceType = result::class,
        targetType = JsObject::class
    )
}

@OptIn(ExperimentalUnsignedTypes::class)
@PublishedApi
internal fun canConvertInternally(type: KClass<*>): Boolean {
    return type == Unit::class ||
            type == Int::class || type == Long::class ||
            type == Float::class || type == Double::class ||
            type == Boolean::class || type == String::class ||
            type == ByteArray::class || type == UByteArray::class ||
            type == Array::class || type == List::class ||
            type == Set::class || type == Map::class ||
            type == Error::class || type == JsObject::class
}
