package com.dokar.quickjs.test

import com.dokar.quickjs.QuickJs
import com.dokar.quickjs.binding.define
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MultiRuntimeTest {
    @Test
    fun runMultipleRuntimes() = runTest {
        val apps = List(10) {
            App(
                name = "App $it",
                version = "1.0.$it"
            )
        }

        val runtimes = apps.map { initRuntime(it) }

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

    private fun initRuntime(app: App) = QuickJs.create().apply {
        define("app") {
            prop("name") {
                getter { app.name }
            }

            prop("version") {
                getter { app.version }
            }

            func("launch") { app.launched = true }
        }
    }

    private class App(
        val name: String,
        val version: String,
        var launched: Boolean = false,
    )
}