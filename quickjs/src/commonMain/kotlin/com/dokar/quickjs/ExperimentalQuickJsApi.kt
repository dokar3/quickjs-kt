package com.dokar.quickjs

@RequiresOptIn(
    message = "This API is experimental. It can be changed or removed in future.",
    level = RequiresOptIn.Level.WARNING,
)
@Retention(AnnotationRetention.BINARY)
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
annotation class ExperimentalQuickJsApi
