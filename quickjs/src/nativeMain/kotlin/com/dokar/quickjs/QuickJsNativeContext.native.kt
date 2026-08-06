package com.dokar.quickjs

import kotlinx.cinterop.CPointer
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.toCPointer
import quickjs.JSContext
import quickjs.JSRuntime

@OptIn(ExperimentalForeignApi::class)
@ExperimentalQuickJsApi
actual class QuickJsNativeContext internal actual constructor(
    contextAddress: Long,
    runtimeAddress: Long,
) {
    /** Pointer to the owning QuickJS context. */
    val context: CPointer<JSContext> = contextAddress.toCPointer<JSContext>()!!
    /** Pointer to the owning QuickJS runtime. */
    val runtime: CPointer<JSRuntime> = runtimeAddress.toCPointer<JSRuntime>()!!
}
