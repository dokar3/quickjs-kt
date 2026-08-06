package com.dokar.quickjs

@RequiresOptIn(
    message = "This API is experimental. It can be changed or removed in future.",
    level = RequiresOptIn.Level.WARNING,
)
@Retention(AnnotationRetention.BINARY)
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
annotation class ExperimentalQuickJsApi

/**
 * A scoped view of the native QuickJS runtime and context.
 *
 * The platform source set exposes the native representation of this type. It
 * must not be retained or used after the [QuickJs.withNativeContext] callback
 * returns, and becomes invalid when the owning [QuickJs] is closed.
 */
@ExperimentalQuickJsApi
expect class QuickJsNativeContext internal constructor(
    contextAddress: Long,
    runtimeAddress: Long,
)
