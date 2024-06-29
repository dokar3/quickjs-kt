package com.dokar.quickjs.test

import com.dokar.quickjs.QuickJs
import com.dokar.quickjs.binding.function
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.IO
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class MultiThreadShareRuntime {
    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun shareRuntime() = runTest {
        val quickJs = QuickJs.create(UnconfinedTestDispatcher())
        try {
            with(quickJs) {
                function("add") { it.first() as Long + it[1] as Long }
            }
            val jobs = List(20) {
                launch(Dispatchers.IO) {
                    assertEquals(3L, quickJs.evaluate("add(1, 2)"))
                }
            }
            jobs.joinAll()
        } finally {
            quickJs.close()
        }
    }
}