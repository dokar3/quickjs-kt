package com.dokar.quickjs.benchmark

import com.dokar.quickjs.QuickJs
import com.dokar.quickjs.binding.define
import kotlinx.benchmark.Benchmark
import kotlinx.benchmark.Scope
import kotlinx.benchmark.State

@Suppress("unused")
@State(Scope.Benchmark)
class DefineBindingsBenchmark {
    @Benchmark
    fun defineDslBindings() {
        val quickJs = QuickJs.create()
        try {
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
            quickJs.close()
        } finally {
            quickJs.close()
        }
    }
}