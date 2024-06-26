package com.dokar.quickjs.converter.test

import com.dokar.quickjs.binding.asyncFunction
import com.dokar.quickjs.conveter.SerializableConverter
import com.dokar.quickjs.quickJs
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalSerializationApi::class)
class SerializableConverterTest {
    @Test
    fun convertSerializableClasses() = runTest {
        quickJs {
            addTypeConverters(
                SerializableConverter<FetchParams>(),
                SerializableConverter<FetchResponse>()
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
    fun convertGenericSerializableClasses() = runTest {
        quickJs {
            addTypeConverters(
                SerializableConverter<Wrapper<FetchResponse>>(),
            )

            val expected = Wrapper(data = FetchResponse(ok = true, body = "Hello"))

            asyncFunction("load") { expected }

            asyncFunction<Wrapper<FetchResponse>?>("loadNullable") { expected }

            assertEquals(expected, evaluate<Wrapper<FetchResponse>>("await load()"))
            assertEquals(expected, evaluate<Wrapper<FetchResponse>?>("await loadNullable()"))
        }
    }
}

@Serializable
data class Wrapper<T>(
    val data: T,
)

@Serializable
private data class FetchParams(
    val url: String,
    val method: String,
    val headers: Map<String, String>,
)

@Serializable
private data class FetchResponse(
    val ok: Boolean,
    val body: String,
)