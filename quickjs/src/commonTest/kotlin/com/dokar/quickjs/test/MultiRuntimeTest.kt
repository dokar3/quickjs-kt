package com.dokar.quickjs.test

import com.dokar.quickjs.QuickJs
import com.dokar.quickjs.binding.define
import com.dokar.quickjs.binding.function
import com.dokar.quickjs.quickJs
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MultiRuntimeTest {
    @OptIn(ExperimentalStdlibApi::class)
    @Test
    fun runMultipleRuntimes() = runTest {
        runAndVerify(coroutineContext[CoroutineDispatcher]!!)
    }

    @Test
    fun runMultipleRuntimesInMultiThreads() = runTest {
        runAndVerify(Dispatchers.IO)
    }

    private suspend fun runAndVerify(dispatcher: CoroutineDispatcher) {
        val apps = List(10) {
            App(
                name = "App $it",
                version = "1.0.$it"
            )
        }

        val runtimes = apps.map { initRuntime(it, dispatcher) }

        for (i in apps.indices) {
            val app = apps[i]
            val runtime = runtimes[i]
            assertEquals(app.name, runtime.evaluate("app.name"))
            assertEquals(app.version, runtime.evaluate("app.version"))
            runtime.evaluate<Any?>("app.launch()")
            assertTrue(app.launched)
        }

        runtimes.forEach { it.close() }
    }

    // https://github.com/dokar3/quickjs-kt/issues/136
    @Test
    fun nestedQuickJsBlocks() = runTest {
        // Shared global states should be reference-counted to support nested DSL blocks.
        val result = quickJs {
            @OptIn(com.dokar.quickjs.ExperimentalQuickJsApi::class)
            function("foo") {
                2
            }
            quickJs {
                // The inner instance is closed here, but the outer one should remain functional.
            }
            // Verify that closing one instance doesn't affect another by clearing
            // the global JavaVM cache or other shared native states.
            evaluate<Int>("foo()")
        }
        assertEquals(2, result)
    }

    private fun initRuntime(
        app: App,
        dispatcher: CoroutineDispatcher,
    ) = QuickJs.create(jobDispatcher = dispatcher).apply {
        define("app") {
            property("name") {
                getter { app.name }
            }

            property("version") {
                getter { app.version }
            }

            function("launch") { app.launched = true }
        }
    }

    private class App(
        val name: String,
        val version: String,
        var launched: Boolean = false,
    )
}