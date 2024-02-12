package com.dokar.quickjs.util

import kotlinx.cinterop.CPointer
import kotlinx.cinterop.CValue
import kotlinx.cinterop.ExperimentalForeignApi
import quickjs.JSContext
import quickjs.JSValue
import quickjs.JS_FreeValue

@OptIn(ExperimentalForeignApi::class)
internal fun freeJsValues(context: CPointer<JSContext>, vararg values: CValue<JSValue>) {
    for (value in values) {
        JS_FreeValue(context, value)
    }
}