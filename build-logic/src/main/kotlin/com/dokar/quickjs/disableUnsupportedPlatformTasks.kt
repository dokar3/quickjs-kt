package com.dokar.quickjs

import org.gradle.api.Project

fun Project.disableUnsupportedPlatformTasks() {
    val isPublishing = gradle.startParameter.taskNames.any {
        it.contains("publish", ignoreCase = true)
    }

    tasks.configureEach {
        val taskName = name.lowercase()
        val platform = when {
            taskName.contains("linuxx64") -> Platform.linux_x64
            taskName.contains("linuxarm64") -> Platform.linux_aarch64
            taskName.contains("mingwx64") -> Platform.windows_x64
            taskName.contains("macosx64") -> Platform.macos_x64
            taskName.contains("macosarm64") -> Platform.macos_aarch64
            taskName.contains("iosx64") -> Platform.ios_x64
            taskName.contains("iosarm64") -> Platform.ios_aarch64
            taskName.contains("iossimulatorarm64") -> Platform.ios_simulator_aarch64
            else -> null
        } ?: return@configureEach

        enabled = if (isPublishing) {
            when (currentPlatform) {
                Platform.linux_x64,
                Platform.linux_aarch64 -> {
                    platform == Platform.linux_x64 || platform == Platform.linux_aarch64
                }

                Platform.windows_x64 -> platform == Platform.windows_x64

                Platform.macos_x64,
                Platform.macos_aarch64 -> true

                else -> false
            }
        } else {
            when (platform) {
                Platform.linux_x64 -> currentPlatform == Platform.linux_x64
                Platform.linux_aarch64 -> currentPlatform == Platform.linux_aarch64
                Platform.windows_x64 -> currentPlatform == Platform.windows_x64
                Platform.macos_x64 -> currentPlatform == Platform.macos_x64
                Platform.macos_aarch64 -> currentPlatform == Platform.macos_aarch64
                Platform.ios_x64 -> currentPlatform == Platform.macos_x64
                Platform.ios_aarch64,
                Platform.ios_simulator_aarch64 -> currentPlatform == Platform.macos_aarch64
            }
        }
    }
}
