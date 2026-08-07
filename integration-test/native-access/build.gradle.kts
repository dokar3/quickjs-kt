import org.gradle.api.tasks.Exec
import org.gradle.api.tasks.testing.Test
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget
import org.jetbrains.kotlin.gradle.targets.native.tasks.KotlinNativeTest
import java.io.File
import java.util.Locale

plugins {
    alias(libs.plugins.kotlinMultiplatform)
}

repositories {
    mavenLocal()
    mavenCentral()
}

val quickjsVersion = providers.gradleProperty("VERSION_NAME").get()

val quickjsClasspath = configurations.create("quickjsClasspath")
dependencies {
    quickjsClasspath("io.github.dokar3:quickjs-kt-jvm:$quickjsVersion")
}

val nativeBuildDirectory = layout.buildDirectory.dir("native")
val nativeCmakeDirectory = nativeBuildDirectory.map { it.dir("cmake") }
val nativeOutputDirectory = nativeBuildDirectory.map { it.dir("output") }
val quickjsExtractedDirectory = nativeBuildDirectory.map { it.dir("extracted_quickjs") }

val currentOs = System.getProperty("os.name").lowercase(Locale.US)
val currentArchitecture = System.getProperty("os.arch").lowercase(Locale.US)
val currentPlatform = when {
    currentOs.contains("linux") -> "linux"
    currentOs.contains("mac") || currentOs.contains("osx") -> "macos"
    currentOs.contains("windows") -> "windows"
    else -> error("Unsupported operating system: $currentOs")
}
val currentArchitectureName = when (currentArchitecture) {
    "aarch64", "arm64" -> "aarch64"
    "amd64", "x86_64" -> "x64"
    else -> error("Unsupported architecture: $currentArchitecture")
}
val currentTarget = "${currentPlatform}_$currentArchitectureName"
val isWindows = currentPlatform == "windows"
val nativeLibraryExtension = when (currentPlatform) {
    "linux" -> "so"
    "macos" -> "dylib"
    "windows" -> "dll"
    else -> error("Unsupported platform: $currentPlatform")
}
val nativeLibraryFileName = if (isWindows) {
    "quickjs_native_operations.dll"
} else {
    "libquickjs_native_operations.$nativeLibraryExtension"
}
val quickjsLibraryFileName = if (isWindows) {
    "libquickjs.dll"
} else {
    "libquickjs.$nativeLibraryExtension"
}
val nativeLibraryFile = nativeOutputDirectory.map {
    it.file(nativeLibraryFileName)
}

val quickjsLibraryFile = quickjsExtractedDirectory.map {
    it.file(quickjsLibraryFileName)
}

val extractQuickJsLibrary = tasks.register("extractQuickJsLibrary") {
    group = "verification"
    description = "Extracts libquickjs native library from the published mavenLocal JVM artifact."
    dependsOn(":quickjs:publishToMavenLocal")
    inputs.files(quickjsClasspath)
    outputs.file(quickjsLibraryFile)
    doLast {
        val jarFile = quickjsClasspath.files.firstOrNull { it.name.endsWith(".jar") }
            ?: error("Could not resolve published quickjs-kt-jvm jar from mavenLocal")

        val entryPath = "jni/$currentTarget/$quickjsLibraryFileName"

        val extractedDirectory = quickjsExtractedDirectory.get().asFile
        delete(extractedDirectory)

        val destFile = quickjsLibraryFile.get().asFile
        destFile.parentFile.mkdirs()

        val extracted = zipTree(jarFile).matching { include(entryPath) }.singleFile
        extracted.copyTo(destFile, overwrite = true)
    }
}

val nativeLibrarySearchPath = if (isWindows) {
    val systemPath = System.getenv().entries
        .firstOrNull { it.key.equals("PATH", ignoreCase = true) }
        ?.value
        .orEmpty()
    listOf(
        nativeOutputDirectory.get().asFile.absolutePath,
        nativeOutputDirectory.get().asFile.resolve("Debug").absolutePath,
        nativeOutputDirectory.get().asFile.resolve("Release").absolutePath,
        quickjsExtractedDirectory.get().asFile.absolutePath,
        systemPath,
    ).joinToString(File.pathSeparator)
} else {
    null
}

val quickjsHeaderDirectory = nativeBuildDirectory.map { it.dir("include") }
val quickjsHeaderFile = quickjsHeaderDirectory.map { it.file("quickjs.h") }

