package com.dokar.quickjs.test

import com.dokar.quickjs.ModuleContent
import com.dokar.quickjs.ModuleImportType
import com.dokar.quickjs.ModuleLoader
import com.dokar.quickjs.QuickJsException
import com.dokar.quickjs.QuickJsInterruptedException
import com.dokar.quickjs.binding.function
import com.dokar.quickjs.moduleLoader
import com.dokar.quickjs.quickJs
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

@OptIn(ExperimentalUnsignedTypes::class)
class ModuleImportTypeTest {
    @Test
    fun staticImportsSupportJsTextJsonAndBytes() = runTest {
        val loaded = mutableListOf<Pair<String, ModuleImportType>>()
        val compiled = mutableListOf<String>()
        val payload = byteArrayOf(0, 1, 2, 127, -128, -1)

        val loader = moduleLoader {
            load { name, type ->
                loaded += name to type
                when (name to type) {
                    "math.js" to ModuleImportType.Js -> ModuleContent.Source("export const factor = 2;")
                    "greeting.txt" to ModuleImportType.Text -> ModuleContent.Text("Hello, world!")
                    "config.json" to ModuleImportType.Json -> ModuleContent.Json("""{"count": 21}""")
                    "data.bin" to ModuleImportType.Bytes -> ModuleContent.Bytes(payload)
                    else -> null
                }
            }
            onCompiled { name, _ -> compiled += name }
        }

        var text: String? = null
        var product = 0
        var isUint8Array = false
        var bytes: UByteArray? = null

        quickJs(moduleLoader = loader) {
            function("capture") { args ->
                text = args[0] as String
                product = (args[1] as Number).toInt()
                isUint8Array = args[2] as Boolean
                bytes = args[3] as UByteArray
            }

            evaluate<Any?>(
                code = """
                    import { factor } from "math.js";
                    import greeting from "greeting.txt" with { type: "text" };
                    import config from "config.json" with { type: "json" };
                    import data from "data.bin" with { type: "bytes" };
                    capture(greeting, config.count * factor, data instanceof Uint8Array, data);
                """.trimIndent(),
                filename = "entry.js",
                asModule = true,
            )
        }

        assertEquals("Hello, world!", text)
        assertEquals(42, product)
        assertTrue(isUint8Array)
        assertContentEquals(payload.asUByteArray(), bytes)
        assertEquals(
            listOf(
                "math.js" to ModuleImportType.Js,
                "greeting.txt" to ModuleImportType.Text,
                "config.json" to ModuleImportType.Json,
                "data.bin" to ModuleImportType.Bytes,
            ),
            loaded,
        )
        assertEquals(listOf("math.js"), compiled)
    }

    @Test
    fun dynamicImportsSupportTextJsonAndBytes() = runTest {
        val loader = ModuleLoader { name, type ->
            when (name to type) {
                "note.txt" to ModuleImportType.Text -> ModuleContent.Text("dynamic text")
                "cfg.json" to ModuleImportType.Json -> ModuleContent.Json("""{"answer": 42}""")
                "raw.bin" to ModuleImportType.Bytes -> ModuleContent.Bytes(byteArrayOf(9, 8, 7))
                else -> null
            }
        }

        var text: String? = null
        var answer = 0
        var bytes: UByteArray? = null

        quickJs(moduleLoader = loader) {
            function("capture") { args ->
                text = args[0] as String
                answer = (args[1] as Number).toInt()
                bytes = args[2] as UByteArray
            }

            evaluate<Any?>(
                code = """
                    const textMod = await import("note.txt", { with: { type: "text" } });
                    const jsonMod = await import("cfg.json", { with: { type: "json" } });
                    const bytesMod = await import("raw.bin", { with: { type: "bytes" } });
                    capture(textMod.default, jsonMod.default.answer, bytesMod.default);
                """.trimIndent(),
                filename = "entry.js",
                asModule = true,
            )
        }

        assertEquals("dynamic text", text)
        assertEquals(42, answer)
        assertContentEquals(ubyteArrayOf(9u, 8u, 7u), bytes)
    }

