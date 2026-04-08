package com.dokar.quickjs

import java.io.File

// Source-adapted from AndroidX NativeLibraryLoader.android.kt:
// https://raw.githubusercontent.com/androidx/androidx/refs/heads/androidx-main/sqlite/sqlite-bundled/src/androidMain/kotlin/androidx/sqlite/driver/bundled/NativeLibraryLoader.android.kt
/** Helper class to load native libraries based on the host platform. */
internal actual object NativeLibraryLoader {

    private const val LIB_PATH_PROPERTY_NAME = "com.dokar.quickjs.library.path"
    private const val LIB_NAME_PROPERTY_NAME = "com.dokar.quickjs.library.name"

    actual fun loadLibrary(name: String): Unit =
        synchronized(this) {
            val libraryPath = System.getProperty(LIB_PATH_PROPERTY_NAME)
            val libraryName = System.getProperty(LIB_NAME_PROPERTY_NAME)
            if (libraryPath != null && libraryName != null) {
                val libFile = File(libraryPath, libraryName)
                check(libFile.exists()) {
                    "Cannot find a suitable QuickJS binary at the configured path " +
                        "($LIB_PATH_PROPERTY_NAME = $libraryPath). File $libFile does not exist."
                }
                @Suppress("UnsafeDynamicallyLoadedCode") System.load(libFile.absolutePath)
                return
            }

            System.loadLibrary(name)
        }
}
