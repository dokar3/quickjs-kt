package com.dokar.quickjs.test

import com.dokar.quickjs.QuickJs
import com.dokar.quickjs.quickJs
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFails

class ModulesTest {
    @Test
    fun codeAsModule() = runTest {
        quickJs {
            setupHelloModule()

            val result = evaluate<String>(
                """
                        import * as hello from "hello";
                        returns(hello.greeting());
                    """.trimIndent(),
                asModule = true,
            )
            assertEquals("Hi from the hello module!", result)
        }
    }

    @Test
    fun bytecodeAsModule() = runTest {
        quickJs {
            setupHelloModuleBytecode()

            val result = evaluate<String>(
                """
                        import * as hello from "hello";
                        returns(hello.greeting());
                    """.trimIndent(),
                asModule = true,
            )
            assertEquals("Hi from the hello module!", result)
        }
    }

    @Test
    fun withNoReturns() = runTest {
        quickJs {
            setupHelloModule()

            val result = evaluate<String?>(
                """
                        import * as hello from "hello";
                        hello.greeting();
                    """.trimIndent(),
                asModule = true,
            )
            assertEquals(null, result)
        }
    }

    @Test
    fun nonModuleBytecodeAsModule(): Unit = runTest {
        quickJs {
            val helloModuleCode = """
                function greeting() {
                    return "Hi from the hello module!";
                }
            """.trimIndent()
            val bytecode = compile(
                code = helloModuleCode,
                filename = "hello",
                asModule = false,
            )
            addModule(bytecode)

            assertFails {
                evaluate<String>(
                    """
                        import * as hello from "hello";
                        returns(hello.greeting());
                    """.trimIndent(),
                    asModule = true,
                )
            }.also {
                assertContains(it.message!!, "ReferenceError: could not load module 'hello'")
            }
        }
    }

    private fun QuickJs.setupHelloModule() {
        val helloModuleCode = """
                export function greeting() {
                    return "Hi from the hello module!";
                }
            """.trimIndent()
        addModule(name = "hello", code = helloModuleCode)
    }

    private fun QuickJs.setupHelloModuleBytecode() {
        val helloModuleCode = """
                export function greeting() {
                    return "Hi from the hello module!";
                }
            """.trimIndent()
        val bytecode = compile(
            code = helloModuleCode,
            filename = "hello",
            asModule = true,
        )
        addModule(bytecode)
    }
}