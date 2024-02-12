package com.dokar.quickjs.bridge

import com.dokar.quickjs.MemoryUsage
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import quickjs.JSMemoryUsage
import quickjs.JSRuntime
import quickjs.JS_ComputeMemoryUsage

@OptIn(ExperimentalForeignApi::class)
internal fun CPointer<JSRuntime>.ktMemoryUsage(): MemoryUsage = memScoped {
    val usage = alloc<JSMemoryUsage>()
    JS_ComputeMemoryUsage(this@ktMemoryUsage, usage.ptr)
    MemoryUsage(
        mallocLimit = usage.malloc_limit,
        mallocSize = usage.malloc_size,
        mallocCount = usage.malloc_count,
        memoryUsedSize = usage.memory_used_size,
        memoryUsedCount = usage.memory_used_count,
    )
}