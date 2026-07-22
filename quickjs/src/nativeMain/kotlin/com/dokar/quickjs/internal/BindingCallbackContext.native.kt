package com.dokar.quickjs.internal

import com.dokar.quickjs.QuickJs
import kotlin.native.concurrent.ThreadLocal

@ThreadLocal
private var bindingCallbackQuickJs: QuickJs? = null

internal actual fun <T> withBindingCallback(quickJs: QuickJs, block: () -> T): T {
    val previous = bindingCallbackQuickJs
    bindingCallbackQuickJs = quickJs
    return try {
        block()
    } finally {
        bindingCallbackQuickJs = previous
    }
}

internal actual fun isInBindingCallback(quickJs: QuickJs): Boolean =
    bindingCallbackQuickJs === quickJs
