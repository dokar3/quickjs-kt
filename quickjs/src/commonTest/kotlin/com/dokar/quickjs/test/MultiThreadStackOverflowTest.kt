package com.dokar.quickjs.test

import com.dokar.quickjs.binding.asyncFunction
import com.dokar.quickjs.quickJs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.IO
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

class MultiThreadStackOverflowTest {
    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun stackOverflowWhenExecutingJobs() = runTest {
        // This ensures that our async jobs are executed in different threads
        quickJs(jobDispatcher = Dispatchers.IO.limitedParallelism(100)) {
            asyncFunction("runJob") {}

            // This CAN throw a stack overflow error if executePendingJob()
            // is not thread-safe.
            evaluate<Any?>(
                """
                    for (let i = 0; i < 1000; i++) {
                        await runJob();
                    }
                """.trimIndent()
            )
        }
    }
}