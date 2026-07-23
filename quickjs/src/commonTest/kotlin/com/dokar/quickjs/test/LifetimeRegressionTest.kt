package com.dokar.quickjs.test

import com.dokar.quickjs.QuickJs
import com.dokar.quickjs.QuickJsException
import com.dokar.quickjs.binding.JsFunction
import com.dokar.quickjs.binding.JsObjectHandle
import com.dokar.quickjs.binding.JsProperty
import com.dokar.quickjs.binding.ObjectBinding
import com.dokar.quickjs.binding.function
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith

class LifetimeRegressionTest {
    @Test
    fun nestedCrossRuntimeBindingTracksAllActiveRuntimes() = runTest {
        val outer = QuickJs.create(Dispatchers.Default)
        val inner = QuickJs.create(Dispatchers.Default)
        try {
            inner.function("touchOuterRuntime") {
                outer.memoryUsage
                assertFailsWith<QuickJsException> { outer.close() }.message.orEmpty()
            }
            outer.function("enterInnerRuntime") {
                runBlocking {
                    inner.evaluate<String>("touchOuterRuntime()")
                }
            }

            val closeError = outer.evaluate<String>("enterInnerRuntime()")
            assertContains(
                closeError,
                "Cannot close QuickJs from within a binding callback.",
            )
            assertFalse(outer.isClosed)
            assertEquals(3L, outer.evaluate<Long>("1 + 2"))
        } finally {
            inner.close()
            outer.close()
        }
    }

    @Test
    fun closeFromSynchronousBindingFailsWithoutClosingRuntime() = runTest {
        val quickJs = QuickJs.create(Dispatchers.Default)
        try {
            quickJs.function("closeFromBinding") {
                quickJs.close()
            }

            val error = assertFailsWith<QuickJsException> {
                quickJs.evaluate<Any?>("closeFromBinding()")
            }
            assertContains(
                error.message.orEmpty(),
                "Cannot close QuickJs from within a binding callback.",
            )
            assertFalse(quickJs.isClosed)
            assertEquals(3L, quickJs.evaluate<Long>("1 + 2"))
        } finally {
            quickJs.close()
        }
    }

    @Test
    fun synchronousBindingCanCallRuntimeApis() = runTest {
        val quickJs = QuickJs.create(Dispatchers.Default)
        try {
            val moduleBytecode = quickJs.compile(
                code = "export const fromBytecode = 20;",
                filename = "callback-bytecode",
                asModule = true,
            )

            quickJs.function("touchRuntimeApis") {
                quickJs.memoryUsage
                quickJs.memoryLimit = quickJs.memoryLimit
                quickJs.maxStackSize = quickJs.maxStackSize
                quickJs.gc()
                quickJs.addModule(moduleBytecode)
                quickJs.addModule(
                    name = "callback-source",
                    code = "export const fromSource = 22;",
                )
                quickJs.compile("40 + 2").isNotEmpty()
            }

            assertEquals(true, quickJs.evaluate<Boolean>("touchRuntimeApis()"))
            var moduleSum: Long? = null
            quickJs.function("captureModuleSum") { args ->
                moduleSum = args.single() as Long
            }
            quickJs.evaluate<Any?>(
                code = """
                    import { fromBytecode } from "callback-bytecode";
                    import { fromSource } from "callback-source";
                    captureModuleSum(fromBytecode + fromSource);
                """.trimIndent(),
                asModule = true,
            )
            assertEquals(42L, moduleSum)
        } finally {
            quickJs.close()
        }
    }

    @Test
    fun failedObjectDefinitionDoesNotLeaveDanglingHandle() = runTest {
        val quickJs = QuickJs.create(Dispatchers.Default)
        try {
            val parent = quickJs.defineBinding(
                name = "Infinity",
                binding = EmptyObjectBinding,
                parent = JsObjectHandle.globalThis,
            )

            // Defining on the non-configurable global property fails internally and
            // must not leave the returned handle pointing at a freed JSValue.
            try {
                quickJs.defineBinding(
                    name = "child",
                    binding = EmptyObjectBinding,
                    parent = parent,
                )
            } catch (_: QuickJsException) {
                // A safe implementation may reject the invalid parent handle.
            }

            assertEquals(3L, quickJs.evaluate<Long>("1 + 2"))
        } finally {
            quickJs.close()
        }
    }

    @Test
    fun lifecycleAccessorsCanRaceClose() = runTest {
        repeat(50) {
            val quickJs = QuickJs.create(Dispatchers.Default)
            val start = CompletableDeferred<Unit>()
            val accessors = listOf(
                launch(Dispatchers.Default) {
                    start.await()
                    repeat(64) {
                        ignoreClosed { quickJs.memoryUsage }
                    }
                },
                launch(Dispatchers.Default) {
                    start.await()
                    repeat(64) {
                        ignoreClosed { quickJs.memoryLimit = -1L }
                        ignoreClosed { quickJs.maxStackSize = 256 * 1024L }
                    }
                },
                launch(Dispatchers.Default) {
                    start.await()
                    repeat(64) {
                        ignoreClosed { quickJs.interruptEvaluation() }
                    }
                },
                launch(Dispatchers.Default) {
                    start.await()
                    quickJs.close()
                },
            )

            start.complete(Unit)
            accessors.joinAll()
            quickJs.close()
        }
    }

    private inline fun ignoreClosed(block: () -> Unit) {
        try {
            block()
        } catch (_: QuickJsException) {
            // Closing concurrently is allowed to make the operation fail normally.
        }
    }

    private object EmptyObjectBinding : ObjectBinding {
        override val properties: List<JsProperty> = emptyList()
        override val functions: List<JsFunction> = emptyList()

        override fun getter(name: String): Any? = error("No properties")

        override fun setter(name: String, value: Any?) = error("No properties")

        override fun invoke(name: String, args: Array<Any?>): Any? = error("No functions")
    }
}
