package com.dokar.quickjs.bridge

import kotlinx.cinterop.CPointer
import kotlinx.cinterop.CValue
import kotlinx.cinterop.ExperimentalForeignApi
import quickjs.JSContext
import quickjs.JSValue
import quickjs.JS_FreeValue

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