@file:OptIn(
    com.dokar.quickjs.ExperimentalQuickJsApi::class,
    kotlinx.cinterop.ExperimentalForeignApi::class,
)

package com.dokar.quickjs.nativeintegration

import com.dokar.quickjs.QuickJsNativeContext
import nativeoperations.quickjs_native_operations_cleanup_count
import nativeoperations.quickjs_native_operations_execute
import nativeoperations.quickjs_native_operations_install
import nativeoperations.quickjs_native_operations_reset_cleanup_count
import nativeoperations.quickjs_native_operations_uninstall

actual object NativeOperations {
    actual fun load() = Unit

    actual fun install(context: QuickJsNativeContext) {
        quickjs_native_operations_install(context.context, context.runtime)
    }

    actual fun execute(context: QuickJsNativeContext): Int {
        return quickjs_native_operations_execute(context.context, context.runtime)
    }

    actual fun uninstall(context: QuickJsNativeContext) {
        quickjs_native_operations_uninstall(context.context, context.runtime)
    }

    actual fun cleanupCount(): Int = quickjs_native_operations_cleanup_count()

    actual fun resetCleanupCount() {
        quickjs_native_operations_reset_cleanup_count()
    }
}
