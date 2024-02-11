package com.dokar.quickjs.bridge

import com.dokar.quickjs.JSContext
import com.dokar.quickjs.JSValue
import com.dokar.quickjs.JS_FreeValue
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.CValue
import kotlinx.cinterop.ExperimentalForeignApi

@OptIn(ExperimentalForeignApi::class)
internal inline fun <T> CValue<JSValue>.use(
    context: CPointer<JSContext>,
    block: CValue<JSValue>.() -> T
): T {
    try {
        return block()
    } finally {
        JS_FreeValue(context, this)
    }
}