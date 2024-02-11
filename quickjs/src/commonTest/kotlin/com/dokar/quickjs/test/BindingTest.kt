package com.dokar.quickjs.test

import com.dokar.quickjs.binding.define
import com.dokar.quickjs.binding.function
import com.dokar.quickjs.quickJs
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BindingTest {
    @Test
    fun bindFunction() = runTest {
        val result = quickJs {
            function("hello") { "World" }
            evaluate<String>("hello()")
        }
        assertEquals("World", result)
    }

    @Test
    fun bindObject() = runTest {
        val result = quickJs {
            define("console") {}
            evaluate<Boolean>("console != null")
        }
        assertTrue(result)
    }

    @Test
    fun bindObjectWithReadOnlyProp() = runTest {
        quickJs {
            define("logger") {
                property("level") {
                    writable = false
                    getter { "Debug" }
                }
            }
            assertEquals("Debug", evaluate("logger.level"))
            evaluate<Any?>("logger.level = 'Info'")
        }
    }

    @Test
    fun bindObjectWithFuncAndProps() = runTest {
        val version = "1.2.0"
        var name = "Unnamed"
        var launchCount = 0

        quickJs {
            define("app") {
                property("version") {
                    getter { version }
                }

                property("name") {
                    getter { name }
                    setter { name = it }
                }

                function("launch") { launchCount++ }
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