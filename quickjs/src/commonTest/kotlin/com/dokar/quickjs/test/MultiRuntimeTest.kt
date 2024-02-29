package com.dokar.quickjs.test

import com.dokar.quickjs.QuickJs
import com.dokar.quickjs.binding.define
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