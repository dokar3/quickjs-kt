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
        mallocCount = usage.malloc_count,
        mallocSize = usage.malloc_size,
        memoryUsedCount = usage.memory_used_count,
        memoryUsedSize = usage.memory_used_size,
        atomCount = usage.atom_count,
        atomSize = usage.atom_size,
        strCount = usage.str_count,
        strSize = usage.str_size,
        objCount = usage.obj_count,
        objSize = usage.obj_size,
        propCount = usage.prop_count,
        propSize = usage.prop_size,
        shapeCount = usage.shape_count,
        shapeSize = usage.shape_size,
        jsFuncCount = usage.js_func_count,
        jsFuncSize = usage.js_func_size,
        jsFuncCodeSize = usage.js_func_code_size,
        jsFuncPc2lineCount = usage.js_func_pc2line_count,
        jsFuncPc2lineSize = usage.js_func_pc2line_size,
        cFuncCount = usage.c_func_count,
        arrayCount = usage.array_count,
        fastArrayCount = usage.fast_array_count,
        fastArrayElements = usage.fast_array_elements,
        binaryObjectCount = usage.binary_object_count,
        binaryObjectSize = usage.binary_object_size,

        )
}