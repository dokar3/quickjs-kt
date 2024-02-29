package com.dokar.quickjs.benchmark

import com.dokar.quickjs.QuickJs
import com.dokar.quickjs.binding.define
import kotlinx.benchmark.Benchmark
import kotlinx.benchmark.Scope
import kotlinx.benchmark.Setup
import kotlinx.benchmark.State
import kotlinx.benchmark.TearDown
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking

@Suppress("unused")
@State(Scope.Benchmark)
class InvokeBindingsBenchmark {
    private lateinit var quickJs: QuickJs

    @Setup
    fun setup() {
        quickJs = QuickJs.create(Dispatchers.Default)
        quickJs.define("dslConsole") {
            property("level") {
                getter { "Debug" }
            }

            function("log") {}
        }
        quickJs.define("reflectionConsole", Console::class.java, Console())
    }

    @TearDown
    fun cleanup() {
        quickJs.close()
    }

    @Benchmark
    fun invokeDslBindings() = runBlocking {
        quickJs.evaluate<Any?>("dslConsole.level")
        quickJs.evaluate<Any?>("dslConsole.log()")
    }

    @Benchmark
    fun invokeReflectionBindings() = runBlocking {
        quickJs.evaluate<Any?>("reflectionConsole.level")
        quickJs.evaluate<Any?>("reflectionConsole.log()")
    }

    private class Console {
        val level = "Debug"

        fun log() {}
    }
}