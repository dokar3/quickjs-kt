import com.dokar.quickjs.disableUnsupportedPlatformTasks

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.kotlinBenchmark)
    alias(libs.plugins.kotlinAllOpen)
}

kotlin {
    jvm()

    mingwX64()
    linuxX64()

    sourceSets {
        commonMain {
            dependencies {
                implementation(projects.quickjs)
                implementation(libs.kotlinx.coroutines.core)
                implementation(libs.kotlinx.benchmark.runtime)
            }
        }
    }
}

benchmark {
    targets {
        register("jvm")
        register("mingwX64")
        register("linuxX64")
    }
}

allOpen {
    annotation("org.openjdk.jmh.annotations.State")
}

disableUnsupportedPlatformTasks()
