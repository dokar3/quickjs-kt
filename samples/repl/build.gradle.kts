@file:OptIn(org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeCacheApi::class)

import com.dokar.quickjs.disableUnsupportedPlatformTasks
import org.jetbrains.kotlin.gradle.plugin.mpp.DisableCacheInKotlinVersion

plugins {
    alias(libs.plugins.kotlinMultiplatform)
}

kotlin {
    jvm {
    }

    mingwX64 {
        binaries {
            executable {
                disableNativeCache(
                    version = DisableCacheInKotlinVersion.`2_3_20`,
                    reason = "Kotlin 2.3.20 native cache regression"
                )
            }
        }
    }
    linuxX64 {
        binaries {
            executable {
                disableNativeCache(
                    version = DisableCacheInKotlinVersion.`2_3_20`,
                    reason = "Kotlin 2.3.20 native cache regression"
                )
            }
        }
    }
    macosArm64 {
        binaries {
            executable {
                disableNativeCache(
                    version = DisableCacheInKotlinVersion.`2_3_20`,
                    reason = "Kotlin 2.3.20 native cache regression"
                )
            }
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
