plugins {
    id("application")
    alias(libs.plugins.kotlinJvm)
    id("com.github.johnrengelman.shadow") version "8.1.1"
}

kotlin {
    jvmToolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}

dependencies {
    implementation(projects.quickjs)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.clikt)
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

application {
    mainClass.set("com.dokar.quickjs.sample.repl.ReplMainKt")
}