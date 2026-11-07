package com.dokar.quickjs

import com.dokar.quickjs.binding.asyncFunction
import com.dokar.quickjs.binding.define
import com.dokar.quickjs.binding.function
import kotlinx.coroutines.test.runTest
import java.lang.ref.WeakReference
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class JniLifetimeRegressionTest {
    @Test
    fun primitiveThrownByResultGetterRemainsValidDuringExceptionConversion() = runTest {
        quickJs {
            val failure = assertFailsWith<QuickJsException> {
                evaluate<Any?>(
                    """
                    ({
                        get message() {
                            throw 'primitive getter failure';
                        }
                    })
                    """.trimIndent()
                )
            }

            assertContains(failure.message.orEmpty(), "primitive getter failure")
        }
    }

    @Test
    fun missingCollectionConstructorDoesNotInvalidateGlobalObject() = runTest {
        quickJs {
            function("returnSet") { setOf(1L, 2L) }

            assertFailsWith<QuickJsException> {
                evaluate<Any?>("delete globalThis.Set; returnSet()")
            }

            assertEquals(3L, evaluate<Long>("1 + 2"))
            assertEquals("[object global]", evaluate<String>("String(globalThis)"))
        }
    }

    @Test
    fun nestedCircularCollectionDoesNotDeleteBorrowedVisitedReference() = runTest {
        quickJs {
            function("nestedCircularCollection") {
                val list = mutableListOf<Any?>()
                val set = mutableSetOf<Any?>()
                list.add(set)
                set.add(list)
                list
            }

            repeat(20) {
                assertFailsWith<QuickJsException> {
                    evaluate<Any?>("nestedCircularCollection()")
                }
                assertEquals(3L, evaluate<Long>("1 + 2"))
            }
        }
    }

    // A long-running script never returns from evaluate(), so any local ref a host call
    // forgets to delete pins its Java objects until the heap runs out.
    @Test
    fun hostCallValuesAreReleasedBeforeEvaluationEnds() = runTest {
        val refs = mutableListOf<WeakReference<Any>>()
        fun track(value: Any?) {
            refs += WeakReference(value!!)
        }

        quickJs {
            function("call") { args ->
                track(args[0])
                "returned ".repeat(100).also(::track)
            }
            asyncFunction("callAsync") { args -> track(args[0]) }
            define("host") {
                property("value") {
                    getter { "got ".repeat(100).also(::track) }
                    setter { track(it) }
                }
            }
            function("allCollected") {
                repeat(10) {
                    System.gc()
                    if (refs.all { it.get() == null }) return@function true
                    Thread.sleep(10)
                }
                false
            }

            val collected = evaluate<Boolean>(
                """
                call('arg '.repeat(100));
                await callAsync('async '.repeat(100));
                host.value;
                host.value = 'set '.repeat(100);
                allCollected();
                """.trimIndent()
            )
            assertEquals(5, refs.size)
            assertTrue(collected)
        }
    }
}
