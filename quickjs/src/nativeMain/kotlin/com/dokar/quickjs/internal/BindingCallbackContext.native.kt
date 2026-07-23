package com.dokar.quickjs.internal

import com.dokar.quickjs.QuickJs
import kotlin.native.concurrent.ThreadLocal

@ThreadLocal
private var bindingCallbackStack: ArrayDeque<QuickJs>? = null

internal actual fun <T> withBindingCallback(quickJs: QuickJs, block: () -> T): T {
    val stack = bindingCallbackStack ?: ArrayDeque<QuickJs>().also {
        bindingCallbackStack = it
    }
    stack.addLast(quickJs)
    return try {
        block()
    } finally {
        stack.removeLast()
        if (stack.isEmpty()) bindingCallbackStack = null
    }
}

internal actual fun isInBindingCallback(quickJs: QuickJs): Boolean =
    bindingCallbackStack?.any { it === quickJs } == true
