package com.dokar.quickjs.internal

import com.dokar.quickjs.QuickJs

private val bindingCallbackStack = ThreadLocal<ArrayDeque<QuickJs>?>()

internal actual fun <T> withBindingCallback(quickJs: QuickJs, block: () -> T): T {
    val stack = bindingCallbackStack.get() ?: ArrayDeque<QuickJs>().also {
        bindingCallbackStack.set(it)
    }
    stack.addLast(quickJs)
    return try {
        block()
    } finally {
        stack.removeLast()
        if (stack.isEmpty()) bindingCallbackStack.remove()
    }
}

internal actual fun isInBindingCallback(quickJs: QuickJs): Boolean =
    bindingCallbackStack.get()?.any { it === quickJs } == true
