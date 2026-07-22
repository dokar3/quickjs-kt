package com.dokar.quickjs.internal

import com.dokar.quickjs.QuickJs

private val bindingCallbackQuickJs = ThreadLocal<QuickJs?>()

internal actual fun <T> withBindingCallback(quickJs: QuickJs, block: () -> T): T {
    val previous = bindingCallbackQuickJs.get()
    bindingCallbackQuickJs.set(quickJs)
    return try {
        block()
    } finally {
        bindingCallbackQuickJs.set(previous)
    }
}

internal actual fun isInBindingCallback(quickJs: QuickJs): Boolean =
    bindingCallbackQuickJs.get() === quickJs
