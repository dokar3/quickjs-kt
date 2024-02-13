package com.dokar.quickjs

import org.gradle.api.Project

fun Project.disableUnsupportedPlatformTasks() {
    tasks.configureEach {
        val isPublishing = gradle.startParameter.taskNames.contains("publish")
        if (isPublishing) {
            return@configureEach
        }
        val taskName = name.lowercase()
        if (taskName.contains("linuxx64")) {
            enabled = currentPlatform == Platform.linux_x64
        } else if (taskName.contains("linuxarm64")) {
            enabled = currentPlatform == Platform.linux_aarch64
        } else if (taskName.contains("mingwx64")) {
            enabled = currentPlatform == Platform.windows_x64
        } else if (taskName.contains("macosx64")) {
            enabled = currentPlatform == Platform.macos_x64
        } else if (taskName.contains("macosarm64")) {
            enabled = currentPlatform == Platform.macos_aarch64
        }
    }
}