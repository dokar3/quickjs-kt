package com.dokar.quickjs

import org.gradle.api.Plugin
import org.gradle.api.Project

class QuickJsNativeBuildPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        project.afterEvaluate {
            applyQuickJsNativeBuildTasks(project.file("native/CMakeLists.txt"))
        }
    }
}

class DisableUnsupportedPlatformTasksPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        project.disableUnsupportedPlatformTasks()
    }
}
