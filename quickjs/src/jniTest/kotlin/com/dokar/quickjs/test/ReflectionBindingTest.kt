package com.dokar.quickjs.test

import com.dokar.quickjs.binding.canBeCalledAsSuspend
import com.dokar.quickjs.binding.define
import com.dokar.quickjs.quickJs
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlin.coroutines.Continuation
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertFalse
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
    fun bindInstanceWithSuspendFunc() = runTest {
        quickJs {
            val instance = ClassWithSuspendFunc()
            define("instance", ClassWithSuspendFunc::class.java, instance)

            assertEquals("Hello", evaluate("await instance.greet()"))
            assertEquals("No response", evaluate("await instance.fetch('whatever')"))
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

    @Test
    fun canMethodsBeCalledAsSuspend() {
        val methods = SuspendFunctions::class.java.methods.associateBy { it.name }
        assertTrue(methods["realSuspend"]!!.canBeCalledAsSuspend())
        assertTrue(methods["realSuspendWithArgs"]!!.canBeCalledAsSuspend())
        assertFalse(methods["notSuspend"]!!.canBeCalledAsSuspend())
        assertFalse(methods["likelyButNotSuspend"]!!.canBeCalledAsSuspend())
        assertTrue(methods["compiledSuspend"]!!.canBeCalledAsSuspend())
        assertTrue(methods["compiledSuspendWithArgs"]!!.canBeCalledAsSuspend())
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

    @Suppress("unused", "UNUSED_PARAMETER")
    private class ClassWithSuspendFunc {
        suspend fun greet(): String {
            delay(100)
            return "Hello"
        }

        suspend fun fetch(url: String): String {
            delay(100)
            return "No response"
        }
    }

    @Suppress("unused", "UNUSED_PARAMETER")
    private class SuspendFunctions {
        suspend fun realSuspend() {}
        suspend fun realSuspendWithArgs(name: String) {}
        fun notSuspend() {}
        fun likelyButNotSuspend(continuation: Continuation<*>) {}
        fun compiledSuspend(continuation: Continuation<*>): Any? = null
        fun compiledSuspendWithArgs(name: String, continuation: Continuation<*>): Any? = null
    }
}