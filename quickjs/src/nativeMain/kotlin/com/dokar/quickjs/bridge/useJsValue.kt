package com.dokar.quickjs.bridge

import kotlinx.cinterop.CPointer
import kotlinx.cinterop.CValue
import kotlinx.cinterop.ExperimentalForeignApi
import quickjs.JSContext
import quickjs.QJSValue
import quickjs.qjs_free_value

@OptIn(ExperimentalForeignApi::class)
internal inline fun <T> CValue<QJSValue>.use(
    context: CPointer<JSContext>,
    block: CValue<QJSValue>.() -> T
): T {
    try {
        return block()
    } finally {
        qjs_free_value(context, this)
    }
}
