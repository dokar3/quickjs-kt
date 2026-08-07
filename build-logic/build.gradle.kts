plugins {
    `kotlin-dsl`
    `java-gradle-plugin`
}

repositories {
    google()
    mavenCentral()
    gradlePluginPortal()
}

gradlePlugin {
    plugins {
        create("quickJsNativeBuild") {
            id = "com.dokar.quickjs.native-build"
            implementationClass = "com.dokar.quickjs.QuickJsNativeBuildPlugin"
        }
        create("disableUnsupportedPlatformTasks") {
            id = "com.dokar.quickjs.disable-unsupported-platform-tasks"
            implementationClass = "com.dokar.quickjs.DisableUnsupportedPlatformTasksPlugin"
        }
    }
}
