package com.dokar.quickjs.converter.test

import com.dokar.quickjs.binding.asyncFunction
import com.dokar.quickjs.conveter.JsonClassConverter
import com.dokar.quickjs.quickJs
import com.squareup.moshi.JsonClass
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class JsonClassConverterTest {
    @Test
    fun convertJsonClassClasses() = runTest {
        quickJs {
            addTypeConverters(
                JsonClassConverter<FetchParams>(),
                JsonClassConverter<FetchResponse>()
            )

            asyncFunction<FetchParams, FetchResponse>("fetch") {
                FetchResponse(ok = true, body = "Fetched ${it.url}")
            }

            val result = evaluate<FetchResponse>(
                """
                    const headers = { "Content-Type": "application/json" };
                    await fetch({ url: "https://example.com", method: "GET", headers: headers })
                """.trimIndent()
            )
            val expected = FetchResponse(ok = true, body = "Fetched https://example.com")
            assertEquals(expected, result)
        }
    }

    @Test
    fun convertGenericJsonClassClasses() = runTest {
        quickJs {
            addTypeConverters(
                JsonClassConverter<Wrapper<FetchResponse>>(),
            )

            val expected = Wrapper(data = FetchResponse(ok = true, body = "Hello"))

            asyncFunction("load") { expected }

            asyncFunction<Wrapper<FetchResponse>?>("loadNullable") { expected }

            assertEquals(expected, evaluate<Wrapper<FetchResponse>>("await load()"))
            assertEquals(expected, evaluate<Wrapper<FetchResponse>?>("await loadNullable()"))
        }
    }
}

@JsonClass(generateAdapter = true)
data class Wrapper<T>(
    val data: T,
)

@JsonClass(generateAdapter = true)
internal data class FetchParams(
    val url: String,
    val method: String,
    val headers: Map<String, String>,
)

@JsonClass(generateAdapter = true)
internal data class FetchResponse(
    val ok: Boolean,
    val body: String,
)
