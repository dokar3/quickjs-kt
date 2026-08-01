package com.dokar.quickjs.test

import com.dokar.quickjs.ModuleContent
import com.dokar.quickjs.QuickJsException
import com.dokar.quickjs.binding.function
import com.dokar.quickjs.moduleLoader
import com.dokar.quickjs.quickJs
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class ModuleLoaderTest {
    @Test
    fun sourceModulesAreLoadedLazilyAndReportedAfterCompilation() = runTest {
        val sources = mapOf(
            "middle" to """
                import { value } from "leaf";
                export const result = value * 2;
            """.trimIndent(),
            "leaf" to "export const value = 21;",
            "unused" to "export const value = 100;",
        )
        val loaded = mutableListOf<String>()
        val compiled = linkedMapOf<String, ByteArray>()
        val loader = moduleLoader {
            load { name ->
                loaded += name
                sources[name]?.let(ModuleContent::Source)
            }
            onCompiled { name, bytecode -> compiled[name] = bytecode }
        }

        var captured = 0
        quickJs(moduleLoader = loader) {
            function("capture") { captured = (it.first() as Number).toInt() }
            evaluate<Any?>(
                code = "import { result } from 'middle'; capture(result);",
                filename = "entry",
                asModule = true,
            )
        }

        assertEquals(42, captured)
        assertEquals(listOf("middle", "leaf"), loaded)
        assertEquals(setOf("middle", "leaf"), compiled.keys)
        assertFalse("unused" in compiled)
    }

    @Test
    fun cachedBytecodeIsLoadedWithoutCompilationCallback() = runTest {
        val cached = quickJs {
            compile(
                code = "export const value = 42;",
                filename = "cached",
                asModule = true,
            )
        }
        var compilationCallbacks = 0
        val loader = moduleLoader {
            load { name ->
                if (name == "cached") ModuleContent.Bytecode(cached) else null
            }
            onCompiled { _, _ -> compilationCallbacks++ }
        }

        var captured = 0
        quickJs(moduleLoader = loader) {
            function("capture") { captured = (it.first() as Number).toInt() }
            evaluate<Any?>(
                "import { value } from 'cached'; capture(value);",
                asModule = true,
            )
        }

        assertEquals(42, captured)
        assertEquals(0, compilationCallbacks)
    }

    @Test
    fun sourceAndBytecodeModulesCanBeMixedInOneGraph() = runTest {
        val leafBytecode = quickJs {
            compile(
                code = "export const value = 21;",
                filename = "leaf",
                asModule = true,
            )
        }
        val compiled = linkedMapOf<String, ByteArray>()
        val loader = moduleLoader {
            load { name ->
                when (name) {
                    "middle" -> ModuleContent.Source(
                        "import { value } from 'leaf'; export const result = value * 2;"
                    )
                    "leaf" -> ModuleContent.Bytecode(leafBytecode)
                    else -> null
                }
            }
            onCompiled { name, bytecode -> compiled[name] = bytecode }
        }

        var captured = 0
        quickJs(moduleLoader = loader) {
            function("capture") { captured = (it.first() as Number).toInt() }
            evaluate<Any?>(
                "import { result } from 'middle'; capture(result);",
                asModule = true,
            )
        }

        assertEquals(42, captured)
        assertEquals(setOf("middle"), compiled.keys)
    }

    @Test
    fun resolveModuleGraphReturnsStaticGraphWithoutEvaluation() = runTest {
        val sources = mapOf(
            "middle" to """
                import { value } from "leaf";
                moduleExecuted();
                export const result = value * 2;
            """.trimIndent(),
            "leaf" to "export const value = 21;",
            "dynamic" to "export const value = 100;",
        )
        var executions = 0
        val loaded = mutableListOf<String>()
        val loader = moduleLoader {
            load { name ->
                loaded += name
                sources[name]?.let(ModuleContent::Source)
            }
        }

        quickJs(moduleLoader = loader) {
            function("moduleExecuted") { executions++ }
            val entry = compile(
                code = """
                    import { result } from "middle";
                    export { result };
                    export async function later() {
                        return (await import("dynamic")).value;
                    }
                """.trimIndent(),
                filename = "entry",
                asModule = true,
            )

            loaded.clear()
            val resolved = resolveModuleGraph(entry)

            assertEquals(setOf("entry", "middle", "leaf"), resolved)
            assertEquals(listOf("middle", "leaf"), loaded)
            assertEquals(0, executions)
            assertFalse("dynamic" in resolved)
        }
    }

    @Test
    fun graphResolutionCanPopulateCacheForLaterEvaluation() = runTest {
        val sources = mapOf(
            "parent" to "import { value } from 'leaf'; export { value };",
            "leaf" to "export const value = 42;",
        )
        val producer = moduleLoader {
            load { name -> sources[name]?.let(ModuleContent::Source) }
        }
        val entryBytecode = quickJs(moduleLoader = producer) {
            compile(
                "import { value } from 'parent'; capture(value);",
                filename = "entry",
                asModule = true,
            )
        }

        val cache = linkedMapOf<String, ByteArray>()
        var sourceLoads = 0
        val cachingLoader = moduleLoader {
            load { name ->
                cache[name]?.let(ModuleContent::Bytecode)
                    ?: sources[name]?.let {
                        sourceLoads++
                        ModuleContent.Source(it)
                    }
            }
            onCompiled { name, bytecode -> cache[name] = bytecode }
        }

        var captured = 0
        quickJs(moduleLoader = cachingLoader) {
            function("capture") { captured = (it.first() as Number).toInt() }

            assertEquals(
                setOf("entry", "parent", "leaf"),
                resolveModuleGraph(entryBytecode),
            )
            assertEquals(2, sourceLoads)
            assertEquals(setOf("parent", "leaf"), cache.keys)

            evaluate<Any?>(entryBytecode)
        }

        assertEquals(42, captured)
        assertEquals(2, sourceLoads)
    }

    @Test
    fun repeatedGraphResolutionReturnsTheCompleteGraph() = runTest {
        val sources = mapOf(
            "parent" to "import { value } from 'leaf'; export { value };",
            "leaf" to "export const value = 42;",
        )
        val loader = moduleLoader {
            load { name -> sources[name]?.let(ModuleContent::Source) }
        }

        quickJs(moduleLoader = loader) {
            val entry = compile(
                "import { value } from 'parent'; export { value };",
                filename = "entry",
                asModule = true,
            )

            val first = resolveModuleGraph(entry)
            val second = resolveModuleGraph(entry)

            assertEquals(setOf("entry", "parent", "leaf"), first)
            assertEquals(first, second)
        }
    }

    @Test
    fun successfullyCompiledSiblingIsReportedBeforeGraphFailure() = runTest {
        val completeLoader = moduleLoader {
            load { name ->
                when (name) {
                    "first" -> ModuleContent.Source("export const first = 20;")
                    "missing" -> ModuleContent.Source("export const second = 22;")
                    else -> null
                }
            }
        }
        val entryBytecode = quickJs(moduleLoader = completeLoader) {
            compile(
                code = """
                    import { first } from "first";
                    import { second } from "missing";
                    export const result = first + second;
                """.trimIndent(),
                filename = "entry",
                asModule = true,
            )
        }

        val compiledBeforeFailure = linkedMapOf<String, ByteArray>()
        val incompleteLoader = moduleLoader {
            load { name ->
                if (name == "first") {
                    ModuleContent.Source("export const first = 20;")
                } else {
                    null
                }
            }
            onCompiled { name, bytecode -> compiledBeforeFailure[name] = bytecode }
        }

        quickJs(moduleLoader = incompleteLoader) {
            assertFailsWith<QuickJsException> {
                resolveModuleGraph(entryBytecode)
            }
        }

        assertEquals(setOf("first"), compiledBeforeFailure.keys)
        assertTrue(compiledBeforeFailure.getValue("first").isNotEmpty())
    }

    @Test
    fun dynamicImportUsesTheRuntimeLoaderAndReportsCompiledBytecode() = runTest {
        val compiled = linkedMapOf<String, ByteArray>()
        val loader = moduleLoader {
            load { name ->
                when (name) {
                    "base" -> ModuleContent.Source("export const value = 20;")
                    "dynamic" -> ModuleContent.Source("export const value = 22;")
                    else -> null
                }
            }
            onCompiled { name, bytecode -> compiled[name] = bytecode }
        }

        var captured = 0
        quickJs(moduleLoader = loader) {
            function("capture") { captured = (it.first() as Number).toInt() }
            evaluate<Any?>(
                code = """
                    import { value } from "base";
                    const loaded = await import("dynamic");
                    capture(value + loaded.value);
                """.trimIndent(),
                filename = "entry",
                asModule = true,
            )
        }

        assertEquals(42, captured)
        assertEquals(setOf("base", "dynamic"), compiled.keys)
    }

    @Test
    fun circularDependenciesAreLoadedAndCompiledOnce() = runTest {
        val loadCounts = mutableMapOf<String, Int>()
        val compileCounts = mutableMapOf<String, Int>()
        val loader = moduleLoader {
            load { name ->
                loadCounts[name] = loadCounts.getOrElse(name) { 0 } + 1
                when (name) {
                    "cycle-a" -> ModuleContent.Source(
                        """
                            import { fromB } from "cycle-b";
                            export function fromA() { return 20; }
                            export function total() { return fromA() + fromB(); }
                        """.trimIndent()
                    )
                    "cycle-b" -> ModuleContent.Source(
                        """
                            import { fromA } from "cycle-a";
                            export function fromB() { return fromA() + 2; }
                        """.trimIndent()
                    )
                    else -> null
                }
            }
            onCompiled { name, _ ->
                compileCounts[name] = compileCounts.getOrElse(name) { 0 } + 1
            }
        }

        var captured = 0
        quickJs(moduleLoader = loader) {
            function("capture") { captured = (it.first() as Number).toInt() }
            evaluate<Any?>(
                "import { total } from 'cycle-a'; capture(total());",
                asModule = true,
            )
        }

        assertEquals(42, captured)
        assertEquals(mapOf("cycle-a" to 1, "cycle-b" to 1), loadCounts)
        assertEquals(mapOf("cycle-a" to 1, "cycle-b" to 1), compileCounts)
    }

    @Test
    fun missingModuleFailsWithItsNormalizedName() = runTest {
        val loader = moduleLoader { load { null } }

        val failure = quickJs(moduleLoader = loader) {
            assertFailsWith<QuickJsException> {
                evaluate<Any?>(
                    "import 'missing';",
                    filename = "entry",
                    asModule = true,
                )
            }
        }

        assertContains(failure.message.orEmpty(), "missing")
    }

    @Test
    fun corruptAndMismatchedBytecodeAreRejected() = runTest {
        val actualModule = quickJs {
            compile(
                "export const value = 42;",
                filename = "actual",
                asModule = true,
            )
        }

        val corruptLoader = moduleLoader {
            load { ModuleContent.Bytecode(byteArrayOf(0, 1, 2, 3)) }
        }
        quickJs(moduleLoader = corruptLoader) {
            assertFailsWith<QuickJsException> {
                evaluate<Any?>("import 'corrupt';", asModule = true)
            }
        }

        val mismatchedLoader = moduleLoader {
            load { ModuleContent.Bytecode(actualModule) }
        }
        val mismatch = quickJs(moduleLoader = mismatchedLoader) {
            assertFailsWith<QuickJsException> {
                evaluate<Any?>("import 'expected';", asModule = true)
            }
        }
        assertContains(mismatch.message.orEmpty(), "expected")
    }

    @Test
    fun invalidatedSourceIsRecompiledAndThenReusableAsBytecode() = runTest {
        val versionOne = quickJs {
            compile(
                "export const value = 21;",
                filename = "value",
                asModule = true,
            )
        }
        val refreshed = linkedMapOf<String, ByteArray>()
        val sourceLoader = moduleLoader {
            load { name ->
                if (name == "value") {
                    ModuleContent.Source("export const value = 42;")
                } else {
                    null
                }
            }
            onCompiled { name, bytecode -> refreshed[name] = bytecode }
        }

        var sourceResult = 0
        quickJs(moduleLoader = sourceLoader) {
            function("capture") { sourceResult = (it.first() as Number).toInt() }
            evaluate<Any?>(
                "import { value } from 'value'; capture(value);",
                asModule = true,
            )
        }

        val versionTwo = refreshed.getValue("value")
        assertEquals(42, sourceResult)
        assertNotEquals(versionOne.toList(), versionTwo.toList())

        var bytecodeResult = 0
        var recompilations = 0
        val bytecodeLoader = moduleLoader {
            load { name ->
                if (name == "value") ModuleContent.Bytecode(versionTwo) else null
            }
            onCompiled { _, _ -> recompilations++ }
        }
        quickJs(moduleLoader = bytecodeLoader) {
            function("capture") { bytecodeResult = (it.first() as Number).toInt() }
            evaluate<Any?>(
                "import { value } from 'value'; capture(value);",
                asModule = true,
            )
        }

        assertEquals(42, bytecodeResult)
        assertEquals(0, recompilations)
    }

    @Test
    fun relativeImportsArePassedToLoaderAsNormalizedNames() = runTest {
        val requested = mutableListOf<String>()
        val loader = moduleLoader {
            load { name ->
                requested += name
                if (name == "app/value.js") {
                    ModuleContent.Source("export const value = 42;")
                } else {
                    null
                }
            }
        }

        var captured = 0
        quickJs(moduleLoader = loader) {
            function("capture") { captured = (it.first() as Number).toInt() }
            evaluate<Any?>(
                "import { value } from './value.js'; capture(value);",
                filename = "app/entry.js",
                asModule = true,
            )
        }

        assertEquals(42, captured)
        assertEquals(listOf("app/value.js"), requested)
    }

    @Test
    fun loaderAndCompilationCallbackExceptionsFailTheOperation() = runTest {
        val loadFailure = moduleLoader {
            load { throw IllegalStateException("load failed") }
        }
        val loadError = quickJs(moduleLoader = loadFailure) {
            assertFails {
                evaluate<Any?>("import 'dependency';", asModule = true)
            }
        }
        assertContains(loadError.message.orEmpty(), "load failed")

        val compileError = quickJs(moduleLoader = loadFailure) {
            assertFails {
                compile(
                    "import 'dependency';",
                    filename = "compile-entry",
                    asModule = true,
                )
            }
        }
        assertContains(compileError.message.orEmpty(), "load failed")

        val callbackFailure = moduleLoader {
            load { ModuleContent.Source("export const value = 42;") }
            onCompiled { _, _ -> throw IllegalStateException("persistence handoff failed") }
        }
        val callbackError = quickJs(moduleLoader = callbackFailure) {
            assertFails {
                evaluate<Any?>("import 'dependency';", asModule = true)
            }
        }
        assertContains(callbackError.message.orEmpty(), "persistence handoff failed")
    }

    @Test
    fun invalidSourceCanBeCorrectedBeforeSuccessfulResolution() = runTest {
        var source = "export const value = ;"
        val loader = moduleLoader {
            load { name ->
                if (name == "recoverable") ModuleContent.Source(source) else null
            }
        }

        var captured = 0
        quickJs(moduleLoader = loader) {
            function("capture") { captured = (it.first() as Number).toInt() }
            assertFailsWith<QuickJsException> {
                evaluate<Any?>(
                    "import { value } from 'recoverable'; capture(value);",
                    filename = "failed-entry",
                    asModule = true,
                )
            }

            source = "export const value = 42;"
            evaluate<Any?>(
                "import { value } from 'recoverable'; capture(value);",
                filename = "recovered-entry",
                asModule = true,
            )
        }

        assertEquals(42, captured)
    }

    @Test
    fun loadedModuleKeepsItsInstanceUntilTheRuntimeIsRecreated() = runTest {
        var source = "export const value = 21;"
        var loads = 0
        val loader = moduleLoader {
            load { name ->
                if (name == "stable") {
                    loads++
                    ModuleContent.Source(source)
                } else {
                    null
                }
            }
        }

        val results = mutableListOf<Int>()
        quickJs(moduleLoader = loader) {
            function("capture") {
                results += (it.first() as Number).toInt()
            }
            evaluate<Any?>(
                "import { value } from 'stable'; capture(value);",
                filename = "first-entry",
                asModule = true,
            )

            source = "export const value = 42;"
            evaluate<Any?>(
                "import { value } from 'stable'; capture(value);",
                filename = "second-entry",
                asModule = true,
            )
        }

        assertEquals(listOf(21, 21), results)
        assertEquals(1, loads)

        quickJs(moduleLoader = loader) {
            function("capture") {
                results += (it.first() as Number).toInt()
            }
            evaluate<Any?>(
                "import { value } from 'stable'; capture(value);",
                filename = "new-runtime-entry",
                asModule = true,
            )
        }

        assertEquals(listOf(21, 21, 42), results)
        assertEquals(2, loads)
    }
}
