package com.dokar.quickjs

import org.gradle.api.Project
import org.gradle.api.tasks.bundling.Jar
import org.gradle.kotlin.dsl.withType
import java.io.File

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
                    buildQuickJsNativeLibrary(
                        cmakeFile = cmakeFile,
                        platform = platform,
                        sharedLib = true,
                        withJni = true,
                        release = true,
                        outputDir = File(jniLibOutDir, platform.name)
                    )
                }
            } else {
                buildQuickJsNativeLibrary(
                    cmakeFile = cmakeFile,
                    platform = currentPlatform,
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
                    platform = platform,
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
