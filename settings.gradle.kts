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
include(":samples:js-eval")
include(":samples:repl")
include(":samples:openai")
include(":benchmark")
