package com.dokar.quickjs

import com.dokar.quickjs.binding.JsFunction
import com.dokar.quickjs.binding.JsObjectHandle
import com.dokar.quickjs.binding.JsProperty
import com.dokar.quickjs.binding.ObjectBinding
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class NativeLifetimeRegressionTest {
    @Test
    fun concurrentCloseIsIdempotent() = runTest {
        repeat(200) {
            val quickJs = QuickJs.create(Dispatchers.Default)
            val start = CompletableDeferred<Unit>()
            val closers = List(8) {
                launch(Dispatchers.Default) {
                    start.await()
                    quickJs.close()
                }
            }

            start.complete(Unit)
            closers.joinAll()
        }
    }

    @Test
    fun deletedBindingHandleRetainsItsObject() = runTest {
        val quickJs = QuickJs.create(Dispatchers.Default)
        try {
            val parent = quickJs.defineBinding(
                name = "temporaryBinding",
                binding = EmptyObjectBinding,
                parent = JsObjectHandle.globalThis,
            )
            assertTrue(quickJs.evaluate<Boolean>("delete globalThis.temporaryBinding"))

            quickJs.defineBinding(
                name = "child",
                binding = EmptyObjectBinding,
                parent = parent,
            )

            assertEquals(42L, quickJs.evaluate<Long>("40 + 2"))
        } finally {
            quickJs.close()
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
