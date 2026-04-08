package com.dokar.quickjs

import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.Locale.US

// Source-adapted from AndroidX NativeLibraryLoader.jvm.kt:
// https://raw.githubusercontent.com/androidx/androidx/refs/heads/androidx-main/sqlite/sqlite-bundled/src/jvmMain/kotlin/androidx/sqlite/driver/bundled/NativeLibraryLoader.jvm.kt
/** Helper class to load native libraries based on the host platform. */
internal actual object NativeLibraryLoader {

    private const val LIB_PATH_PROPERTY_NAME = "com.dokar.quickjs.library.path"
    private const val LIB_NAME_PROPERTY_NAME = "com.dokar.quickjs.library.name"

    private val osName: String
        get() = System.getProperty("os.name")?.lowercase(US) ?: error("Cannot read os.name")

    private val osArch: String
        get() = System.getProperty("os.arch")?.lowercase(US) ?: error("Cannot read os.arch")

    private val osPrefix: String
        get() = when {
            osName.contains("linux") -> "linux"
            osName.contains("mac") || osName.contains("osx") -> "macos"
            osName.contains("windows") -> "windows"
            else -> error("Unsupported operating system: $osName")
        }

    private val archSuffix: String
        get() = when (osArch) {
            "aarch64" -> "aarch64"
            "amd64", "x86_64" -> "x64"
            else -> error("Unsupported architecture: $osArch")
        }

    actual fun loadLibrary(name: String): Unit =
        synchronized(this) {
            try {
                System.loadLibrary(name)
                return
            } catch (_: UnsatisfiedLinkError) {
                // Likely not on a host with the library preinstalled, continue...
            }

            val libraryPath = System.getProperty(LIB_PATH_PROPERTY_NAME)
            if (libraryPath != null) {
                val libName = System.getProperty(LIB_NAME_PROPERTY_NAME) ?: getLibraryName(name)
                val libFile = File(libraryPath, libName)
                check(libFile.exists()) {
                    "Cannot find a suitable QuickJS binary for $osName | $osArch at the " +
                        "configured path ($LIB_PATH_PROPERTY_NAME = $libraryPath). " +
                        "File $libFile does not exist."
                }
                tryLoad(libFile.canonicalPath)
                return
            }

            val libName = getLibraryName(name)

            val javaHomeLibs =
                File(System.getProperty("java.home"), if (osPrefix == "windows") "bin" else "lib")
            val libFile = javaHomeLibs.resolve(libName)
            if (libFile.exists()) {
                tryLoad(libFile.canonicalPath)
                return
            }

            val libResourceName = getResourceName(libName)
            val libTempCopy =
                Files.createTempFile("quickjs_$name", null).apply {
                    // File-based deleteOnExit() uses a special internal shutdown hook that always runs last.
                    toFile().deleteOnExit()
                }
            QuickJs::class
                .java
                .classLoader!!
                .getResourceAsStream(libResourceName)
                .use { resourceStream ->
                    checkNotNull(resourceStream) {
                        "Cannot find a suitable QuickJS binary for $osName | $osArch in " +
                            "resource $libResourceName."
                    }
                    Files.copy(resourceStream, libTempCopy, StandardCopyOption.REPLACE_EXISTING)
                }
            tryLoad(libTempCopy.toFile().canonicalPath)
        }

    /** Gets the native library file name. */
    private fun getLibraryName(name: String): String {
        // QuickJS artifacts in this project keep the `lib` prefix on Windows too.
        val prefix = "lib"
        val extension =
            when (osPrefix) {
                "linux" -> "so"
                "macos" -> "dylib"
                "windows" -> "dll"
                else -> error("Unsupported operating system: $osName")
            }
        return "$prefix$name.$extension"
    }

    /** Gets the JAR resource file path to the native library. */
    private fun getResourceName(libName: String): String {
        val resourceFolder = "${osPrefix}_$archSuffix"
        return "jni/$resourceFolder/$libName"
    }

    private fun tryLoad(path: String) {
        @Suppress("UnsafeDynamicallyLoadedCode") System.load(path)
    }
}
