package com.dokar.quickjs.test

import com.dokar.quickjs.binding.function
import com.dokar.quickjs.quickJs
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class JsPromiseTest {
    @Test
    fun asyncFunction() = runTest {
        quickJs {
            val result = evaluate<Int>(
                """
                async function add(a, b) {
                    return a + b;
                }
                await add(1, 2)
                """.trimIndent()
            )
            assertEquals(3, result)
        }
    }

    @Test
    fun promise() = runTest {
        quickJs {
            val result = evaluate<Int>(
                """
                await new Promise((resolve) => resolve(1))
                    .then((result) => result + 1)
                """.trimIndent()
            )
            assertEquals(2, result)
        }
    }

    @Test
    fun promiseDotAll() = runTest {
        quickJs {
            val result = evaluate<List<*>>(
                """
                async function add(a, b) {
                    return a + b;
                }
                await Promise.all([add(1, 2), add(3, 4)])
                """.trimIndent()
            )
            assertEquals(listOf(3L, 7L), result)
        }
    }

    @Test
    fun promiseWithoutAwait() = runTest {
        quickJs {
            val logs = mutableListOf<String>()

            function<String, Unit>("log") { logs += it }

            evaluate<Any?>(
                """
                log("Start");
                new Promise((resolve) => resolve())
                    .then(() => log("Then"));
                log("End");
                """.trimIndent()
            )

            assertEquals(listOf("Start", "End", "Then"), logs)
        }
    }
}