package com.dokar.quickjs.internal

import com.dokar.quickjs.QuickJs

/**
 * Runs [block] while marking the current thread as executing a synchronous binding callback for
 * [quickJs].
 *
 * QuickJS invokes synchronous Kotlin bindings while the runtime's JavaScript lock is already held.
 * The marker allows public APIs called by that binding to reuse the existing lock ownership instead
 * of trying to acquire the non-reentrant lock again. The previous marker must be restored so nested
 * callbacks, including callbacks involving another runtime, retain the correct ownership context.
 */
internal expect fun <T> withBindingCallback(quickJs: QuickJs, block: () -> T): T

/**
 * Returns whether the current thread is executing a synchronous binding callback for [quickJs].
 *
 * The check is instance-specific: a callback may bypass lock acquisition only for the runtime that
 * invoked it. It is also used to reject closing that runtime while QuickJS still has callback frames
 * on the native stack.
 */
internal expect fun isInBindingCallback(quickJs: QuickJs): Boolean
