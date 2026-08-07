@file:OptIn(com.dokar.quickjs.ExperimentalQuickJsApi::class)

package com.dokar.quickjs.nativeintegration

import com.dokar.quickjs.QuickJsNativeContext

actual object NativeOperations {
    private var loaded = false

    actual fun load() {
        if (loaded) return
        System.load(requireNotNull(System.getProperty("quickjs.native.operations.path")))
        loaded = true
    }

    actual fun install(context: QuickJsNativeContext) {
        nativeInstall(context.contextAddress, context.runtimeAddress)
    }

    actual fun execute(context: QuickJsNativeContext): Int {
        return nativeExecute(context.contextAddress, context.runtimeAddress)
    }

    actual fun uninstall(context: QuickJsNativeContext) {
        nativeUninstall(context.contextAddress, context.runtimeAddress)
    }

    actual fun cleanupCount(): Int = nativeCleanupCount()

    actual fun resetCleanupCount() {
        nativeResetCleanupCount()
    }

    @JvmStatic
    private external fun nativeInstall(contextAddress: Long, runtimeAddress: Long)

    @JvmStatic
    private external fun nativeExecute(contextAddress: Long, runtimeAddress: Long): Int

    @JvmStatic
    private external fun nativeUninstall(contextAddress: Long, runtimeAddress: Long)

    @JvmStatic
    private external fun nativeCleanupCount(): Int

    @JvmStatic
    private external fun nativeResetCleanupCount()
}
