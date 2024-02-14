package com.dokar.quickjs

import org.gradle.api.Project
import java.io.File
import java.util.Properties

internal fun Project.buildQuickJsNativeLibrary(
    cmakeFile: File,
    platform: Platform,
    sharedLib: Boolean,
    withJni: Boolean,
    release: Boolean,
    outputDir: File? = null,
    withPlatformSuffixIfCopy: Boolean = false,
) {
    val libType = if (sharedLib) "shared" else "static"

    println("Building $libType native library for target '$platform'...")

    val buildType = if (release) "MinSizeRel" else "Debug"
    val commonArgs = arrayOf(
        "-B",
        "build/$platform",
        "-DCMAKE_BUILD_TYPE=${buildType}",
        "-DTARGET_PLATFORM=$platform",
        "-DBUILD_WITH_JNI=${if (withJni) "ON" else "OFF"}",
        "-DLIBRARY_TYPE=${if (sharedLib) "shared" else "static"}",
    )

    // Generators
    val ninja = "-G Ninja"
    val xcode = "-G Xcode"

    fun javaHomeArg(home: String): String {
        return "-DPLATFORM_JAVA_HOME=$home"
    }

    val generateArgs = if (withJni) {
        when (platform) {
            Platform.windows_x64 -> commonArgs + ninja + javaHomeArg(windowX64JavaHome())
            Platform.linux_x64 -> commonArgs + ninja + javaHomeArg(linuxX64JavaHome())
            Platform.linux_aarch64 -> commonArgs + ninja + javaHomeArg(linuxAarch64JavaHome())
            Platform.macos_x64 -> commonArgs + ninja + javaHomeArg(macosX64JavaHome())
            Platform.macos_aarch64 -> commonArgs + ninja + javaHomeArg(macosAarch64JavaHome())
            else -> error("Unsupported platform: '$platform'")
        }
    } else {
        when (platform) {
            Platform.windows_x64,
            Platform.linux_aarch64,
            Platform.linux_x64,
            Platform.macos_aarch64,
            Platform.macos_x64 -> commonArgs + ninja

            Platform.ios_aarch64,
            Platform.ios_x64,
            Platform.ios_simulator_aarch64 -> commonArgs + xcode
        }
    }

    val buildArgs = when (platform) {
        Platform.ios_x64,
        Platform.ios_simulator_aarch64 -> arrayOf(
            commonArgs[1],
            "--",
            "-sdk",
            "iphonesimulator"
        )

        else -> arrayOf(commonArgs[1])
    }

    fun runCommand(vararg args: Any) {
        exec {
            workingDir = cmakeFile.parentFile
            standardOutput = System.out
            errorOutput = System.err
            commandLine(*args)
        }
    }

    fun copyLibToOutputDir(outDir: File) {
        // Copy built library to output dir
        if (!outDir.exists() && !outDir.mkdirs()) {
            error("Failed to create library output dir: $outDir")
        }
        println("Copying built QuickJS $libType library to ${file(outDir)}")
        val (dir, ext) = if (sharedLib) {
            when (platform.osName) {
                "windows" -> "" to "dll"
                "linux" -> "" to "so"
                "macos" -> "" to "dylib"
                else -> error("Unsupported platform: $platform")
            }
        } else {
            when (platform) {
                Platform.ios_x64,
                Platform.ios_aarch64 -> "$buildType-iphoneos/" to "a"

                Platform.ios_simulator_aarch64 -> "$buildType/" to "a"

                else -> "" to "a"
            }
        }
        val libraryFile = file("native/build/$platform/${dir}libquickjs.$ext")
        val destFilename = if (withPlatformSuffixIfCopy) {
            "libquickjs_${platform}.$ext"
        } else {
            "libquickjs.$ext"
        }
        libraryFile.copyTo(File(outDir, destFilename), overwrite = true)
    }

    // Generate build files
    runCommand("cmake", *generateArgs, "./")
    // Build
    runCommand("cmake", "--build", *buildArgs)

    if (outputDir != null) {
        copyLibToOutputDir(outputDir)
    }
}

/// Multiplatform JDK locations

private fun Project.windowX64JavaHome() =
    requireNotNull(envVarOrLocalPropOf("JAVA_HOME_WINDOWS_X64")) {
        "'JAVA_HOME_WINDOWS_X64' is not found in env vars or local.properties"
    }

private fun Project.linuxX64JavaHome() =
    requireNotNull(envVarOrLocalPropOf("JAVA_HOME_LINUX_X64")) {
        "'JAVA_HOME_LINUX_X64' is not found in env vars or local.properties"
    }

private fun Project.linuxAarch64JavaHome() =
    requireNotNull(envVarOrLocalPropOf("JAVA_HOME_LINUX_AARCH64")) {
        "'JAVA_HOME_LINUX_AARCH64' is not found env vars or in local.properties"
    }

private fun Project.macosX64JavaHome() =
    requireNotNull(envVarOrLocalPropOf("JAVA_HOME_MACOS_X64")) {
        "'JAVA_HOME_MACOS_X64' is not found in env vars or local.properties"
    }

private fun Project.macosAarch64JavaHome() =
    requireNotNull(envVarOrLocalPropOf("JAVA_HOME_MACOS_AARCH64")) {
        "'JAVA_HOME_MACOS_AARCH64' is not found in env vars or local.properties"
    }

private fun Project.envVarOrLocalPropOf(key: String): String? {
    val localProperties = Properties()
    val localPropertiesFile = project.rootDir.resolve("local.properties")
    if (localPropertiesFile.exists()) {
        localPropertiesFile.inputStream().use {
            localProperties.load(it)
        }
    }
    return System.getenv(key) ?: localProperties[key]?.toString()
}