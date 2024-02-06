package com.dokar.quickjs

import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import java.util.Locale.US

// Based on https://github.com/cashapp/zipline/blob/trunk/zipline/src/jvmMain/kotlin/app/cash/zipline/QuickJsNativeLoader.kt
// Add windows support
@Suppress("UnsafeDynamicallyLoadedCode")
actual fun loadNativeLibrary(libraryName: String) {
    val osName = System.getProperty("os.name").lowercase(US)
    val arch = when (val osArch = System.getProperty("os.arch").lowercase(US)) {
        "amd64" -> "x64"
        "x86_64" -> "x64"
        "aarch64" -> osArch
        else -> throw IllegalStateException("Unsupported arch: '${osArch}'")
    }
    val nativeLibraryJarPath = if (osName.contains("linux")) {
        "/jni/linux_$arch/libquickjs.so"
    } else if (osName.contains("mac")) {
        "/jni/macos_$arch/libquickjs.dylib"
    } else if (osName.contains("windows") && arch == "x64") {
        "/jni/windows_$arch/libquickjs.dll"
    } else {
        throw IllegalStateException("Unsupported OS and arch: $osName, $arch")
    }
    val classLoader = QuickJs::class.java.classLoader
    val nativeLibraryUrl = classLoader.getResource(nativeLibraryJarPath)
        ?: classLoader.getResource(nativeLibraryJarPath.substring(1)) // Relative path
        ?: throw IllegalStateException("Unable to read $nativeLibraryJarPath from JAR")
    val nativeLibraryFile: Path
    try {
        nativeLibraryFile = Files.createTempFile("quickjs", null)

        // File-based deleteOnExit() uses a special internal shutdown hook that always runs last.
        nativeLibraryFile.toFile().deleteOnExit()
        nativeLibraryUrl.openStream().use { nativeLibrary ->
            Files.copy(nativeLibrary, nativeLibraryFile, REPLACE_EXISTING)
        }
    } catch (e: IOException) {
        throw RuntimeException("Unable to extract native library from JAR", e)
    }
    System.load(nativeLibraryFile.toAbsolutePath().toString())
}