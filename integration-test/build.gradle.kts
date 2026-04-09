import com.dokar.quickjs.disableUnsupportedPlatformTasks
import org.gradle.api.tasks.Exec
import org.gradle.api.tasks.JavaExec
import java.util.Locale

plugins {
    alias(libs.plugins.kotlinMultiplatform)
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

    sourceSets {
        commonMain.dependencies {
            implementation(projects.quickjs)
            implementation(libs.kotlinx.coroutines.core)
        }

        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(projects.quickjs)
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}

repositories {
    mavenLocal()
    mavenCentral()
}

val quickjsProject = project(":quickjs")
val integrationMainClass = "com.dokar.quickjs.integration.MainKt"
val graalVmHome = providers.environmentVariable("GRAALVM_HOME")
val hasGraalVmHome = graalVmHome.isPresent
val nativeImageName = "quickjs-integration-test"

fun currentOsName(): String {
    val osName = System.getProperty("os.name").lowercase(Locale.US)
    return when {
        osName.contains("linux") -> "linux"
        osName.contains("mac") || osName.contains("osx") -> "macos"
        osName.contains("windows") -> "windows"
        else -> error("Unsupported operating system: $osName")
    }
}

fun currentArchName(): String {
    val arch = System.getProperty("os.arch").lowercase(Locale.US)
    return when (arch) {
        "aarch64", "arm64" -> "aarch64"
        "amd64", "x86_64" -> "x64"
        else -> error("Unsupported architecture: $arch")
    }
}

fun currentQuickJsPlatform(): String = "${currentOsName()}_${currentArchName()}"

fun currentSharedLibraryExtension(): String = when (currentOsName()) {
    "linux" -> "so"
    "macos" -> "dylib"
    "windows" -> "dll"
    else -> error("Unsupported operating system: ${currentOsName()}")
}

fun graalVmExecutable(name: String): String {
    val home = graalVmHome.orNull ?: error("GRAALVM_HOME is not set.")
    val executableName = when {
        currentOsName() == "windows" && name == "native-image" -> "native-image.cmd"
        currentOsName() == "windows" -> "$name.exe"
        else -> name
    }
    return file("$home/bin/$executableName").absolutePath
}

val quickJsLibraryName = "libquickjs.${currentSharedLibraryExtension()}"
val quickJsLibraryDir = quickjsProject.layout.projectDirectory.dir(
    "native/build/jni_libs/${currentQuickJsPlatform()}"
)
val graalVmConfigDir = quickjsProject.layout.projectDirectory.dir("src/jniMain/resources/META-INF/native-image/com.dokar.quickjs/quickjs/")
val graalVmAgentDir = layout.buildDirectory.dir("graalvm/agent")
val graalVmNativeDir = layout.buildDirectory.dir("graalvm/native")
val graalVmNativeExecutable = graalVmNativeDir.map { directory ->
    val suffix = if (currentOsName() == "windows") ".exe" else ""
    directory.file("$nativeImageName$suffix")
}

val jvmMainClasses = tasks.named("jvmMainClasses")
val jvmRuntimeClasspath = configurations.named("jvmRuntimeClasspath")
val jvmClassesDir = layout.buildDirectory.dir("classes/kotlin/jvm/main")
val jvmResourcesDir = layout.buildDirectory.dir("processedResources/jvm/main")
val integrationRuntimeClasspath = files(
    jvmClassesDir,
    jvmResourcesDir,
    jvmRuntimeClasspath,
)

fun JavaExec.configureQuickJsRuntime() {
    dependsOn(quickjsProject.tasks.named("buildQuickJsJniLibs"))
    dependsOn(jvmMainClasses)
    classpath = integrationRuntimeClasspath
    mainClass.set(integrationMainClass)
    workingDir = projectDir
    systemProperty("com.dokar.quickjs.library.path", quickJsLibraryDir.asFile.absolutePath)
    systemProperty("com.dokar.quickjs.library.name", quickJsLibraryName)
}

fun Exec.skipIfGraalVmUnavailable() {
    onlyIf("GRAALVM_HOME is set") {
        if (!hasGraalVmHome) {
            logger.lifecycle("Skipping $path because GRAALVM_HOME is not set.")
        }
        hasGraalVmHome
    }
}

tasks.register("integrationJvmRun", JavaExec::class.java) {
    group = "verification"
    description = "Runs the QuickJS integration scenario on the JVM."
    configureQuickJsRuntime()
}

tasks.register("graalVmAgent", Exec::class.java) {
    group = "verification"
    description = "Runs the QuickJS integration scenario with the GraalVM native-image agent."
    skipIfGraalVmUnavailable()
    dependsOn(quickjsProject.tasks.named("buildQuickJsJniLibs"))
    dependsOn(jvmMainClasses)
    inputs.files(integrationRuntimeClasspath)
    outputs.dir(graalVmAgentDir)
    workingDir = projectDir

    doFirst {
        delete(graalVmAgentDir)
        graalVmAgentDir.get().asFile.mkdirs()
        executable(graalVmExecutable("java"))
        args(
            "-agentlib:native-image-agent=config-output-dir=${graalVmAgentDir.get().asFile.absolutePath}",
            "-Dcom.dokar.quickjs.library.path=${quickJsLibraryDir.asFile.absolutePath}",
            "-Dcom.dokar.quickjs.library.name=$quickJsLibraryName",
            "-cp",
            integrationRuntimeClasspath.asPath,
            integrationMainClass,
        )
    }
}

tasks.register("graalVmNativeImage", Exec::class.java) {
    group = "verification"
    description = "Builds a GraalVM native image for the QuickJS integration scenario."
    skipIfGraalVmUnavailable()
    dependsOn(jvmMainClasses)
    inputs.files(integrationRuntimeClasspath, graalVmConfigDir.asFile)
    outputs.file(graalVmNativeExecutable)
    workingDir = projectDir

    doFirst {
        delete(graalVmNativeDir)
        graalVmNativeDir.get().asFile.mkdirs()
        executable(graalVmExecutable("native-image"))
        args(
            "--no-fallback",
            "-Ob",
            "-H:+ReportExceptionStackTraces",
            "-H:Name=$nativeImageName",
            "-H:Path=${graalVmNativeDir.get().asFile.absolutePath}",
            "-H:ConfigurationFileDirectories=${graalVmConfigDir.asFile.absolutePath}",
            "-cp",
            integrationRuntimeClasspath.asPath,
            integrationMainClass,
        )
    }
}

tasks.register("integrationGraalVmRun", Exec::class.java) {
    group = "verification"
    description = "Runs the GraalVM native image for the QuickJS integration scenario."
    skipIfGraalVmUnavailable()
    dependsOn("graalVmNativeImage")
    dependsOn(quickjsProject.tasks.named("buildQuickJsJniLibs"))
    inputs.file(graalVmNativeExecutable)
    workingDir = projectDir

    doFirst {
        executable(graalVmNativeExecutable.get().asFile.absolutePath)
        args(
            "-Dcom.dokar.quickjs.library.path=${quickJsLibraryDir.asFile.absolutePath}",
            "-Dcom.dokar.quickjs.library.name=$quickJsLibraryName",
        )
    }
}

tasks.named("check") {
    dependsOn("integrationJvmRun")
    dependsOn("integrationGraalVmRun")
}

disableUnsupportedPlatformTasks()
