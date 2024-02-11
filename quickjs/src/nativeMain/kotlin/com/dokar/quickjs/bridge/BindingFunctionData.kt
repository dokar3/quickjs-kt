package com.dokar.quickjs.bridge

import com.dokar.quickjs.JSContext
import com.dokar.quickjs.JSValue
import com.dokar.quickjs.JS_ToCString
import com.dokar.quickjs.JS_ToInt64
import com.dokar.quickjs.QuickJs
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.alloc
import kotlinx.cinterop.asStableRef
import kotlinx.cinterop.get
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.readValue
import kotlinx.cinterop.toCPointer
import kotlinx.cinterop.toKStringFromUtf8
import kotlinx.cinterop.value
import platform.posix.int64_tVar

internal data class BindingFunctionData(
    val name: String,
    val quickJs: QuickJs,
    val objectHandle: Long,
) {
    companion object {
        @OptIn(ExperimentalForeignApi::class)
        fun fromJsValues(
            ctx: CPointer<JSContext>?,
            data: CPointer<JSValue>,
        ): BindingFunctionData = memScoped {
            // Read property name
            val name = JS_ToCString(ctx, data[0].readValue())!!.toKStringFromUtf8()

            // Read the QuickJs instance
            val qjsAddress = alloc<int64_tVar>()
            JS_ToInt64(ctx, qjsAddress.ptr, data[1].readValue())
            val qjsVoidPtr = qjsAddress.value.toCPointer<int64_tVar>()
            val qjsStableRef = qjsVoidPtr!!.asStableRef<QuickJs>()
            val quickJs = qjsStableRef.get()

            // Read object handle
            val objectHandle = alloc<int64_tVar>()
            JS_ToInt64(ctx, objectHandle.ptr, data[2].readValue())

            BindingFunctionData(
                name = name,
                quickJs = quickJs,
                objectHandle = objectHandle.value,
            )
        }
    }
}
