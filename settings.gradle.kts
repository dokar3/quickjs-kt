pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.PREFER_PROJECT)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "quickjs-kt"

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

include(":quickjs")
include(":quickjs-converter-ktxserialization")
include(":quickjs-converter-moshi")
include(":samples:js-eval")
include(":samples:js-eval-android")
include(":samples:repl")
include(":samples:openai")
include(":samples:openai-android")
include(":benchmark")
include(":integration-test")
