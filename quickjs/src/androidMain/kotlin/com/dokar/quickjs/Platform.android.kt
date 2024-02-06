package com.dokar.quickjs

actual fun loadNativeLibrary(libraryName: String) {
    System.loadLibrary(libraryName)
}