package com.dokar.quickjs.test

import com.dokar.quickjs.binding.func
import com.dokar.quickjs.binding.toJsObject
import com.dokar.quickjs.quickJs
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertTrue

class TypeMappingTest {
    @Test
    fun jsReturns() = runTest {
        quickJs {
            assertEquals(null, evaluate<Any?>("null"))
            assertEquals(null, evaluate<Any?>("undefined"))
            assertEquals(false, evaluate("false"))
            assertEquals("hello", evaluate("""'hello'"""))
            assertEquals(1, evaluate("""1"""))
            assertEquals(1L, evaluate("""1"""))
            assertEquals(1.0f, evaluate("""1.0"""))
            assertEquals(1.1f, evaluate("""1.1"""))
            assertEquals(1.0, evaluate("""1.0"""))
            assertEquals(1.1, evaluate("""1.1"""))
            assertContentEquals(arrayOf<Any?>(0L, 1L, null), evaluate("[0, 1, null]"))
            assertEquals(linkedSetOf(0L, 1L), evaluate("new Set([0, 1])"))
            assertEquals(
                mapOf(0L to "Red", 1L to "Pink"),
                evaluate("new Map([[0, 'Red'], [1, 'Pink']])")
            )
            val result = evaluate<Map<String, Any>>(
                """
                const result = { ok: false, error: 'Seems good' };
                result
                """.trimIndent()
            )
            assertEquals(mapOf("ok" to false, "error" to "Seems good"), result)
        }
    }

    @Test
    fun ktReturns() = runTest {
        quickJs {
            func("returnsBoolean") { false }
            func("returnsInt") { 1 }
            func("returnsLong") { 1L }
            func("returnsFloat") { 1.2f }
            func("returnsDouble") { 1.2 }
            func("returnsString") { "hello" }
            func("returnsArray") { arrayOf("hello") }
            func("returnsUnsupportedArray") { arrayOf("hello", Any()) }
            func("returnsList") { listOf("hello") }
            func("returnsSet") { setOf("hello") }
            func("returnsMap") { mapOf("hello" to "world") }
            func("returnsJsObject") { mapOf("hello" to "world").toJsObject() }
            func("returnsAny") { Any() }

            assertEquals(false, evaluate("returnsBoolean()"))
            assertEquals(1, evaluate("returnsInt()"))
            assertEquals(1L, evaluate("returnsLong()"))
            assertEquals(1.2f, evaluate("returnsFloat()"))
            assertEquals(1.2, evaluate("returnsDouble()"))
            assertEquals("hello", evaluate("returnsString()"))
            assertContentEquals(arrayOf<Any?>("hello"), evaluate("returnsArray()"))
            assertFails { evaluate("returnsUnsupportedArray()") }
            assertContentEquals(arrayOf<Any?>("hello"), evaluate("returnsList()"))
            assertEquals(setOf("hello"), evaluate("returnsSet()"))
            assertEquals(mapOf("hello" to "world"), evaluate("returnsMap()"))
            assertEquals("world", evaluate("returnsJsObject().hello"))
            assertFails { evaluate("returnsAny()") }
        }
    }

    @Test
    fun jsPassesNumbers() = runTest {
        quickJs {
            func("ints") {
                assertEquals(1L, it[0])
            }
            func("floats") {
                if (it[0] is Long) {
                    // 1.0 will be converted to a long
                    assertEquals(1L, it[0])
                } else {
                    assertEquals(1.1, it[0])
                }
            }
            evaluate<Any?>("ints(1); floats(1.0); floats(1.1)")
        }
    }

    @Test
    fun jsPassesBooleans() = runTest {
        quickJs {
            func("booleans") {
                assertTrue(it[0] is Boolean)
            }
            evaluate<Any?>("booleans(false); booleans(true)")
        }
    }

    @Test
    fun jsPassesStrings() = runTest {
        quickJs {
            func("strings") {
                assertEquals("Hello", it[0])
            }
            evaluate<Any?>("strings('Hello')")
        }
    }

    @Test
    fun jsPassesArrays() = runTest {
        quickJs {
            func("arrays") {
                val arr = it[0]
                assertTrue(arr is Array<*>)
                assertTrue(arr[0] is Long)
                assertTrue(arr[1] is Double)
                assertTrue(arr[2] is Boolean)
                assertTrue(arr[3] is String)
                assertTrue(arr[4] is Array<*>)
                assertTrue((arr[4] as Array<*>).size == 2)
                assertTrue(arr[5] == null)
                assertTrue(arr[6] == null)
            }
            evaluate<Any?>(
                """
                    arrays([
                        1,
                        1.2,
                        false,
                        "hello",
                        [1, 2],
                        null,
                        undefined,
                    ])
                """.trimIndent()
            )
        }
    }

    @Test
    fun jsPassesSets() = runTest {
        quickJs {
            func("sets") {
                val set = it[0]
                assertTrue(set is Set<*>)
                assertEquals(setOf(0L, 1L), set)
            }
            evaluate<Any?>("sets(new Set([0, 1]))")
        }
    }

    @Test
    fun jsPassesObjectsAndMaps() = runTest {
        quickJs {
            func("objects") {
                val map = it[0]
                assertTrue(map is Map<*, *>)
                assertEquals(false, map["ok"])
                assertEquals("Nothing", map["error"])
            }
            evaluate<Any?>(
                """
                    objects({ ok: false, error: "Nothing" })
                    objects(new Map([["ok", false], ["error", "Nothing"]]))
                """.trimIndent()
            )
        }
    }
}