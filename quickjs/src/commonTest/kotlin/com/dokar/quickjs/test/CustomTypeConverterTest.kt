package com.dokar.quickjs.test

import com.dokar.quickjs.binding.JsObject
import com.dokar.quickjs.binding.asyncFunction
import com.dokar.quickjs.binding.function
import com.dokar.quickjs.binding.toJsObject
import com.dokar.quickjs.converter.JsObjectConverter
import com.dokar.quickjs.quickJs
import kotlinx.coroutines.test.runTest
import kotlin.reflect.KType
import kotlin.reflect.typeOf
import kotlin.test.Test
import kotlin.test.assertEquals

class CustomTypeConverterTest {
    @Test
    fun customClassesOnParametersAndReturn() = runTest {
        quickJs {
            addTypeConverters(FetchParamsConverter, FetchResponseConverter)

            function<FetchParams, FetchResponse>("fetchSync") { args ->
                val (url) = args
                FetchResponse(ok = true, body = "Hello from $url")
            }

            asyncFunction<FetchParams, FetchResponse>("fetch") { args ->
                FetchResponse(ok = true, body = "Hello from ${args.url}")
            }

            val expected = FetchResponse(ok = true, body = "Hello from https://example.com")

            val result = evaluate<FetchResponse>(
                """
                    fetchSync({url: "https://example.com", method: "GET"})
                """.trimIndent()
            )
            assertEquals(expected, result)

            val asyncResult = evaluate<FetchResponse>(
                """
                    await fetch({url: "https://example.com", method: "GET"})
                """.trimIndent()
            )
            assertEquals(expected, asyncResult)
        }
    }
}

private data class FetchParams(
    val url: String,
    val method: String,
)

private data class FetchResponse(
    val ok: Boolean,
    val body: String,
)

private object FetchParamsConverter : JsObjectConverter<FetchParams> {
    override val targetType: KType = typeOf<FetchParams>()

    override fun convertToTarget(value: JsObject): FetchParams = FetchParams(
        url = value["url"] as String,
        method = value["method"] as String,
    )

    override fun convertToSource(value: FetchParams): JsObject =
        mapOf("url" to value.url, "method" to value.method).toJsObject()
}

private object FetchResponseConverter : JsObjectConverter<FetchResponse> {
    override val targetType: KType = typeOf<FetchResponse>()

    override fun convertToTarget(value: JsObject): FetchResponse = FetchResponse(
        ok = value["ok"] as Boolean,
        body = value["body"] as String,
    )

    override fun convertToSource(value: FetchResponse): JsObject =
        mapOf("ok" to value.ok, "body" to value.body).toJsObject()
}
