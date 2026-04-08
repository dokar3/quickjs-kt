package com.dokar.quickjs.test

import com.dokar.quickjs.QuickJs
import com.dokar.quickjs.binding.define
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlin.test.Test

class ConcurrentDefineBindingStressTest {
    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun concurrentDefineBinding() = runBlocking {
        repeat(50) { round ->
            val quickJs = QuickJs.create(UnconfinedTestDispatcher())
            try {
                val start = CompletableDeferred<Unit>()
                val jobs = List(16) { worker ->
                    launch(Dispatchers.Default) {
                        start.await()
                        repeat(200) { index ->
                            quickJs.define("obj_${round}_${worker}_$index") {
                                property("value") {
                                    getter { index }
                                }
                                function("call") { worker + index }
                            }
                        }
                    }
                }
                start.complete(Unit)
                jobs.joinAll()
            } finally {
                quickJs.close()
            }
        }
    }
}
