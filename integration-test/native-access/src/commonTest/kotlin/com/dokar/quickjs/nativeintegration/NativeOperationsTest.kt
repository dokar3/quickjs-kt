package com.dokar.quickjs.nativeintegration

import com.dokar.quickjs.ExperimentalQuickJsApi
import com.dokar.quickjs.QuickJs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalQuickJsApi::class)
class NativeOperationsTest {
    @Test
    fun nativeBindingInjectionAndExecutionUseTheExistingRuntime() = runTest {
        NativeOperations.load()
        NativeOperations.resetCleanupCount()

        val quickJs = QuickJs.create(Dispatchers.Default)
        val cleanupOrder = mutableListOf<String>()
        quickJs.onNativeClose { context ->
            cleanupOrder += "native"
            NativeOperations.uninstall(context)
        }
        quickJs.onNativeClose {
            cleanupOrder += "last"
        }

        quickJs.withNativeContext { context ->
            NativeOperations.install(context)
        }

        assertEquals(5, quickJs.evaluate<Int>("nativeSum(2, 3)"))
        assertEquals(45, quickJs.withNativeContext { context ->
            NativeOperations.execute(context)
        })
        assertEquals(40, quickJs.evaluate<Int>("nativeInjected"))

        quickJs.close()

        assertEquals(listOf("last", "native"), cleanupOrder)
        assertEquals(1, NativeOperations.cleanupCount())
        assertTrue(quickJs.isClosed)
    }
}
