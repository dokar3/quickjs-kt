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

    @Benchmark
    fun defineReflectionBindings() {
        quickJs.define("http", Http::class.java, Http())
        quickJs.define("console", Console::class.java, Console())
        quickJs.define("app", App::class.java, App())
    }

    private class Http {
        suspend fun fetch() {}
    }

    private class Console {
        fun debug() {}
        fun log() {}
        fun info() {}
        fun warn() {}
        fun error() {}
    }

    private class App {
        val version: String = "1.0.0"
        val name: String = "MyApp"

        fun run() {}
    }
}