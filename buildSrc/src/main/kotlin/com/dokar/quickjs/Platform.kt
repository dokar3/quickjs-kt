package com.dokar.quickjs

enum class Platform(
    val osName: String,
    val osArch: String,
) {
    linux_aarch64("linux", "aarch64"),
    linux_x64("linux", "x64"),
    macos_aarch64("macos", "aarch64"),
    macos_x64("macos", "x64"),
    ios_x64("ios", "x64"),
    ios_aarch64("ios", "aarch64"),
    ios_aarch64_simulator("ios_simulator", "aarch64"),
    windows_x64("windows", "x64");
}

val currentPlatform: Platform by lazy { currentPlatform() }

private fun currentPlatform(): Platform {
    val osName = System.getProperty("os.name").lowercase()
    val osArch = System.getProperty("os.arch").lowercase()
    val name = if (osName.contains("windows")) {
        "windows"
    } else if (osName.contains("linux")) {
        "linux"
    } else if (osName.contains("mac")) {
        "macos"
    } else {
        osName
    }
    val arch = when (osArch) {
        "aarch64" -> osArch
        "amd64", "x86_64" -> "x64"
        else -> osArch
    }
    for (platform in Platform.values()) {
        if (platform.osName == name && platform.osArch == arch) {
            return platform
        }
    }
    throw IllegalStateException("Unsupported os, name: $osName, arch: $osArch")
}