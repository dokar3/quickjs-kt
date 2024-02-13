package com.dokar.quickjs

import org.gradle.api.Project
import org.gradle.api.tasks.bundling.Jar
import org.gradle.kotlin.dsl.withType
import java.io.File
import java.util.Properties

private val jniLibraryPlatforms = listOf(
    Platform.windows_x64,
    Platform.linux_x64,
    Platform.linux_aarch64,
    Platform.macos_x64,
    Platform.macos_aarch64,
)

fun Project.applyQuickJsNativeBuildTasks(cmakeFile: File) {
    val nativeBuildDir = File(projectDir, "/native/build")
    val jniLibOutDir = File(nativeBuildDir, "/jni_libs")
    val nativeStaticLibOutDir = File(nativeBuildDir, "/static_libs")

    // Task to build multiplatform jni libraries
    val buildQuickJsJniLibsTask = tasks.register("buildQuickJsJniLibs") {
        inputs.dir(File(projectDir, "/native/quickjs"))
        inputs.dir(File(projectDir, "/native/common"))
        inputs.dir(File(projectDir, "/native/jni"))
        inputs.dir(File(projectDir, "/native/cmake"))
        inputs.file(File(projectDir, "/native/CMakeLists.txt"))

        outputs.dir(jniLibOutDir)

        doLast {
            val isPublishing = gradle.startParameter.taskNames.contains("publish")
            if (isPublishing) {
                for (platform in jniLibraryPlatforms) {
                    try {
                        buildQuickJsNativeLibrary(
                            cmakeFile = cmakeFile,
                            platform = platform.name,
                            sharedLib = true,
                            withJni = true,
                            release = true,
                            outputDir = File(jniLibOutDir, platform.name)
                        )
                    } catch (e: Exception) {
                        error(e)
                    }
                }
            } else {
                buildQuickJsNativeLibrary(
                    cmakeFile = cmakeFile,
                    platform = currentPlatform.name,
                    sharedLib = true,
                    withJni = true,
                    release = false,
                    outputDir = File(jniLibOutDir, currentPlatform.name)
                )
            }
        }
    }

    // Task to build Kotlin/Native static libraries
    val buildQuickJsNativeLibsTask = tasks.register("buildQuickJsNativeLibs") {
        inputs.dir(File(projectDir, "/native/quickjs"))
        inputs.dir(File(projectDir, "/native/common"))
        inputs.dir(File(projectDir, "/native/cmake"))
        inputs.file(File(projectDir, "/native/CMakeLists.txt"))

        outputs.dir(nativeStaticLibOutDir)

        val buildPlatforms = findBuildPlatformsFromStartTaskNames()
        inputs.property("platform", buildPlatforms)

        doLast {
            for (platform in buildPlatforms) {
                buildQuickJsNativeLibrary(
                    cmakeFile = cmakeFile,
                    platform = platform.name,
                    sharedLib = false,
                    withJni = false,
                    release = false,
                    outputDir = nativeStaticLibOutDir,
                    withPlatformSuffixIfCopy = true,
                )
            }
        }
    }

    val copyQuickJsJniLibsTask = tasks.register("copyQuickJsJniLibs") {
        dependsOn(buildQuickJsJniLibsTask.name)

        val outputFiles = tasks.getByName(buildQuickJsJniLibsTask.name).outputs.files
        inputs.dir(outputFiles.first())

        val outputDir = File(layout.buildDirectory.asFile.get(), "libs/jni")
        outputs.dir(outputDir)

        doLast {
            if (outputFiles.isEmpty) {
                return@doLast
            }
            val sharedLibFile = outputFiles.first()
            if (!sharedLibFile.exists()) {
                return@doLast
            }
            copy {
                from(sharedLibFile)
                into(outputDir)
                include("*/*.dll", "*/*.so", "*/*.dylib")
            }
        }
    }

    tasks.named("compileKotlinJvm") {
        dependsOn(copyQuickJsJniLibsTask.name)
    }

    tasks.withType<Jar>().configureEach {
        dependsOn(copyQuickJsJniLibsTask.name)
        // Pack the shared libraries
        from(File(layout.buildDirectory.asFile.get(), "libs")) {
            include("jni/")
        }
    }

    // Jvm test: Copy shared libs to classes dir
    tasks.named("jvmTest") {
        dependsOn(copyQuickJsJniLibsTask.name)
        doFirst {
            val libDir = tasks.getByName(copyQuickJsJniLibsTask.name).outputs.files.first()
            copy {
                from(libDir.parentFile)
                into(project.layout.buildDirectory.dir("classes/kotlin/jvm/test"))
                include("${libDir.name}/**")
            }
        }
    }

    val cinteropTaskSuffixes = listOf(
        "MingwX64",
        "LinuxX64",
        "LinuxArm64",
        "MacosX64",
        "MacosArm64",
        "IosX64",
        "IosArm64",
        "IosSimulatorArm64",
    )
    for (suffix in cinteropTaskSuffixes) {
        tasks.named("cinteropQuickjs$suffix") {
            dependsOn(buildQuickJsNativeLibsTask.name)
        }
    }

    tasks.register("cleanQuickJSBuild") {
        doLast {
            delete(nativeBuildDir)
        }
    }
    tasks.named("clean") {
        dependsOn("cleanQuickJSBuild")
    }
}