    @Test
    fun sameModuleNameCachesIndependentlyPerImportType() = runTest {
        val loadCounts = mutableMapOf<Pair<String, ModuleImportType>, Int>()
        val rawJson = """{"v":7}"""
        val loader = ModuleLoader { name, type ->
            val key = name to type
            loadCounts[key] = loadCounts.getOrElse(key) { 0 } + 1
            if (name != "asset.json") return@ModuleLoader null
            when (type) {
                ModuleImportType.Json -> ModuleContent.Json(rawJson)
                ModuleImportType.Text -> ModuleContent.Text(rawJson)
                ModuleImportType.Bytes -> ModuleContent.Bytes(rawJson.encodeToByteArray())
                ModuleImportType.Js -> ModuleContent.Source("export default 'js';")
            }
        }

        var result = ""
        quickJs(moduleLoader = loader) {
            function("capture") { result = it.single() as String }
            evaluate<Any?>(
                code = """
                    import parsed1 from "asset.json" with { type: "json" };
                    import parsed2 from "asset.json" with { type: "json" };
                    import text from "asset.json" with { type: "text" };
                    import bytes from "asset.json" with { type: "bytes" };
                    import jsVal from "asset.json";
                    capture([parsed1 === parsed2, parsed1.v, text, bytes.length, jsVal].join("|"));
                """.trimIndent(),
                filename = "entry.js",
                asModule = true,
            )
        }

        assertEquals("true|7|$rawJson|${rawJson.length}|js", result)
        assertEquals(
            mapOf(
                ("asset.json" to ModuleImportType.Json) to 1,
                ("asset.json" to ModuleImportType.Text) to 1,
                ("asset.json" to ModuleImportType.Bytes) to 1,
                ("asset.json" to ModuleImportType.Js) to 1,
            ),
            loadCounts,
        )
    }

    @Test
    fun bytesModuleIsImmutable() = runTest {
        val loader = ModuleLoader { name, type ->
            if (name == "secret.bin" && type == ModuleImportType.Bytes) {
                ModuleContent.Bytes(byteArrayOf(1, 2, 3))
            } else {
                null
            }
        }

        var result = ""
        quickJs(moduleLoader = loader) {
            function("capture") { result = it.single() as String }
            evaluate<Any?>(
                code = """
                    import bytes from "secret.bin" with { type: "bytes" };
                    let transferErr = false;
                    try {
                        bytes.buffer.transfer();
                    } catch (e) {
                        transferErr = e instanceof TypeError;
                    }
                    capture([
                        bytes.buffer.immutable,
                        Reflect.set(bytes, 0, 99),
                        transferErr,
                        bytes[0] === 1,
                    ].join("|"));
                """.trimIndent(),
                filename = "entry.js",
                asModule = true,
            )
        }

        assertEquals("true|false|true|true", result)
    }

    @Test
    fun textJsonAndBytesPreserveEmptyEmbeddedNulAndUnicode() = runTest {
        val unicode = "ASCII 中文 e\u0301 😀"
        val loader = ModuleLoader { name, type ->
            when (name to type) {
                "empty.txt" to ModuleImportType.Text -> ModuleContent.Text("")
                "nul.txt" to ModuleImportType.Text -> ModuleContent.Text("a\u0000b")
                "unicode.txt" to ModuleImportType.Text -> ModuleContent.Text(unicode)
                "unicode.json" to ModuleImportType.Json -> ModuleContent.Json("\"$unicode\"")
                "empty.bin" to ModuleImportType.Bytes -> ModuleContent.Bytes(ByteArray(0))
                else -> null
            }
        }

        var result = ""
        quickJs(moduleLoader = loader) {
            function("capture") { result = it.single() as String }
            evaluate<Any?>(
                code = """
                    import emptyText from "empty.txt" with { type: "text" };
                    import nulText from "nul.txt" with { type: "text" };
                    import uniText from "unicode.txt" with { type: "text" };
                    import uniJson from "unicode.json" with { type: "json" };
                    import emptyBytes from "empty.bin" with { type: "bytes" };
                    capture([
                        emptyText.length,
                        nulText.length,
                        nulText.charCodeAt(1),
                        uniText === uniJson,
                        emptyBytes.length,
                    ].join("|"));
                """.trimIndent(),
                filename = "entry.js",
                asModule = true,
            )
        }

        assertEquals("0|3|0|true|0", result)
    }

