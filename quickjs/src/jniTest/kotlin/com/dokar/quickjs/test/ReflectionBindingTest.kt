package com.dokar.quickjs.test

import com.dokar.quickjs.binding.define
import com.dokar.quickjs.quickJs
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertTrue

class ReflectionBindingTest {
    @Test
    fun bindInstance() = runTest {
        quickJs {
            val instance = TestClass()
            define("instance", TestClass::class.java, instance)
            assertTrue(evaluate("instance.privateField === undefined"))
            assertEquals(instance.publicField, evaluate("instance.publicField"))
            assertFails { evaluate("instance.privateFunc()") }
            assertEquals(instance.publicFunc(), evaluate("instance.publicFunc()"))

        }
    }

    @Test
    fun functionParameters() = runTest {
        quickJs {
            val instance = Functions()
            define<Functions>("functions", instance)
            evaluate<Any?>(
                """
                    functions.boolean(false)
                    functions.int(0)
                    functions.long(0)
                    functions.float(1.0)
                    functions.double(1.0)
                    functions.string("")
                    functions.array([1, 2])
                    functions.set(new Set())
                    functions.map(new Map())
                    functions.objects({})
                    functions.multiple(0, 1, 2, "string", [])
                """.trimIndent()
            )
        }
    }

    @Suppress("unused")
    private class TestClass {
        private val privateField = 0

        @JvmField
        val publicField = 0

        private fun privateFunc() {}

        fun publicFunc(): Int {
            return 0
        }
    }

    @Suppress("unused", "UNUSED_PARAMETER")
    private class Functions {
        fun boolean(arg: Boolean) {}
        fun int(arg: Int) {}
        fun long(arg: Long) {}
        fun float(arg: Float) {}
        fun double(arg: Double) {}
        fun string(arg: String) {}
        fun array(arg: Array<Any?>) {}
        fun set(arg: Set<*>) {}
        fun map(arg: Map<*, *>) {}
        fun objects(arg: Map<*, *>) {}
        fun multiple(int: Int, long: Long, float: Float, string: String, arr: Array<*>) {}
    }
}