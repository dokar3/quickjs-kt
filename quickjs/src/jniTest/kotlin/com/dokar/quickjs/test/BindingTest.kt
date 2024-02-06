package com.dokar.quickjs.test

import com.dokar.quickjs.binding.define
import com.dokar.quickjs.binding.func
import com.dokar.quickjs.quickJs
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertTrue

class BindingTest {
    @Test
    fun bindFunction() = runTest {
        val result = quickJs {
            func("hello") { "World" }
            evaluate<String>("hello()")
        }
        assertEquals("World", result)
    }

    @Test
    fun bindObject() = runTest  {
        val result = quickJs {
            func("console") {}
            evaluate<Boolean>("console != null")
        }
        assertTrue(result)
    }


    @Test
    fun bindObjectWithReadOnlyProp() = runTest  {
        quickJs {
            define("logger") {
                prop("level") {
                    writable = false
                    getter { "Debug" }
                }
            }
            assertEquals("Debug", evaluate("logger.level"))
            assertFails { evaluate<Any?>("logger.level = 'Info'") }
        }
    }

    @Test
    fun bindObjectWithFuncAndProps() = runTest  {
        val version = "1.2.0"
        var name = "Unnamed"
        var launchCount = 0

        quickJs {
            define("app") {
                prop("version") {
                    getter { version }
                }

                prop("name") {
                    getter { name }
                    setter { name = it }
                }

                func("launch") { launchCount++ }
            }

            evaluate<Any>(
                """
                    if(app.version !== "1.2.0") {
                       throw new Error("Failed to validate version");
                    }
                    app.name = "My App";
                    app.launch();
                    app.launch();
                """.trimIndent()
            )
        }

        assertEquals(2, launchCount)
        assertEquals("My App", name)
    }
}