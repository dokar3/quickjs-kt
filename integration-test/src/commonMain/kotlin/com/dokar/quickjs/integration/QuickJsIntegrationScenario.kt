package com.dokar.quickjs.integration

import com.dokar.quickjs.MemoryUsage
import com.dokar.quickjs.QuickJs
import com.dokar.quickjs.binding.JsObject
import com.dokar.quickjs.binding.asyncFunction
import com.dokar.quickjs.binding.define
import com.dokar.quickjs.binding.function
import com.dokar.quickjs.binding.toJsObject
import com.dokar.quickjs.converter.JsObjectConverter
import com.dokar.quickjs.quickJs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlin.reflect.KType
import kotlin.reflect.typeOf

suspend fun runQuickJsIntegrationScenario() {
    exerciseQuickJsDsl()
    exerciseQuickJsLifecycle()
}

private suspend fun exerciseQuickJsDsl() {
    quickJs(Dispatchers.Default) {
        expect(version.isNotBlank(), "QuickJS version should not be blank.")

        memoryLimit = 8L * 1024L * 1024L
        maxStackSize = 512L * 1024L
        expectEquals(8L * 1024L * 1024L, memoryLimit, "Unexpected memory limit.")
        expectEquals(512L * 1024L, maxStackSize, "Unexpected max stack size.")
        expectMemoryUsage(memoryUsage)

        expectEquals(3, evaluate<Int>("1 + 2"), "Basic eval failed.")

        function("sum") { args ->
            args.sumOf { (it as Number).toInt() }
        }
        expectEquals(6, evaluate<Int>("sum(1, 2, 3)"), "Function binding failed.")

        asyncFunction("delayedUppercase") { args ->
            delay(5)
            (args.single() as String).uppercase()
        }
        expectEquals(
            "READY",
            evaluate<String>("await delayedUppercase('ready')"),
            "Async function binding failed.",
        )

        var appName = "quickjs-kt"
        var launches = 0
        define("app") {
            property("name") {
                getter { appName }
                setter { appName = it }
            }
            property("version") {
                getter { "1.0.0" }
            }
            function("launch") {
                launches++
            }
        }
        evaluate<Any?>(
            """
                if (app.version !== "1.0.0") {
                    throw new Error("Unexpected version");
                }
                app.name = "graal";
                app.launch();
                app.launch();
            """.trimIndent()
        )
        expectEquals("graal", appName, "Object binding property setter failed.")
        expectEquals(2, launches, "Object binding function failed.")

        val compiledScript = compile("40 + 2")
        expectEquals(42, evaluate<Int>(compiledScript), "Compiled bytecode evaluation failed.")

        addModule(
            name = "hello",
            code = """
                export function greeting(name) {
                    return "Hello, " + name;
                }
            """.trimIndent(),
        )
        val answerModule = compile(
            code = """
                export const answer = 42;
            """.trimIndent(),
            filename = "answer",
            asModule = true,
        )
        addModule(answerModule)

        evaluate<Any?>(
            """
                import { greeting } from "hello";
                globalThis.__moduleGreeting = greeting("Native");
            """.trimIndent(),
            asModule = true,
        )
        expectEquals(
            "Hello, Native",
            evaluate<String>("__moduleGreeting"),
            "Module source loading failed.",
        )

        evaluate<Any?>(
            """
                import { answer } from "answer";
                globalThis.__moduleAnswer = answer;
            """.trimIndent(),
            asModule = true,
        )
        expectEquals(42, evaluate<Int>("__moduleAnswer"), "Module bytecode loading failed.")

        addTypeConverters(RequestConverter, ResponseConverter)
        function<Request, Response>("fetchSync") { request ->
            Response(ok = true, body = "${request.method} ${request.url}")
        }
        asyncFunction<Request, Response>("fetchAsync") { request ->
            delay(5)
            Response(ok = true, body = "${request.method} ${request.url}")
        }
        expectEquals(
            Response(ok = true, body = "GET https://example.com"),
            evaluate<Response>(
                """
                    fetchSync({ url: "https://example.com", method: "GET" })
                """.trimIndent()
            ),
            "Custom sync converter failed.",
        )
        expectEquals(
            Response(ok = true, body = "POST https://example.com"),
            evaluate<Response>(
                """
                    await fetchAsync({ url: "https://example.com", method: "POST" })
                """.trimIndent()
            ),
            "Custom async converter failed.",
        )

        gc()
        expectMemoryUsage(memoryUsage)
    }
}

private fun exerciseQuickJsLifecycle() {
    val quickJs = QuickJs.create(Dispatchers.Default)
    expect(!quickJs.isClosed, "QuickJS should be open after creation.")
    quickJs.close()
    expect(quickJs.isClosed, "QuickJS should be closed after close().")
}

private fun expectMemoryUsage(memoryUsage: MemoryUsage) {
    expect(memoryUsage.memoryUsedCount >= 0, "Memory usage count should be non-negative.")
    expect(memoryUsage.memoryUsedSize >= 0, "Memory usage size should be non-negative.")
}

private fun expect(condition: Boolean, message: String) {
    if (!condition) {
        error(message)
    }
}

private fun expectEquals(expected: Any?, actual: Any?, message: String = "") {
    if (expected != actual) {
        error("$message Expected <$expected>, actual <$actual>.")
    }
}

private data class Request(
    val url: String,
    val method: String,
)

private data class Response(
    val ok: Boolean,
    val body: String,
)

private object RequestConverter : JsObjectConverter<Request> {
    override val targetType: KType = typeOf<Request>()

    override fun convertToTarget(value: JsObject): Request = Request(
        url = value["url"] as String,
        method = value["method"] as String,
    )

    override fun convertToSource(value: Request): JsObject =
        mapOf("url" to value.url, "method" to value.method).toJsObject()
}

private object ResponseConverter : JsObjectConverter<Response> {
    override val targetType: KType = typeOf<Response>()

    override fun convertToTarget(value: JsObject): Response = Response(
        ok = value["ok"] as Boolean,
        body = value["body"] as String,
    )

    override fun convertToSource(value: Response): JsObject =
        mapOf("ok" to value.ok, "body" to value.body).toJsObject()
}