private fun Project.buildQuickJsNativeLibrary(
    cmakeFile: File,
    platform: String,
    sharedLib: Boolean,
    withJni: Boolean,
    release: Boolean,
    outputDir: File? = null,
    withPlatformSuffixIfCopy: Boolean = false,
) {
    val libType = if (sharedLib) "shared" else "static"

    println("Building $libType native library for target '$platform'...")

    val commonArgs = arrayOf(
        "-B",
        "build/$platform",
        "-G Ninja",
        "-DCMAKE_BUILD_TYPE=${if (release) "MinSizeRel" else "Debug"}",
        "-DTARGET_PLATFORM=$platform",
        "-DBUILD_WITH_JNI=${if (withJni) "ON" else "OFF"}",
        "-DLIBRARY_TYPE=${if (sharedLib) "shared" else "static"}",
    )

    fun javaHomeArg(home: String): String {
        return "-DPLATFORM_JAVA_HOME=$home"
    }

    val args = if (withJni) {
        when (platform) {
            "windows_x64" -> commonArgs + javaHomeArg(windowX64JavaHome())
            "linux_x64" -> commonArgs + javaHomeArg(linuxX64JavaHome())
            "linux_aarch64" -> commonArgs + javaHomeArg(linuxAarch64JavaHome())
            "macos_x64" -> commonArgs + javaHomeArg(macosX64JavaHome())
            "macos_aarch64" -> commonArgs + javaHomeArg(macosAarch64JavaHome())
            else -> error("Unsupported platform: '$platform'")
        }
    } else {
        commonArgs
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
        val ext = if (sharedLib) {
            if (platform.startsWith("windows")) "dll"
            else if (platform.startsWith("linux")) "so"
            else if (platform.startsWith("macos")) "dylib"
            else error("Unknown platform: $platform")
        } else {
            "a"
        }
        val libraryFile = file("native/build/$platform/libquickjs.$ext")
        val destFilename = if (withPlatformSuffixIfCopy) {
            "libquickjs_${platform}.$ext"
        } else {
            "libquickjs.$ext"
        }
        libraryFile.copyTo(File(outDir, destFilename), overwrite = true)
    }

    // Generate build files
    runCommand("cmake", *args, "./")
    // Build
    runCommand("cmake", "--build", args[1])

    if (outputDir != null) {
        copyLibToOutputDir(outputDir)
    }
}

private fun Project.findBuildPlatformsFromStartTaskNames(): List<Platform> {
    val taskNames = gradle.startParameter.taskNames

    if (taskNames.contains("build")) {
        when (currentPlatform) {
            Platform.linux_x64 -> {
                return listOf(Platform.linux_x64)
            }

            Platform.macos_x64 -> {
                return listOf(
                    Platform.macos_x64,
                    Platform.macos_aarch64,
                    Platform.ios_x64,
                    Platform.ios_aarch64,
                    Platform.ios_aarch64_simulator,
                )
            }

            Platform.windows_x64 -> {
                return listOf(Platform.windows_x64)
            }

            else -> {} // Not supported yet
        }
    }

    var platform: Platform? = null
    for (taskName in taskNames) {
        val name = taskName.lowercase()
        if (name.contains("mingwx64")) {
            platform = Platform.windows_x64
            break
        } else if (name.contains("linuxx64")) {
            platform = Platform.linux_x64
            break
        } else if (name.contains("linuxarm64")) {
            platform = Platform.linux_aarch64
            break
        } else if (name.contains("macosx64")) {
            platform = Platform.macos_x64
            break
        } else if (name.contains("macosarm64")) {
            platform = Platform.macos_aarch64
            break
        } else if (name.contains("iosx64")) {
            platform = Platform.ios_x64
            break
        } else if (name.contains("iosarm64")) {
            platform = Platform.ios_aarch64
            break
        } else if (name.contains("iossimulatorarm64")) {
            platform = Platform.ios_aarch64_simulator
            break
        }
    }

    return listOf(platform ?: currentPlatform)
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