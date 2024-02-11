/// Based on https://github.com/cashapp/zipline/blob/trunk/zipline/build.gradle.kts
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget
import java.util.Properties

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.mavenPublish)
}

kotlin {
    androidTarget {
        publishLibraryVariants("release")
    }
    jvm()

    mingwX64()

    applyDefaultHierarchyTemplate()

    jvmToolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(libs.kotlinx.coroutines.core)
            }
        }

        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
        }

        val jniMain by creating {
            dependsOn(commonMain)
        }

        val jvmMain by getting {
            dependsOn(jniMain)
        }

        val jvmTest by getting {
            dependsOn(commonTest.get())
            kotlin.srcDir("src/jniTest/kotlin/")
        }

        val androidMain by getting {
            dependsOn(jniMain)
        }

        targets.withType<KotlinNativeTarget> {
            val main by compilations.getting

            main.cinterops {
                create("quickjs") {
                    header(file("native/quickjs/quickjs.h"))
                    packageName("com.dokar.quickjs")
                }
            }
        }
    }
}

val jniCmakeFile = file("native/CMakeLists.txt")

android {
    namespace = "com.dokar.quickjs"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        minSdk = libs.versions.android.minSdk.get().toInt()
        multiDexEnabled = true

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")

        ndk {
            abiFilters += listOf("x86", "x86_64", "armeabi-v7a", "arm64-v8a")
        }

        externalNativeBuild {
            cmake {
                arguments(
                    "-DANDROID_TOOLCHAIN=clang",
                    "-DTARGET_PLATFORM=android",
                    "-DLIBRARY_TYPE=shared",
                )
                cFlags("-fstrict-aliasing")
            }
        }

        packaging {
            jniLibs.keepDebugSymbols += "**/libquickjs.so"
        }
    }

    buildTypes {
        val release by getting {
            externalNativeBuild {
                cmake {
                    arguments("-DCMAKE_BUILD_TYPE=MinSizeRel")
                    cFlags(
                        "-g0",
                        "-Os",
                        "-fomit-frame-pointer",
                        "-DNDEBUG",
                        "-fvisibility=hidden"
                    )
                }
            }
        }

        val debug by getting {
            externalNativeBuild {
                cmake {
                    cFlags("-g", "-DDEBUG", "-DDUMP_LEAKS")
                }
            }
        }
    }

    externalNativeBuild {
        cmake {
            path = jniCmakeFile
        }
    }
}

val nativeLibraryPlatforms = listOf(
    "windows_x64",
    "linux_x64",
    "linux_aarch64",
    "macos_x64",
    "macos_aarch64",
)

val nativeBuildDir = File(projectDir, "/native/build")

// Task to build multiplatform jni libraries
val buildQuickJsJniLibsTask = tasks.register("buildQuickJsJniLibs") {
    inputs.dir(File(projectDir, "/native"))
    inputs.dir(File(projectDir, "/native/cmake"))
    inputs.file(File(projectDir, "/native/CMakeLists.txt"))

    outputs.dir(nativeBuildDir)

    doLast {
        val isPublishing = gradle.startParameter.taskNames.contains("publish")
        if (isPublishing) {
            for (platform in nativeLibraryPlatforms) {
                try {
                    buildQuickJsNativeLibrary(
                        platform = platform,
                        sharedLib = true,
                        withJni = true,
                        release = true,
                    )
                } catch (e: Exception) {
                    error(e)
                }
            }
        } else {
            buildQuickJsNativeLibrary(
                platform = currentPlatform(),
                sharedLib = true,
                withJni = true,
                release = false,
            )
        }
    }
}

