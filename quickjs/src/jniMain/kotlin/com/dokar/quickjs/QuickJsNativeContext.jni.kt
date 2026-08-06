package com.dokar.quickjs

@ExperimentalQuickJsApi
actual class QuickJsNativeContext internal actual constructor(
    /** Address of the owning QuickJS context, valid for the runtime lifetime. */
    val contextAddress: Long,
    /** Address of the owning QuickJS runtime, valid for the runtime lifetime. */
    val runtimeAddress: Long,
)
