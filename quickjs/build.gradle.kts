/// Based on https://github.com/cashapp/zipline/blob/trunk/zipline/build.gradle.kts
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
    }
}

val jniCmakeFile = file("src/jniMain/CMakeLists.txt")

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

val jniLibraryPlatforms = listOf(
    "windows_x64",
    "linux_x64",
    "linux_aarch64",
    "macos_x64",
    "macos_aarch64",
)

val jniBuildDir = File(projectDir, "/src/jniMain/build")

// Task to build multiplatform jni libraries
tasks.register("jniBuildQuickJs") {
    inputs.dir(File(projectDir, "/native"))
    inputs.dir(File(projectDir, "/src/jniMain/cmake"))
    inputs.file(File(projectDir, "/src/jniMain/CMakeLists.txt"))

    outputs.dir(jniBuildDir)

    fun buildLibrary(platform: String, release: Boolean) {
        println("Building native library for target '$platform'...")

        val commonArgs = arrayOf(
            "-B",
            "build/$platform",
            "-G Ninja",
            "-DCMAKE_BUILD_TYPE=${if (release) "MinSizeRel" else "Debug"}",
            "-DTARGET_PLATFORM=$platform",
        )

        fun javaHomeArg(home: String): String {
            return "-DPLATFORM_JAVA_HOME=$home"
        }

        val args = when (platform) {
            "windows_x64" -> commonArgs + javaHomeArg(windowX64JavaHome())
            "linux_x64" -> commonArgs + javaHomeArg(linuxX64JavaHome())
            "linux_aarch64" -> commonArgs + javaHomeArg(linuxAarch64JavaHome())
            "macos_x64" -> commonArgs + javaHomeArg(macosX64JavaHome())
            "macos_aarch64" -> commonArgs + javaHomeArg(macosAarch64JavaHome())
            else -> error("Unsupported platform: '$platform'")
        }

        fun runCommand(vararg args: Any) {
            exec {
                workingDir = jniCmakeFile.parentFile
                standardOutput = System.out
                errorOutput = System.err
                commandLine(*args)
            }
        }

        // Generate build files
        runCommand("cmake", *args, "./")
        // Build
        runCommand("cmake", "--build", args[1])
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

    doLast {
        val isPublishing = gradle.startParameter.taskNames.contains("publish")
        if (isPublishing) {
            for (platform in jniLibraryPlatforms) {
                try {
                    buildLibrary(platform, true)
                } catch (e: Exception) {
                    error(e)
                }
            }
        } else {
            buildLibrary(currentPlatform(), false)
        }
    }
}

val copyQjsSharedLibTask = tasks.register("copyQuickJsSharedLib") {
    dependsOn("jniBuildQuickJs")

    val outputFiles = tasks.getByName("jniBuildQuickJs").outputs.files
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
    dependsOn(copyQjsSharedLibTask.name)
}

tasks.withType<Jar>().configureEach {
    dependsOn(copyQjsSharedLibTask.name)
    // Pack the shared library
    from(File(layout.buildDirectory.asFile.get(), "libs")) {
        include("jni/")
    }
}

// Jvm test: Copy shared libs to classes dir
tasks.named("jvmTest") {
    dependsOn(copyQjsSharedLibTask.name)
    doFirst {
        val libDir = tasks.getByName(copyQjsSharedLibTask.name).outputs.files.first()
        copy {
            from(libDir.parentFile)
            into(project.layout.buildDirectory.dir("classes/kotlin/jvm/test"))
            include("${libDir.name}/**")
        }
    }
}

tasks.register("cleanQuickJSBuild") {
    doLast {
        delete(jniBuildDir)
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