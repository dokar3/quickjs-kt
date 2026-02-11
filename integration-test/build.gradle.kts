import com.dokar.quickjs.disableUnsupportedPlatformTasks

plugins {
    alias(libs.plugins.kotlinMultiplatform)
}

val quickjsVersion: String = property("VERSION_NAME") as String

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
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation("io.github.dokar3:quickjs-kt:$quickjsVersion")
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}

repositories {
    mavenLocal()
    mavenCentral()
}

disableUnsupportedPlatformTasks()
