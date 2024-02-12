package com.dokar.quickjs.util

import com.dokar.quickjs.JSContext
import com.dokar.quickjs.JSValue
import com.dokar.quickjs.JS_FreeValue
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.CValue
import kotlinx.cinterop.ExperimentalForeignApi

@OptIn(ExperimentalForeignApi::class)
internal fun freeJsValues(context: CPointer<JSContext>, vararg values: CValue<JSValue>) {
    for (value in values) {
        JS_FreeValue(context, value)
    }
}