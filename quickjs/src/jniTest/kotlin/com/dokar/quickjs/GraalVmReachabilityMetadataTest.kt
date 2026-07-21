package com.dokar.quickjs

import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class GraalVmReachabilityMetadataTest {
    @Test
    fun promiseRejectionCallbacksIncludePromiseIdentity() {
        val resourcePath =
            "META-INF/native-image/com.dokar.quickjs/quickjs/reachability-metadata.json"
        val metadata = assertNotNull(javaClass.classLoader.getResourceAsStream(resourcePath))
            .bufferedReader()
            .use { it.readText() }

        assertTrue(
            callbackPattern(
                name = "setUnhandledPromiseRejection",
                parameterTypes = listOf("long", "java.lang.Object"),
            ).containsMatchIn(metadata),
        )
        assertTrue(
            callbackPattern(
                name = "clearHandledPromiseRejection",
                parameterTypes = listOf("long"),
            ).containsMatchIn(metadata),
        )
    }

    private fun callbackPattern(name: String, parameterTypes: List<String>): Regex {
        val parameters = parameterTypes.joinToString("\\s*,\\s*") {
            "\"${Regex.escape(it)}\""
        }
        return Regex(
            "\"name\"\\s*:\\s*\"${Regex.escape(name)}\"\\s*," +
                    "\\s*\"parameterTypes\"\\s*:\\s*\\[\\s*" +
                    parameters +
                    "\\s*]",
        )
    }
}
