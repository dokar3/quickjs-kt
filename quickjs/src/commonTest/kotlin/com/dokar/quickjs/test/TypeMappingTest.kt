package com.dokar.quickjs.test

import com.dokar.quickjs.QuickJsException
import com.dokar.quickjs.binding.JsObject
import com.dokar.quickjs.binding.asyncFunction
import com.dokar.quickjs.binding.define
import com.dokar.quickjs.binding.function
import com.dokar.quickjs.binding.toJsObject
import com.dokar.quickjs.quickJs
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class TypeMappingTest {
    @Test
    fun jsReturns() = runTest {
        quickJs {
            // null
            assertEquals(null, evaluate<Any?>("null"))
            // undefined
            assertEquals(null, evaluate<Any?>("undefined"))
            // boolean
            assertEquals(false, evaluate("false"))
            // string
            assertEquals("hello", evaluate("""'hello'"""))
            // number
            assertEquals(1, evaluate("""1"""))
            assertEquals(1L, evaluate("""1"""))
            assertEquals(1.0f, evaluate("""1.0"""))
            assertEquals(1.1f, evaluate("""1.1"""))
            assertEquals(1.0, evaluate("""1.0"""))
            assertEquals(1.1, evaluate("""1.1"""))
            // Array
            assertContentEquals(listOf<Any?>(0L, 1L, null), evaluate("[0, 1, null]"))
            // Set
            assertEquals(linkedSetOf(0L, 1L), evaluate("new Set([0, 1])"))
            // Map
            assertEquals(
                mapOf(0L to "Red", 1L to "Pink"),
                evaluate("new Map([[0, 'Red'], [1, 'Pink']])")
            )
            // Object
            val result = evaluate<JsObject>(
                """
                    const result = { ok: false, error: 'Seems good' };
                    result
                """.trimIndent()
            )
            assertEquals(mapOf("ok" to false, "error" to "Seems good").toJsObject(), result)
        }
    }

    @Test
    fun ktReturns() = runTest {
        quickJs {
            function("returnsBoolean") { false }
            function("returnsInt") { 1 }
            function("returnsLong") { 1L }
            function("returnsFloat") { 1.2f }
            function("returnsDouble") { 1.2 }
            function("returnsString") { "hello" }
            function("returnsArray") { arrayOf("hello") }
            function("returnsUnsupportedArray") { arrayOf("hello", Any()) }
            function("returnsList") { listOf("hello") }
            function("returnsSet") { setOf("hello") }
            function("returnsMap") { mapOf("hello" to "world") }
            function("returnsJsObject") { mapOf("hello" to "world").toJsObject() }
            function("returnsAny") { Any() }

            assertEquals(false, evaluate("returnsBoolean()"))
            assertEquals(1, evaluate("returnsInt()"))
            assertEquals(1L, evaluate("returnsLong()"))
            assertEquals(1.2f, evaluate("returnsFloat()"))
            assertEquals(1.2, evaluate("returnsDouble()"))
            assertEquals("hello", evaluate("returnsString()"))
            assertContentEquals(listOf<Any?>("hello"), evaluate("returnsArray()"))
            assertFails { evaluate("returnsUnsupportedArray()") }
            assertContentEquals(listOf<Any?>("hello"), evaluate("returnsList()"))
            assertEquals(setOf("hello"), evaluate("returnsSet()"))
            assertEquals(mapOf("hello" to "world"), evaluate("returnsMap()"))
            assertEquals("world", evaluate("returnsJsObject().hello"))
            assertFails { evaluate("returnsAny()") }
        }
    }

    @Test
    fun jsPassesNumbers() = runTest {
        quickJs {
            function("ints") {
                assertEquals(1L, it[0])
            }
            function("floats") {
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
            function("booleans") {
                assertTrue(it[0] is Boolean)
            }
            evaluate<Any?>("booleans(false); booleans(true)")
        }
    }

    @Test
    fun jsPassesStrings() = runTest {
        quickJs {
            function("strings") {
                assertEquals("Hello", it[0])
            }
            evaluate<Any?>("strings('Hello')")
        }
    }

    @Test
    fun jsPassesArrays() = runTest {
        quickJs {
            function("arrays") {
                val arr = it[0]
                assertTrue(arr is List<*>)
                assertTrue(arr[0] is Long)
                assertTrue(arr[1] is Double)
                assertTrue(arr[2] is Boolean)
                assertTrue(arr[3] is String)
                assertTrue(arr[4] is List<*>)
                assertTrue((arr[4] as List<*>).size == 2)
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
            function("sets") {
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
            function("objects") {
                val map = it[0]
                assertTrue(map is Map<*, *>)
                assertEquals(false, map["ok"])
                assertEquals("Nothing", map["error"])
            }
            evaluate<Any?>(
                """
                    objects(new Map([["ok", false], ["error", "Nothing"]]))
                    objects({ ok: false, error: "Nothing" })
                """.trimIndent()
            )
        }
    }

    @Test
    fun jsNestedObjects() = runTest {
        quickJs {
            val result = evaluate<Map<Any?, Any?>>(
                """
                const obj = {
                    id: 1,
                    request: {
                        method: "GET",
                        http: {
                            http_proxy: "NO_PROXY",
                        },
                    },
                    response: {
                        status: 200,
                    }
                };
                obj;
            """.trimIndent()
            )
            assertEquals(1L, result["id"])
            val request = result["request"] as Map<*, *>
            assertEquals(request["method"], "GET")
            assertEquals((request["http"] as Map<*, *>)["http_proxy"], "NO_PROXY")
            val response = result["response"] as Map<*, *>
            assertEquals(200L, response["status"])
        }
    }

    @Test
    fun ktNestedObjects() = runTest {
        quickJs {
            val request = mutableMapOf<String, Any?>()
            request["request"] = mapOf(
                "url" to "https://www.example.com",
                "method" to "GET",
                "headers" to mapOf(
                    "Content-Type" to "application/json",
                ),
            )

            function("getRequest") { request }

            assertEquals(request, evaluate("getRequest()"))
        }
    }

    @Test
    fun jsListNodes() = runTest {
        quickJs {
            assertFails {
                evaluate<Any?>(
                    """
                        const head = { prev: null, next: null, val: 0 };
                        const next = { prev: null, next: null, val: 1 }
                        const tail = { prev: null, next: null, val: 2 }
                        head.next = next;
                        next.prev = head;
                        next.next = tail;
                        tail.prev = next;
                        head;
                    """.trimIndent()
                )
            }.also {
                assertContains(it.message!!, "circular reference")
            }
        }
    }

    @Test
    fun jsCircularRefObjects() = runTest {
        quickJs {
            assertFails {
                evaluate<Any?>(
                    """
                        const object = {};
                        object.self = object;
                        object;
                    """.trimIndent()
                )
            }.also {
                assertContains(it.message!!, "circular reference")
            }

            val globalThis = evaluate<Map<Any?, Any?>>("globalThis")
            assertEquals("[object global]", globalThis["globalThis"])
        }
    }

    @Test
    fun ktCircularRefObjects() = runTest {
        quickJs {
            function("circularRefArray") {
                val arr = arrayOf<Any?>(null)
                arr[0] = arr
                arr
            }

            function("circularRefList") {
                val list = mutableListOf<Any?>(null)
                list[0] = list
                list
            }

            function("circularRefSet") {
                val set = mutableSetOf<Any?>()
                set.add(set)
                set
            }

            function("circularRefMap") {
                val map = mutableMapOf<String, Map<*, *>?>()
                map["next"] = map
                map
            }

            assertFails { evaluate("circularRefArray()") }
                .also { assertContains(it.message!!, "circular reference") }
            assertFails { evaluate("circularRefList()") }
                .also { assertContains(it.message!!, "circular reference") }
            assertFails { evaluate("circularRefSet()") }
                .also { assertContains(it.message!!, "circular reference") }
            assertFails { evaluate("circularRefMap()") }
                .also { assertContains(it.message!!, "circular reference") }
        }
    }

    @Test
    fun ktDeepLevelCircularRefs() = runTest {
        quickJs {
            function("circularRefArray") {
                val arr = arrayOf<Any?>(null)
                arr[0] = listOf(arrayOf(arr))
                arr
            }

            function("circularRefList") {
                val list = mutableListOf<Any?>(null)
                list[0] = arrayOf(1, 2, list)
                list
            }

            function("circularRefSet") {
                val set = mutableSetOf<Any?>()
                set.add(arrayOf(setOf(1, set)))
                set
            }

            function("circularRefMap") {
                val map = mutableMapOf<String, Any?>()
                map["next"] = arrayOf(map)
                map
            }

            assertFails { evaluate("circularRefArray()") }
                .also { assertContains(it.message!!, "circular reference") }
            assertFails { evaluate("circularRefList()") }
                .also { assertContains(it.message!!, "circular reference") }
            assertFails { evaluate("circularRefSet()") }
                .also { assertContains(it.message!!, "circular reference") }
            assertFails { evaluate("circularRefMap()") }
                .also { assertContains(it.message!!, "circular reference") }
        }
    }

    @Test
    fun typedParameters() = runTest {
        quickJs {
            function<String, String>("greet") { "Hello, $it!" }

            function<String?, String>("nullableGreet") { "Hello, $it!" }

            asyncFunction<String, String>("greetAsync") { "Hello, $it!" }

            define("http") {
                function<String, String>("fetchSync") { "OK" }

                asyncFunction<String, String>("fetch") { "OK" }
            }

            assertFailsWith<QuickJsException> { evaluate<String>("greet()") }.also {
                val message = "Function 'greet' requires 1 parameter but none was passed."
                assertContains(it.message!!, message)
            }
            assertFailsWith<QuickJsException> { evaluate<String>("greet(1, 2)") }.also {
                val message = "Function 'greet' requires 1 parameter but 2 were passed."
                assertContains(it.message!!, message)
            }
            assertFailsWith<QuickJsException> { evaluate<String>("greet(null)") }.also {
                val message =
                    "Function 'greet' requires 1 non-null parameter but null was passed."
                assertContains(it.message!!, message)
            }
            assertEquals("Hello, null!", evaluate<String>("""nullableGreet(null)"""))
            assertEquals("Hello, Jack!", evaluate<String>("""greet("Jack")"""))
            assertEquals("Hello, Jack!", evaluate<String>("""await greetAsync("Jack")"""))

            assertEquals("OK", evaluate<String>("""http.fetchSync("something")"""))
            assertEquals("OK", evaluate<String>("""await http.fetch("something")"""))
        }
    }
}