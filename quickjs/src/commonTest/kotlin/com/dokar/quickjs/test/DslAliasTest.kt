package com.dokar.quickjs.test

import com.dokar.quickjs.ExperimentalQuickJsApi
import com.dokar.quickjs.alias.asyncFunc
import com.dokar.quickjs.alias.def
import com.dokar.quickjs.alias.eval
import com.dokar.quickjs.alias.func
import com.dokar.quickjs.alias.prop
import com.dokar.quickjs.quickJs
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class DslAliasTest {
    @Test
    fun useDslAlias() = runTest {
        @OptIn(ExperimentalQuickJsApi::class)
        quickJs {
            var log: Any? = null

            def("console") {
                prop("level") {
                    getter { "Debug" }
                }

                func("log") { log = it.firstOrNull() }

                asyncFunc("upload") {
                    delay(1000)
                    "Done"
                }
            }

            func("fetch") { "Hello" }

            asyncFunc("delay") { delay(1000) }

            assertEquals("Debug", eval("console.level"))

            eval<Any?>("console.log('Hi')")
            assertEquals("Hi", log)

            assertEquals("Done", eval("await console.upload()"))

            assertEquals("Hello", eval("fetch()"))

            val bytecode = compile(code = "fetch()")
            assertEquals("Hello", eval(bytecode))
        }
    }
}