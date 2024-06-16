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
    fun convertSerializableClasses() = runTest {
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
                    await fetch({ url: "https://example.com", method: "GET" })
                """.trimIndent()
            )
            val expected = FetchResponse(ok = true, body = "Fetched https://example.com")
            assertEquals(expected, result)
        }
    }
}

@JsonClass(generateAdapter = true)
internal data class FetchParams(
    val url: String,
    val method: String,
)

@JsonClass(generateAdapter = true)
internal data class FetchResponse(
    val ok: Boolean,
    val body: String,
)