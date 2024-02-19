package com.dokar.quickjs.benchmark

import com.dokar.quickjs.QuickJs
import com.dokar.quickjs.binding.define
import kotlinx.benchmark.Benchmark
import kotlinx.benchmark.Scope
import kotlinx.benchmark.Setup
import kotlinx.benchmark.State
import kotlinx.benchmark.TearDown

@Suppress("unused")
@State(Scope.Benchmark)
class DefineBindingsBenchmark {
    private lateinit var quickJs: QuickJs

    @Setup
    fun setup() {
        quickJs = QuickJs.create()
    }

    @TearDown
    fun cleanup() {
        quickJs.close()
    }

    @Benchmark
    fun defineDslBindings() {
        quickJs.define("http") {
            asyncFunction("fetch") {}
        }
        quickJs.define("console") {
            function("debug") {}
            function("log") {}
            function("info") {}
            function("warn") {}
            function("error") {}
        }
        quickJs.define("app") {
            property("name") {
                getter { "MyApp" }
            }

            property("version") {
                getter { "1.0.0" }
            }

            function("run") {}
        }
    }
}