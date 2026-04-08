package com.dokar.quickjs

import kotlin.test.Test
import kotlin.test.assertEquals

class NativeLibraryLoaderJvmTest {
    @Test
    fun windowsResourceNameMatchesPackagedQuickJsLibrary() {
        withSystemProperties(
            "os.name" to "Windows 11",
            "os.arch" to "amd64",
        ) {
            val loaderClass = NativeLibraryLoader::class.java
            val instance = loaderClass.getField("INSTANCE").get(null)

            val getLibraryName = loaderClass.getDeclaredMethod("getLibraryName", String::class.java)
            getLibraryName.isAccessible = true
            assertEquals("libquickjs.dll", getLibraryName.invoke(instance, "quickjs"))

            val getResourceName = loaderClass.getDeclaredMethod("getResourceName", String::class.java)
            getResourceName.isAccessible = true
            assertEquals(
                "jni/windows_x64/libquickjs.dll",
                getResourceName.invoke(instance, "libquickjs.dll"),
            )
        }
    }

    private fun withSystemProperties(vararg entries: Pair<String, String>, block: () -> Unit) {
        val previousValues = entries.associate { (key, _) -> key to System.getProperty(key) }
        try {
            entries.forEach { (key, value) -> System.setProperty(key, value) }
            block()
        } finally {
            previousValues.forEach { (key, value) ->
                if (value == null) {
                    System.clearProperty(key)
                } else {
                    System.setProperty(key, value)
                }
            }
        }
    }
}
