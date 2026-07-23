package com.dokar.quickjs.internal

import com.dokar.quickjs.QuickJs

/**
 * Runs [block] while marking the current thread as executing a synchronous binding callback for
 * [quickJs].
 *
 * QuickJS invokes synchronous Kotlin bindings while the runtime's JavaScript lock is already held.
 * The marker allows public APIs called by that binding to reuse the existing lock ownership instead
 * of trying to acquire the non-reentrant lock again. Implementations must retain every active marker
 * in a per-thread stack so a callback nested through another runtime can still access its ancestor
 * runtime safely.
 */
internal expect fun <T> withBindingCallback(quickJs: QuickJs, block: () -> T): T

/**
 * Returns whether the current thread is executing a synchronous binding callback for [quickJs].
 *
 * The check is instance-specific and considers the entire callback stack, not only its innermost
 * entry. A callback may bypass lock acquisition only for a runtime with an active callback frame.
 * The result is also used to reject closing such a runtime while QuickJS still has callback frames
 * on the native stack.
 */
internal expect fun isInBindingCallback(quickJs: QuickJs): Boolean
