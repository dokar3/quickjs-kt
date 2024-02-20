package com.dokar.quickjs.test

import com.dokar.quickjs.binding.asyncFunction
import com.dokar.quickjs.quickJs
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals

class FlowToAsyncIteratorTest {
    @OptIn(DelicateCoroutinesApi::class)
    @Test
    fun flowToAsyncIterator() = runTest {
        val flow = flow {
            repeat(5) {
                delay(500)
                emit(it)
            }
        }

        var started = false
        val channel = Channel<Int>()
        fun startCollecting() {
            started = true
            launch {
                flow.collect(channel::send)
                channel.close()
            }
        }

        quickJs {
            asyncFunction("nextFlowValue") {
                if (!started) {
                    startCollecting()
                }
                if (!channel.isClosedForReceive) {
                    channel.receive()
                } else {
                    null
                }
            }

            val result = evaluate<Array<Any?>>(
                """
                async function* numbers() {
                    while (true) {
                        const value = await nextFlowValue();
                        if (value == null) {
                            break;
                        }
                        yield value;
                    }
                }
                
                const array = [];
                for await (const value of numbers()) {
                    array.push(value);
                }
                
                array;
                """.trimIndent()
            )
            assertContentEquals(Array(5) { it.toLong() }, result)
        }
    }
}