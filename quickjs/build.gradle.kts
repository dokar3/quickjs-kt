/// Based on https://github.com/cashapp/zipline/blob/trunk/zipline/build.gradle.kts
import com.dokar.quickjs.applyQuickJsNativeBuildTasks
import com.dokar.quickjs.currentPlatform
import com.dokar.quickjs.disableUnsupportedPlatformTasks
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget

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
    linuxX64()
    linuxArm64()
    if (currentPlatform.osName == "macos") {
        macosX64()
        macosArm64()
    }

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
                    headers(
                        file("native/quickjs/quickjs.h"),
                        file("native/common/quickjs_version.h"),
                    )
                    packageName("quickjs")
                }
            }
        }
    }
}

val cmakeFile = file("native/CMakeLists.txt")

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
            path = cmakeFile
        }
    }
}

applyQuickJsNativeBuildTasks(cmakeFile)

disableUnsupportedPlatformTasks()

afterEvaluate {
    // Disable Android tests
    tasks.named("testDebugUnitTest").configure {
        enabled = false
    }
    tasks.named("testReleaseUnitTest").configure {
        enabled = false
    }
}
