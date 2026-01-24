import com.dokar.quickjs.disableUnsupportedPlatformTasks

plugins {
    alias(libs.plugins.kotlinMultiplatform)
}

kotlin {
    jvm {
    }

    mingwX64 {
        binaries {
            executable()
        }
    }
    linuxX64 {
        binaries {
            executable()
        }
    }
    macosArm64 {
        binaries {
            executable()
        }
    }

    applyDefaultHierarchyTemplate()

    jvmToolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(projects.quickjs)
                implementation(libs.kotlinx.coroutines.core)
                implementation(libs.clikt)
            }
        }
    }
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

// application {
//     mainClass.set("ReplMainKt")
// }

disableUnsupportedPlatformTasks()
