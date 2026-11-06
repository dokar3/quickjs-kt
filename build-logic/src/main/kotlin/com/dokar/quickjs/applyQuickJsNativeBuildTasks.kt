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

/**
 * Registers the tasks that build the native QuickJS libraries with CMake (the JNI shared libraries
 * and the Kotlin/Native static libraries) and wires them into the JVM compile, cinterop and link
 * tasks.
 */
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
            val taskNames = gradle.startParameter.taskNames
            val isPublishingToMavenLocal = taskNames.any {
                it.contains("publish", ignoreCase = true) &&
                        it.contains("MavenLocal", ignoreCase = true)
            }
            val isPublishing = taskNames.any {
                it.contains("publish", ignoreCase = true)
            } && !isPublishingToMavenLocal
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
                    release = true,
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
        "TvosArm64",
        "TvosSimulatorArm64",
    )
    for (suffix in cinteropTaskSuffixes) {
        tasks.named("cinteropQuickjs$suffix") {
            dependsOn(buildQuickJsNativeLibsTask.name)
            inputs.dir(nativeStaticLibOutDir)
        }
    }

    tasks.configureEach {
        if (name.startsWith("link") &&
            (name.contains("Test") || name.contains("Executable"))
        ) {
            dependsOn(buildQuickJsNativeLibsTask.name)
            inputs.dir(nativeStaticLibOutDir)
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
    val targetGroup = providers.gradleProperty("quickjs.targetGroup").orNull?.lowercase()

    fun List<Platform>.filterByTargetGroup(): List<Platform> {
        if (targetGroup == null) return this
        return filter { platform ->
            when (targetGroup) {
                "macos" -> platform == Platform.macos_x64 || platform == Platform.macos_aarch64
                "ios" -> platform == Platform.ios_x64 ||
                        platform == Platform.ios_aarch64 ||
                        platform == Platform.ios_simulator_aarch64
                "tvos" -> platform == Platform.tvos_aarch64 ||
                        platform == Platform.tvos_simulator_aarch64
                else -> error("Unknown quickjs.targetGroup: '$targetGroup'")
            }
        }
    }

    val isPublishingToMavenLocal = taskNames.any {
        it.contains("publish", ignoreCase = true) &&
                it.contains("MavenLocal", ignoreCase = true)
    }
    val isPublishing = taskNames.any {
        it.contains("publish", ignoreCase = true)
    } && !isPublishingToMavenLocal
    if (isPublishing) {
        val allPlatforms = Platform.values().toList()
        return when (currentPlatform) {
            Platform.linux_x64,
            Platform.linux_aarch64 -> allPlatforms.filter {
                it == Platform.linux_x64 || it == Platform.linux_aarch64
            }
            Platform.windows_x64 -> allPlatforms.filter { it == Platform.windows_x64 }
            Platform.macos_x64,
            Platform.macos_aarch64 -> allPlatforms
            else -> allPlatforms
        }.filterByTargetGroup()
    }

    val isBuild = taskNames.any { it.contains("build", ignoreCase = true) }
    val isAssemble = taskNames.any { it.contains("assemble", ignoreCase = true) }
    val isCheck = taskNames.any { it.contains("check", ignoreCase = true) }

    if (isBuild || isAssemble || isCheck || isPublishingToMavenLocal) {
        when (currentPlatform) {
            Platform.linux_x64 -> {
                return listOf(Platform.linux_x64).filterByTargetGroup()
            }

            Platform.macos_x64,
            Platform.macos_aarch64 -> {
                return listOf(
                    Platform.macos_x64,
                    Platform.macos_aarch64,
                    Platform.ios_x64,
                    Platform.ios_aarch64,
                    Platform.ios_simulator_aarch64,
                    Platform.tvos_aarch64,
                    Platform.tvos_simulator_aarch64,
                ).filterByTargetGroup()
            }

            Platform.windows_x64 -> {
                return listOf(Platform.windows_x64).filterByTargetGroup()
            }

            else -> {} // Not supported yet
        }
    }

    val matchedPlatforms = LinkedHashSet<Platform>()
    for (taskName in taskNames) {
        val name = taskName.lowercase()
        if (name.contains("mingwx64")) {
            matchedPlatforms.add(Platform.windows_x64)
        }
        if (name.contains("linuxx64")) {
            matchedPlatforms.add(Platform.linux_x64)
        }
        if (name.contains("linuxarm64")) {
            matchedPlatforms.add(Platform.linux_aarch64)
        }
        if (name.contains("macosx64")) {
            matchedPlatforms.add(Platform.macos_x64)
        }
        if (name.contains("macosarm64")) {
            matchedPlatforms.add(Platform.macos_aarch64)
        }
        if (name.contains("iosx64")) {
            matchedPlatforms.add(Platform.ios_x64)
        }
        if (name.contains("iosarm64")) {
            matchedPlatforms.add(Platform.ios_aarch64)
        }
        if (name.contains("iossimulatorarm64")) {
            matchedPlatforms.add(Platform.ios_simulator_aarch64)
        }
        if (name.contains("tvosarm64")) {
            matchedPlatforms.add(Platform.tvos_aarch64)
        }
        if (name.contains("tvossimulatorarm64")) {
            matchedPlatforms.add(Platform.tvos_simulator_aarch64)
        }
    }

    if (matchedPlatforms.isNotEmpty()) {
        return matchedPlatforms.toList().filterByTargetGroup()
    }

    return listOf(currentPlatform).filterByTargetGroup()
}