    @Test
    fun invalidJsonFailsWithSyntaxErrorAndNotifiesLoadFailed() = runTest {
        val failed = mutableListOf<Pair<String, ModuleImportType>>()
        val payloads = mapOf(
            "broken.json" to "{ invalid",
            "json5.json" to "{ a: 1, }",
            "nul.json" to "123\u0000garbage",
        )
        val loader = moduleLoader {
            load { name, type ->
                if (type == ModuleImportType.Json) payloads[name]?.let(ModuleContent::Json) else null
            }
            onLoadFailed { name, type -> failed += name to type }
        }

        quickJs(moduleLoader = loader) {
            for (name in payloads.keys) {
                val error = assertFailsWith<QuickJsException> {
                    evaluate<Any?>(
                        code = "import v from '$name' with { type: 'json' };",
                        filename = "entry-$name.js",
                        asModule = true,
                    )
                }
                assertContains(error.message.orEmpty(), "SyntaxError")
            }
        }

        assertEquals(
            payloads.keys.map { it to ModuleImportType.Json },
            failed,
        )
    }

    @Test
    fun unsupportedImportAttributesAndTypesFailBeforeLoading() = runTest {
        var loads = 0
        val failed = mutableListOf<Pair<String, ModuleImportType>>()
        val loader = moduleLoader {
            load { _, _ ->
                loads++
                ModuleContent.Source("export default 1;")
            }
            onLoadFailed { name, type -> failed += name to type }
        }

        quickJs(moduleLoader = loader) {
            val badKey = assertFailsWith<QuickJsException> {
                compile("import x from 'a' with { foo: 'bar' };", asModule = true)
            }
            assertContains(badKey.message.orEmpty(), "TypeError")

            for (badType in listOf("xml", "js")) {
                val err = assertFailsWith<QuickJsException> {
                    evaluate<Any?>("import x from 'a' with { type: '$badType' };", asModule = true)
                }
                assertContains(err.message.orEmpty(), "TypeError")
            }

            var dynamicTypeErrors = 0
            function("mark") { if (it.single() == true) dynamicTypeErrors++ }
            evaluate<Any?>(
                code = """
                    for (const opts of [{ with: { foo: "bar" } }, { with: { type: "xml" } }]) {
                        try { await import("a", opts); } catch (e) { mark(e instanceof TypeError); }
                    }
                """.trimIndent(),
                filename = "dyn.js",
                asModule = true,
            )
            assertEquals(2, dynamicTypeErrors)
        }

        // Unsupported attribute keys are also rejected without a ModuleLoader.
        quickJs {
            val err = assertFailsWith<QuickJsException> {
                compile("import x from 'a' with { foo: 'bar' };", asModule = true)
            }
            assertContains(err.message.orEmpty(), "TypeError")
        }

        assertEquals(0, loads)
        assertTrue(failed.isEmpty())
    }

    @Test
    fun mismatchedModuleContentThrowsTypeErrorAndNotifiesFailure() = runTest {
        val cases = listOf(
            Triple("js-bad", "", ModuleImportType.Js) to ModuleContent.Text("not js"),
            Triple("text-bad", "with { type: 'text' }", ModuleImportType.Text) to ModuleContent.Source("export default 1;"),
            Triple("json-bad", "with { type: 'json' }", ModuleImportType.Json) to ModuleContent.Text("{}"),
            Triple("bytes-bad", "with { type: 'bytes' }", ModuleImportType.Bytes) to ModuleContent.Bytecode(byteArrayOf(1)),
        )
        val contentByName = cases.associate { (spec, content) -> spec.first to content }
        val failed = mutableListOf<Pair<String, ModuleImportType>>()
        val loader = moduleLoader {
            load { name, _ -> contentByName[name] }
            onLoadFailed { name, type -> failed += name to type }
        }

        quickJs(moduleLoader = loader) {
            for ((spec, _) in cases) {
                val (name, withClause, _) = spec
                val err = assertFailsWith<QuickJsException> {
                    evaluate<Any?>("import v from '$name' $withClause;", asModule = true)
                }
                assertContains(err.message.orEmpty(), "TypeError")
            }
        }

        assertEquals(
            cases.map { (spec, _) -> spec.first to spec.third },
            failed,
        )
    }

