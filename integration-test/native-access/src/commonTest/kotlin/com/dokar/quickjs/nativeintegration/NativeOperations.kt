@file:OptIn(com.dokar.quickjs.ExperimentalQuickJsApi::class)

package com.dokar.quickjs.nativeintegration

import com.dokar.quickjs.QuickJsNativeContext

expect object NativeOperations {
    fun load()
    fun install(context: QuickJsNativeContext)
    fun execute(context: QuickJsNativeContext): Int
    fun uninstall(context: QuickJsNativeContext)
    fun cleanupCount(): Int
    fun resetCleanupCount()
}
