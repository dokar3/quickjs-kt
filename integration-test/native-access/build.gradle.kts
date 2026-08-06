import com.dokar.quickjs.disableUnsupportedPlatformTasks
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

val quickjsProject = project(":quickjs")
val nativeBuildDirectory = layout.buildDirectory.dir("native")
val nativeCmakeDirectory = nativeBuildDirectory.map { it.dir("cmake") }
val nativeOutputDirectory = nativeBuildDirectory.map { it.dir("output") }
val currentOs = System.getProperty("os.name").lowercase(Locale.US)
val currentArchitecture = System.getProperty("os.arch").lowercase(Locale.US)
val nativeLibraryExtension = when {
    currentOs.contains("linux") -> "so"
    currentOs.contains("mac") || currentOs.contains("osx") -> "dylib"
    currentOs.contains("windows") -> "dll"
    else -> error("Unsupported operating system: $currentOs")
}
val nativeLibraryFile = nativeOutputDirectory.map {
    it.file(
        if (nativeLibraryExtension == "dll") {
            "quickjs_native_operations.$nativeLibraryExtension"
        } else {
            "libquickjs_native_operations.$nativeLibraryExtension"
        }
    )
}
val quickjsLibraryDirectory = quickjsProject.layout.projectDirectory.dir(
    "native/build/jni_libs/${when {
        currentOs.contains("linux") -> "linux"
        currentOs.contains("mac") || currentOs.contains("osx") -> "macos"
        currentOs.contains("windows") -> "windows"
        else -> error("Unsupported operating system: $currentOs")
    }}_${when (currentArchitecture) {
        "aarch64", "arm64" -> "aarch64"
        "amd64", "x86_64" -> "x64"
        else -> error("Unsupported architecture: $currentArchitecture")
    }}"
)
val quickjsLibraryFile = quickjsLibraryDirectory.file(
    if (nativeLibraryExtension == "dll") "libquickjs.dll" else "libquickjs.$nativeLibraryExtension"
)
val nativeLibrarySearchPath = if (nativeLibraryExtension == "dll") {
    val systemPath = System.getenv().entries
        .firstOrNull { it.key.equals("PATH", ignoreCase = true) }
        ?.value
        .orEmpty()
    listOf(
        nativeOutputDirectory.get().asFile.absolutePath,
        quickjsLibraryDirectory.asFile.absolutePath,
        systemPath,
    ).joinToString(File.pathSeparator)
} else {
    null
}

val configureNativeOperations = tasks.register("configureNativeOperations", Exec::class.java) {
    group = "verification"
    description = "Configures the consumer-style native QuickJS integration library."
    dependsOn(quickjsProject.tasks.named("buildQuickJsJniLibs"))
    inputs.files(
        file("native/CMakeLists.txt"),
        file("native/native_operations.c"),
        file("native/native_operations.h"),
        quickjsLibraryFile,
    )
    doFirst {
        nativeCmakeDirectory.get().asFile.mkdirs()
        nativeOutputDirectory.get().asFile.mkdirs()
        executable("cmake")
        args(
            "-S", file("native").absolutePath,
            "-B", nativeCmakeDirectory.get().asFile.absolutePath,
            "-DCMAKE_BUILD_TYPE=Debug",
            "-DJAVA_HOME=${File(System.getProperty("java.home")).absolutePath}",
            "-DQUICKJS_ROOT=${quickjsProject.projectDir.absolutePath}",
            "-DQUICKJS_LIBRARY=${quickjsLibraryFile.asFile.absolutePath}",
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
    mingwX64()
    linuxX64()
    linuxArm64()
    macosX64()
    macosArm64()

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
                if (nativeLibraryExtension != "dll") {
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
                compilerOpts("-I${file("native").absolutePath}")
            }
        }
    }

    sourceSets {
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(projects.quickjs)
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

disableUnsupportedPlatformTasks()