    @Test
    fun bytecodeCompilationAndGraphResolutionPreserveImportTypes() = runTest {
        val compiled = mutableListOf<String>()
        val loader = moduleLoader {
            load { name, type ->
                when (name to type) {
                    "helper.js" to ModuleImportType.Js -> ModuleContent.Source(
                        """
                        import cfg from "cfg.json" with { type: "json" };
                        import label from "label.txt" with { type: "text" };
                        import raw from "raw.bin" with { type: "bytes" };
                        export const summary = label + ":" + cfg.value + ":" + raw[0];
                        """.trimIndent()
                    )
                    "cfg.json" to ModuleImportType.Json -> ModuleContent.Json("""{"value": 42}""")
                    "label.txt" to ModuleImportType.Text -> ModuleContent.Text("ok")
                    "raw.bin" to ModuleImportType.Bytes -> ModuleContent.Bytes(byteArrayOf(7))
                    else -> null
                }
            }
            onCompiled { name, _ -> compiled += name }
        }

        val entryBytecode = quickJs(moduleLoader = loader) {
            compile(
                code = """
                    import { summary } from "helper.js";
                    capture(summary);
                """.trimIndent(),
                filename = "entry.js",
                asModule = true,
            )
        }
        compiled.clear()

        var captured: String? = null
        quickJs(moduleLoader = loader) {
            function("capture") { captured = it.single() as String }
            assertEquals(
                setOf("entry.js", "helper.js", "cfg.json", "label.txt", "raw.bin"),
                resolveModuleGraph(entryBytecode),
            )
            assertEquals(listOf("helper.js"), compiled)
            evaluate<Any?>(entryBytecode)
        }

        assertEquals("ok:42:7", captured)
    }

    @Test
    @Suppress("DEPRECATION")
    fun deprecatedSingleArgLoaderHandlesJsOnlyAndRejectsOtherTypes() = runTest {
        var calls = 0
        val failedNames = mutableListOf<String>()
        val loader = moduleLoader {
            load { name ->
                calls++
                if (name == "mod.js") ModuleContent.Source("export const v = 42;") else null
            }
            onLoadFailed { name -> failedNames += name }
        }

        var captured = 0
        quickJs(moduleLoader = loader) {
            function("capture") { captured = (it.single() as Number).toInt() }
            evaluate<Any?>("import { v } from 'mod.js'; capture(v);", asModule = true)
            assertFailsWith<QuickJsException> {
                evaluate<Any?>("import t from 'mod.js' with { type: 'text' };", asModule = true)
            }
        }

        assertEquals(42, captured)
        assertEquals(1, calls)
        assertEquals(listOf("mod.js"), failedNames)
    }

    @Test
    fun interruptRecoveryRestoresImportTypeLoader() = runTest {
        val loader = ModuleLoader { name, type ->
            if (name == "cfg.json" && type == ModuleImportType.Json) {
                ModuleContent.Json("""{"ok": true}""")
            } else {
                null
            }
        }

        var recovered = false
        quickJs(moduleLoader = loader) {
            function("capture") { recovered = it.single() as Boolean }
            evaluationTimeoutMillis = 50L
            assertFailsWith<QuickJsInterruptedException> {
                // Queue a pending job so interrupt cleanup swaps and restores the module loader.
                evaluate<Any?>("Promise.resolve().then(() => {}); while (true) {}")
            }
            evaluationTimeoutMillis = 0L

            evaluate<Any?>(
                code = """
                    import cfg from "cfg.json" with { type: "json" };
                    capture(cfg.ok);
                """.trimIndent(),
                filename = "after-interrupt.js",
                asModule = true,
            )
        }
        assertTrue(recovered)

        // Also verify attribute checking is restored on runtimes without a ModuleLoader.
        quickJs {
            evaluationTimeoutMillis = 50L
            assertFailsWith<QuickJsInterruptedException> {
                evaluate<Any?>("Promise.resolve().then(() => {}); while (true) {}")
            }
            evaluationTimeoutMillis = 0L

            val err = assertFailsWith<QuickJsException> {
                compile("import x from 'a' with { foo: 'bar' };", asModule = true)
            }
            assertContains(err.message.orEmpty(), "TypeError")
        }
    }
}
