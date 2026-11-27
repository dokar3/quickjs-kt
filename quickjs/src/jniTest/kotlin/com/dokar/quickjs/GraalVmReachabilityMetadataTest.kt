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

    @Test
    fun moduleLoaderCallbacksIncludeImportTypeAndDataAccessors() {
        val resourcePath =
            "META-INF/native-image/com.dokar.quickjs/quickjs/reachability-metadata.json"
        val metadata = assertNotNull(javaClass.classLoader.getResourceAsStream(resourcePath))
            .bufferedReader()
            .use { it.readText() }

        val expectedCallbacks = listOf(
            "hasModuleNormalizer" to emptyList(),
            "normalizeModule" to listOf("java.lang.String", "java.lang.String"),
            "loadModule" to listOf("java.lang.String", "int"),
            "getModuleSource" to listOf("java.lang.Object"),
            "getModuleBytecode" to listOf("java.lang.Object"),
            "getModuleText" to listOf("java.lang.Object"),
            "getModuleJson" to listOf("java.lang.Object"),
            "getModuleBytes" to listOf("java.lang.Object"),
            "onModuleCompiled" to listOf("java.lang.String", "byte[]"),
            "onModuleLoadFailed" to listOf("java.lang.String", "int"),
        )
        for ((name, params) in expectedCallbacks) {
            assertTrue(
                callbackPattern(name = name, parameterTypes = params).containsMatchIn(metadata),
                "Missing reachability metadata entry for $name(${params.joinToString()})",
            )
        }
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
