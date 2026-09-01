package com.dokar.quickjs.test

import com.dokar.quickjs.ModuleContent
import com.dokar.quickjs.QuickJsException
import com.dokar.quickjs.binding.define
import com.dokar.quickjs.binding.function
import com.dokar.quickjs.binding.toJsObject
import com.dokar.quickjs.moduleLoader
import com.dokar.quickjs.quickJs
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * Verifies that valid Unicode survives every bridge path between Kotlin and QuickJS.
 */
class UnicodeStringTest {
    private val unicodeText = "ASCII 中文 e\u0301 😀 👩‍💻"

    /** Verifies source, result, supplementary-plane identifiers, and bytecode. */
    @Test
    fun evaluateAndBytecodePreserveUnicode() = runTest {
        quickJs {
            assertEquals(unicodeText, evaluate<String>("'$unicodeText'"))
            assertEquals(42, evaluate<Int>("const 𐐀 = 42; 𐐀"))

            val bytecode = compile("'$unicodeText'")
            assertEquals(unicodeText, evaluate<String>(bytecode))
        }
    }

    /** Verifies binding names, property names, arguments, results, and nested object keys. */
    @Test
    fun bindingsAndObjectsPreserveUnicode() = runTest {
        quickJs {
            function("函数😀") { args -> args.single() }
            define("对象😀") {
                property("属性😀") {
                    getter { unicodeText }
                }
                function("回声😀") { args -> args.single() }
            }
            function("返回对象😀") {
                mapOf("键😀" to unicodeText).toJsObject()
            }

            assertEquals(
                unicodeText,
                evaluate<String>("globalThis['函数😀']('$unicodeText')"),
            )
            assertEquals(
                unicodeText,
                evaluate<String>("globalThis['对象😀']['属性😀']"),
            )
            assertEquals(
                unicodeText,
                evaluate<String>("globalThis['对象😀']['回声😀']('$unicodeText')"),
            )
            val result = evaluate<Map<String, Any?>>("globalThis['返回对象😀']()")
            assertEquals(unicodeText, result["键😀"])
        }
    }

    /** Verifies Unicode propagation in JavaScript and Kotlin exception messages. */
    @Test
    fun exceptionsPreserveUnicode() = runTest {
        quickJs {
            val jsError = assertFailsWith<QuickJsException> {
                evaluate<Any?>("throw new Error('$unicodeText')")
            }
            assertContains(jsError.message.orEmpty(), unicodeText)

            function("抛出😀") { error(unicodeText) }
            val kotlinError = assertFailsWith<IllegalStateException> {
                evaluate<Any?>("globalThis['抛出😀']()")
            }
            assertEquals(unicodeText, kotlinError.message)
        }
    }

    /** Verifies Unicode in module names, module source, and compilation callbacks. */
    @Test
    fun moduleLoaderPreservesUnicode() = runTest {
        val moduleName = "模块😀"
        val loaded = mutableListOf<String>()
        val compiled = mutableListOf<String>()
        val loader = moduleLoader {
            load { name ->
                loaded += name
                if (name == moduleName) {
                    ModuleContent.Source("export const value = '$unicodeText';")
                } else {
                    null
                }
            }
            onCompiled { name, _ -> compiled += name }
        }

        var result: String? = null
        quickJs(moduleLoader = loader) {
            function("捕获😀") { args -> result = args.single() as String }
            evaluate<Any?>(
                code = """
                    import { value } from '$moduleName';
                    globalThis['捕获😀'](value);
                """.trimIndent(),
                filename = "入口😀",
                asModule = true,
            )
        }

        assertEquals(unicodeText, result)
        assertEquals(listOf(moduleName), loaded)
        assertEquals(listOf(moduleName), compiled)
    }
}
