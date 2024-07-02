package com.dokar.quickjs.binding

import com.dokar.quickjs.QuickJs
import com.dokar.quickjs.converter.TypeConverters
import com.dokar.quickjs.converter.canConvertReturnInternally
import com.dokar.quickjs.converter.castValueOr
import com.dokar.quickjs.converter.typeOfInstance
import com.dokar.quickjs.qjsError
import kotlin.reflect.KType
import kotlin.reflect.typeOf

/**
 * Define a function and attach to 'globalThis'.
 *
 * This function requires a typed parameter [T].
 */
inline fun <reified T : Any?, R : Any?> QuickJs.function(
    name: String,
    crossinline block: (T) -> R
) {
    defineBinding(
        name = name,
        binding = FunctionBinding { args ->
            callNormalWithTypedArg<T, R>(
                name = name,
                typeConverters = typeConverters,
                argType = typeOf<T>(),
                args = args,
                block = { block(it) },
            )
        },
    )
}

/**
 * Define an `async` function and attach to 'globalThis'.
 *
 * This function requires a typed parameter [T].
 */
inline fun <reified T : Any?, R : Any?> QuickJs.asyncFunction(
    name: String,
    crossinline block: suspend (T) -> R
) {
    defineBinding(
        name = name,
        binding = AsyncFunctionBinding { args ->
            callSuspendWithTypedArg<T, R>(
                name = name,
                typeConverters = typeConverters,
                argType = typeOf<T>(),
                args = args,
                block = { block(it) },
            )
        },
    )
}

/**
 * Define a function on parent.
 *
 * This function requires a typed parameter [T].
 */
inline fun <reified T : Any?, R : Any?> ObjectBindingScope.function(
    name: String,
    crossinline block: (T) -> R
) {
    function(
        name = name,
        block = { args ->
            callNormalWithTypedArg<T, R>(
                name = name,
                // TODO: This is awkward, update it when the context receivers feature is ready.
                typeConverters = (this as ObjectBindingScopeImpl).typeConverters,
                args = args,
                argType = typeOf<T>(),
                block = { block(it) },
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
            callSuspendWithTypedArg<T, R>(
                name = name,
                // TODO: This is awkward, update it when the context receivers feature is ready.
                typeConverters = (this as ObjectBindingScopeImpl).typeConverters,
                argType = typeOf<T>(),
                args = args,
                block = { block(it) },
            )
        },
    )
}

@PublishedApi
internal fun <T : Any?, R : Any?> callNormalWithTypedArg(
    name: String,
    typeConverters: TypeConverters,
    args: Array<Any?>,
    argType: KType,
    block: (T) -> R
): Any? {
    return callWithTypedArg(
        name = name,
        typeConverters = typeConverters,
        args = args,
        argType = argType,
        block
    )
}

@PublishedApi
internal suspend fun <T : Any?, R : Any?> callSuspendWithTypedArg(
    name: String,
    typeConverters: TypeConverters,
    args: Array<Any?>,
    argType: KType,
    block: suspend (T) -> R
): Any? {
    return callWithTypedArg<T, R>(
        name = name,
        typeConverters = typeConverters,
        args = args,
        argType = argType,
        block = { block(it) }
    )
}

private inline fun <T : Any?, R : Any?> callWithTypedArg(
    name: String,
    typeConverters: TypeConverters,
    args: Array<Any?>,
    argType: KType,
    block: (T) -> R
): Any? {
    if (args.isEmpty()) {
        qjsError("Function '$name' requires 1 parameter but none was passed.")
    } else if (args.size > 1) {
        qjsError("Function '$name' requires 1 parameter but ${args.size} were passed.")
    }

    val typedArg = args.first()?.let {
        // Get the typed argument
        castValueOr(
            value = it,
            expectedType = argType,
            fallback = { value ->
                typeConverters.convert<Any?, T>(
                    source = value,
                    sourceType = typeOfInstance(typeConverters, it),
                    targetType = argType,
                )
            },
        )
    }

    if (typedArg == null && !argType.isMarkedNullable) {
        qjsError("Function '$name' requires 1 non-null parameter but null was passed.")
    }

    @Suppress("UNCHECKED_CAST")
    val result = block(typedArg as T)

    if (canConvertReturnInternally(result)) {
        return result
    }

    // Convert result to JsObject
    return typeConverters.convert<R, Any?>(
        source = result,
        sourceType = typeOfInstance(typeConverters, result),
        targetType = typeOf<JsObject>(),
    )
}