// Task to build Kotlin/Native static libraries
val buildQuickJsNativeLibsTask = tasks.register("buildQuickJsNativeLibs") {
    inputs.dir(File(projectDir, "/native"))
    inputs.dir(File(projectDir, "/native/cmake"))
    inputs.file(File(projectDir, "/native/CMakeLists.txt"))

    outputs.dir(nativeBuildDir)

    doLast {
        val isPublishing = gradle.startParameter.taskNames.contains("publish")
        if (isPublishing) {
            for (platform in nativeLibraryPlatforms) {
                try {
                    buildQuickJsNativeLibrary(
                        platform = platform,
                        sharedLib = false,
                        withJni = false,
                        release = true,
                    )
                } catch (e: Exception) {
                    error(e)
                }
            }
        } else {
            buildQuickJsNativeLibrary(
                platform = currentPlatform(),
                sharedLib = false,
                withJni = false,
                release = false,
                outputDir = "/native/build/static_libs"
            )
        }
    }
}

fun buildQuickJsNativeLibrary(
    platform: String,
    sharedLib: Boolean,
    withJni: Boolean,
    release: Boolean,
    outputDir: String? = null,
) {
    println("Building native library for target '$platform'...")

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
            workingDir = jniCmakeFile.parentFile
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
        println("Coping built QuickJS static library to ${file(outDir)}")
        val ext = if (sharedLib) {
            if (platform.startsWith("windows")) "dll"
            else if (platform.startsWith("linux")) "so"
            else if (platform.startsWith("macos")) "dylib"
            else error("Unknown platform: $platform")
        } else {
            "a"
        }
        val libraryFile = file("native/build/$platform/libquickjs.$ext")
        libraryFile.copyTo(File(outDir, libraryFile.name), overwrite = true)
    }

    // Generate build files
    runCommand("cmake", *args, "./")
    // Build
    runCommand("cmake", "--build", args[1])

    if (outputDir != null) {
        copyLibToOutputDir(file(outputDir))
    }
}

fun currentPlatform(): String {
    val osName = System.getProperty("os.name").lowercase()
    val osArch = System.getProperty("os.arch").lowercase()
    val os = if (osName.contains("windows")) {
        "windows"
    } else if (osName.contains("linux")) {
        "linux"
    } else if (osName.contains("mac")) {
        "macos"
    } else {
        error("Unsupported OS: '$osName'")
    }
    val arch = when (osArch) {
        "aarch64" -> osArch
        "amd64", "x86_64" -> "x64"
        else -> error("Unsupported arch '$osArch'")
    }
    return "${os}_$arch"
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
    // Pack the shared library
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

// Kotlin/Native mingwX64
tasks.named("cinteropQuickjsMingwX64") {
    dependsOn(buildQuickJsNativeLibsTask.name)
}

tasks.register("cleanQuickJSBuild") {
    doLast {
        delete(nativeBuildDir)
    }
}
tasks.named("clean") {
    dependsOn("cleanQuickJSBuild")
}

fun quickJsVersion(): String {
    return File(projectDir, "native/quickjs/VERSION").readText().trim()
}

/// Multiplatform JDK locations

fun windowX64JavaHome(): String = requireNotNull(localPropOf("java_home.windows_x64")) {
    "'java_home.windows_x64' not found in local.properties"
}

fun linuxX64JavaHome(): String = requireNotNull(localPropOf("java_home.linux_x64")) {
    "'java_home.linux_x64' not found in local.properties"
}

fun linuxAarch64JavaHome(): String = requireNotNull(localPropOf("java_home.linux_aarch64")) {
    "'java_home.linux_aarch64' not found in local.properties"
}

fun macosX64JavaHome(): String = requireNotNull(localPropOf("java_home.macos_x64")) {
    "'java_home.macos_x64' not found in local.properties"
}

fun macosAarch64JavaHome(): String = requireNotNull(localPropOf("java_home.macos_aarch64")) {
    "'java_home.macos_aarch64' not found in local.properties"
}

val localProperties = Properties()
val localPropertiesFile = project.rootDir.resolve("local.properties")
if (localPropertiesFile.exists()) {
    localPropertiesFile.inputStream().use {
        localProperties.load(it)
    }
} else {
    error("Missing local.properties")
}

fun localPropOf(key: String) = localProperties[key]?.toString()