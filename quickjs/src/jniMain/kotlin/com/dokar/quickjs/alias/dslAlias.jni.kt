package com.dokar.quickjs.alias

import com.dokar.quickjs.ExperimentalQuickJsApi
import com.dokar.quickjs.QuickJs
import com.dokar.quickjs.QuickJsException
import com.dokar.quickjs.binding.JsObjectHandle
import com.dokar.quickjs.binding.define
import com.dokar.quickjs.evaluate

/**
 * Alias for inline version of [QuickJs.define].
 */
@ExperimentalQuickJsApi
inline fun <reified T> QuickJs.def(
    name: String,
    instance: Any,
    parent: JsObjectHandle = JsObjectHandle.globalThis,
) {
    define<T>(name = name, instance = instance, parent = parent)
}

/**
 * Alias for [QuickJs.define].
 */
@ExperimentalQuickJsApi
fun <T> QuickJs.def(
    name: String,
    type: Class<T>,
    instance: Any,
    parent: JsObjectHandle = JsObjectHandle.globalThis,
) {
    define(name = name, type = type, instance = instance, parent = parent)
}

/**
 * Alias for [QuickJs.evaluate].
 */
@ExperimentalQuickJsApi
@Throws(QuickJsException::class)
suspend fun <T> QuickJs.eval(
    bytecode: ByteArray,
    type: Class<T>
): T {
    return evaluate(bytecode = bytecode, type = type)
}

/**
 * Alias for [QuickJs.evaluate].
 */
@ExperimentalQuickJsApi
@Throws(QuickJsException::class)
suspend fun <T> QuickJs.eval(
    code: String,
    type: Class<T>,
    filename: String = "main.js",
    asModule: Boolean = false
): T {
    return evaluate(code = code, type = type, filename = filename, asModule = asModule)
}