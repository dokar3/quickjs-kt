package com.dokar.quickjs

internal actual fun loadNativeLibrary(libraryName: String) {
    System.loadLibrary(libraryName)
}