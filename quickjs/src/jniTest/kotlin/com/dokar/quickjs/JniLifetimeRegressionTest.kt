package com.dokar.quickjs

import com.dokar.quickjs.binding.function
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

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
}
