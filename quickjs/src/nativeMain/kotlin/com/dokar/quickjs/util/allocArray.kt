package com.dokar.quickjs.util

import kotlinx.cinterop.CArrayPointer
import kotlinx.cinterop.CValues
import kotlinx.cinterop.CVariable
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.MemScope
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.ptr

@ExperimentalForeignApi
internal inline fun <reified T : CVariable> MemScope.allocArrayOf(
    vararg elements: CValues<T>
): CArrayPointer<T> {
    val array = allocArray<T>(elements.size) {
        elements[it].place(this.ptr)
    }
    return array
}