val prepareQuickJsHeader = tasks.register("prepareQuickJsHeader") {
    group = "verification"
    description = "Rewrites quickjs.h into build dir, resolving MSVC struct cast compatibility."
    val originalHeader = rootDir.resolve("quickjs/native/quickjs/quickjs.h")
    inputs.file(originalHeader)
    outputs.file(quickjsHeaderFile)
    doLast {
        val originalContent = originalHeader.readText()
        val fixedContent = originalContent.replace("return (JSValue)v;", "return v;")
        require(fixedContent != originalContent) { "Failed to patch return cast in quickjs.h" }
        val outFile = quickjsHeaderFile.get().asFile
        outFile.parentFile.mkdirs()
        outFile.writeText(fixedContent)
    }
}

val configureNativeOperations = tasks.register("configureNativeOperations", Exec::class.java) {
    group = "verification"
    description = "Configures the consumer-style native QuickJS integration library."
    dependsOn(extractQuickJsLibrary, prepareQuickJsHeader)
    inputs.files(
        file("native/CMakeLists.txt"),
        file("native/native_operations.c"),
        file("native/native_operations.h"),
        quickjsHeaderFile,
        quickjsLibraryFile,
    )
    doFirst {
        nativeCmakeDirectory.get().asFile.mkdirs()
        nativeOutputDirectory.get().asFile.mkdirs()
        executable("cmake")
        args(
            "-S", file("native").absolutePath,
            "-B", nativeCmakeDirectory.get().asFile.absolutePath,
            "-DCMAKE_BUILD_TYPE=Release",
            "-DJAVA_HOME=${File(System.getProperty("java.home")).absolutePath}",
            "-DQUICKJS_HEADER_DIR=${quickjsHeaderDirectory.get().asFile.absolutePath}",
            "-DQUICKJS_LIBRARY=${quickjsLibraryFile.get().asFile.absolutePath}",
            "-DNATIVE_OUTPUT_DIR=${nativeOutputDirectory.get().asFile.absolutePath}",
        )
    }
}

val buildNativeOperations = tasks.register("buildNativeOperations", Exec::class.java) {
    group = "verification"
    description = "Builds the consumer-style native QuickJS integration library."
    dependsOn(configureNativeOperations)
    inputs.files(
        file("native/native_operations.c"),
        file("native/native_operations.h"),
    )
    outputs.file(nativeLibraryFile)
    executable("cmake")
    args("--build", nativeCmakeDirectory.get().asFile.absolutePath)
}

kotlin {
    jvm()
    when {
        currentPlatform == "windows" -> mingwX64()
        currentPlatform == "linux" && currentArchitectureName == "aarch64" -> linuxArm64()
        currentPlatform == "linux" -> linuxX64()
        currentPlatform == "macos" && currentArchitectureName == "aarch64" -> macosArm64()
        currentPlatform == "macos" -> macosX64()
    }

    applyDefaultHierarchyTemplate()

    jvmToolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }

    targets.withType<KotlinNativeTarget>().configureEach {
        binaries.configureEach {
            if (compilation.name == "test") {
                linkerOpts(
                    "-L${nativeOutputDirectory.get().asFile.absolutePath}",
                    "-lquickjs_native_operations",
                )
                if (isWindows) {
                    linkerOpts("-Wl,--export-all-symbols")
                }
                if (!isWindows) {
                    linkerOpts(
                        "-rpath",
                        nativeOutputDirectory.get().asFile.absolutePath,
                    )
                }
            }
        }

        compilations.getByName("main").cinterops {
            create("nativeOperations") {
                headers(file("native/native_operations.h"))
                packageName("nativeoperations")
                compilerOpts(
                    "-I${file("native").absolutePath}",
                    "-I${quickjsHeaderDirectory.get().asFile.absolutePath}",
                )
            }
        }
    }

    sourceSets {
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation("io.github.dokar3:quickjs-kt:$quickjsVersion")
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}

tasks.configureEach {
    if (name.startsWith("cinteropNativeOperations")) {
        dependsOn(buildNativeOperations)
    }
    if (name.contains("Test", ignoreCase = true)) {
        dependsOn(buildNativeOperations)
    }
}

tasks.withType<Test>().configureEach {
    systemProperty("quickjs.native.operations.path", nativeLibraryFile.get().asFile.absolutePath)
    nativeLibrarySearchPath?.let { environment("PATH", it) }
}

tasks.withType<KotlinNativeTest>().configureEach {
    nativeLibrarySearchPath?.let { environment("PATH", it) }
}
