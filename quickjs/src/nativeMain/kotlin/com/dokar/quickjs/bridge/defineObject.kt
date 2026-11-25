package com.dokar.quickjs.bridge

import com.dokar.quickjs.QuickJs
import com.dokar.quickjs.binding.JsProperty
import com.dokar.quickjs.binding.ObjectBinding
import com.dokar.quickjs.util.allocArrayOf
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.CValue
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.StableRef
import kotlinx.cinterop.asStableRef
import kotlinx.cinterop.cstr
import kotlinx.cinterop.get
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.readValue
import kotlinx.cinterop.staticCFunction
import kotlinx.cinterop.toCPointer
import kotlinx.cinterop.toLong
import platform.posix.int64_tVar
import quickjs.JSContext
import quickjs.JS_FreeAtom
import quickjs.JS_NewAtom
import quickjs.JS_PROP_CONFIGURABLE
import quickjs.JS_PROP_C_W_E
import quickjs.JS_PROP_ENUMERABLE
import quickjs.JS_PROP_WRITABLE
import quickjs.JsException
import quickjs.JsUndefined
import quickjs.QJSValue
import quickjs.qjs_define_property_get_set
import quickjs.qjs_define_property_value_str
import quickjs.qjs_dup_value
import quickjs.qjs_free_value
import quickjs.qjs_get_global_object
import quickjs.qjs_new_big_int64
import quickjs.qjs_new_c_function_data
import quickjs.qjs_new_object
import quickjs.qjs_new_string
import quickjs.qjs_throw

@OptIn(ExperimentalForeignApi::class)
internal fun CPointer<JSContext>.defineObject(
    quickJsRef: StableRef<QuickJs>,
    parentHandle: Long?,
    name: String,
    binding: ObjectBinding
): Long {
    val instance = qjs_new_object(this)
    val retainedInstance = qjs_dup_value(this, instance)

    val handleRef = StableRef.create(retainedInstance)
    val handle = handleRef.asCPointer().toLong()

    val properties = binding.properties
    for (prop in properties) {
        defineProperty(
            quickJsRef = quickJsRef,
            instance = instance,
            handle = handle,
            property = prop,
        )
    }

    val functions = binding.functions
    for (func in functions) {
        defineFunction(
            quickJsRef = quickJsRef,
            parent = instance,
            parentHandle = handle,
            name = func.name,
            isAsync = func.isAsync,
        )
    }

    val parent = parentHandle?.let { objectHandleToStableRef(it) }?.get()
    val defineResult = if (parent == null) {
        val globalThis = qjs_get_global_object(this)
        try {
            qjs_define_property_value_str(this, globalThis, name, instance, JS_PROP_C_W_E)
        } finally {
            qjs_free_value(this, globalThis)
        }
    } else {
        qjs_define_property_value_str(this, parent, name, instance, JS_PROP_C_W_E)
    }

    if (defineResult < 0) {
        qjs_free_value(this, retainedInstance)
        handleRef.dispose()
        checkContextException(this)
        error("QuickJS failed to define object '$name'.")
    }

    return handle
}

@OptIn(ExperimentalForeignApi::class)
internal fun CPointer<JSContext>.defineProperty(
    quickJsRef: StableRef<QuickJs>,
    instance: CValue<QJSValue>,
    handle: Long,
    property: JsProperty,
) = memScoped {
    val context = this@defineProperty

    val quickJs = quickJsRef.get()
    val qjsVoidPtr = quickJsRef.asCPointer()
    val qjsPtrAddress = qjsVoidPtr.toLong()

    val funcDataArray = arrayOf(
        qjs_new_string(context, property.name.cstr),
        qjs_new_big_int64(context, qjsPtrAddress),
        qjs_new_big_int64(context, handle),
    )

    // Free function data when closing
    quickJs.addManagedJsValues(*funcDataArray)

    val getter = qjs_new_c_function_data(
        ctx = context,
        func = staticCFunction(::invokeGetter),
        length = 0,
        magic = 0,
        data_len = 3,
        data = allocArrayOf<QJSValue>(*funcDataArray),
    )

    val setter = if (property.writable) {
        qjs_new_c_function_data(
            ctx = context,
            func = staticCFunction(::invokeSetter),
            length = 0,
            magic = 0,
            data_len = 3,
            data = allocArrayOf<QJSValue>(*funcDataArray),
        )
    } else {
        JsUndefined()
    }

    var flags = JS_PROP_WRITABLE.inv()
    if (!property.configurable) {
        flags = flags and JS_PROP_CONFIGURABLE.inv()
    }
    if (!property.writable) {
        flags = flags and JS_PROP_WRITABLE.inv()
    }
    if (!property.enumerable) {
        flags = flags and JS_PROP_ENUMERABLE.inv()
    }

    val prop = JS_NewAtom(context, property.name)
    qjs_define_property_get_set(
        ctx = context,
        this_obj = instance,
        prop = prop,
        getter = getter,
        setter = setter,
        flags = flags,
    )
    JS_FreeAtom(context, prop)
}

@OptIn(ExperimentalForeignApi::class)
@Suppress("unused_parameter")
private fun invokeGetter(
    ctx: CPointer<JSContext>?,
    thisVal: CValue<QJSValue>,
    argc: Int,
    argv: CPointer<QJSValue>?,
    magic: Int,
    funcData: CPointer<QJSValue>?,
): CValue<QJSValue> = memScoped {
    ctx ?: return@memScoped JsException()
    funcData ?: return@memScoped JsException()

    val (propName, quickJs, objectHandle) = BindingFunctionData.fromJsValues(ctx, funcData)
    if (quickJs.checkInterruptOrThrow(ctx)) {
        return@memScoped JsException()
    }

    val result = try {
        quickJs
            .onCallBindingGetter(parentHandle = objectHandle, name = propName)
            .toJsValue(context = ctx)
    } catch (e: Throwable) {
        qjs_throw(ctx, ktErrorToJsError(ctx, e))
        JsException()
    }
    quickJs.checkInterruptResult(ctx, result)
}

@OptIn(ExperimentalForeignApi::class)
@Suppress("unused_parameter")
private fun invokeSetter(
    ctx: CPointer<JSContext>?,
    thisVal: CValue<QJSValue>,
    argc: Int,
    argv: CPointer<QJSValue>?,
    magic: Int,
    funcData: CPointer<QJSValue>?,
): CValue<QJSValue> = memScoped {
    ctx ?: return@memScoped JsException()
    funcData ?: return@memScoped JsException()

    if (argc <= 0) {
        return@memScoped JsException()
    }

    val (propName, quickJs, objectHandle) = BindingFunctionData.fromJsValues(ctx, funcData)
    if (quickJs.checkInterruptOrThrow(ctx)) {
        return@memScoped JsException()
    }

    val result = try {
        val value = argv!![0].readValue().toKtValue(ctx)
        quickJs.onCallBindingSetter(
            parentHandle = objectHandle,
            name = propName,
            value = value,
        )
        JsUndefined()
    } catch (e: Throwable) {
        qjs_throw(ctx, ktErrorToJsError(ctx, e))
        JsException()
    }
    quickJs.checkInterruptResult(ctx, result)
}

@OptIn(ExperimentalForeignApi::class)
internal fun objectHandleToStableRef(handle: Long): StableRef<CValue<QJSValue>>? {
    val voidPtr = handle.toCPointer<int64_tVar>() ?: return null
    return voidPtr.asStableRef<CValue<QJSValue>>()
}
