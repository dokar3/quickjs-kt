package com.dokar.quickjs.bridge

import com.dokar.quickjs.JSContext
import com.dokar.quickjs.JSValue
import com.dokar.quickjs.JS_DefinePropertyGetSet
import com.dokar.quickjs.JS_DefinePropertyValueStr
import com.dokar.quickjs.JS_FreeAtom
import com.dokar.quickjs.JS_FreeValue
import com.dokar.quickjs.JS_GetGlobalObject
import com.dokar.quickjs.JS_NewAtom
import com.dokar.quickjs.JS_NewCFunctionData
import com.dokar.quickjs.JS_NewInt64
import com.dokar.quickjs.JS_NewObject
import com.dokar.quickjs.JS_NewString
import com.dokar.quickjs.JS_PROP_CONFIGURABLE
import com.dokar.quickjs.JS_PROP_C_W_E
import com.dokar.quickjs.JS_PROP_ENUMERABLE
import com.dokar.quickjs.JS_PROP_WRITABLE
import com.dokar.quickjs.JS_Throw
import com.dokar.quickjs.JsException
import com.dokar.quickjs.JsUndefined
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

@OptIn(ExperimentalForeignApi::class)
internal fun CPointer<JSContext>.defineObject(
    quickJsRef: StableRef<QuickJs>,
    parentHandle: Long?,
    name: String,
    binding: ObjectBinding
): Long {
    val instance = JS_NewObject(this)

    val handle = jsValueToObjectHandle(instance)

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
    if (parent == null) {
        val globalThis = JS_GetGlobalObject(this)
        JS_DefinePropertyValueStr(this, globalThis, name, instance, JS_PROP_C_W_E)
        JS_FreeValue(this, globalThis)
    } else {
        JS_DefinePropertyValueStr(this, parent, name, instance, JS_PROP_C_W_E)
    }

    return handle
}

@OptIn(ExperimentalForeignApi::class)
internal fun CPointer<JSContext>.defineProperty(
    quickJsRef: StableRef<QuickJs>,
    instance: CValue<JSValue>,
    handle: Long,
    property: JsProperty,
) = memScoped {
    val context = this@defineProperty

    val quickJs = quickJsRef.get()
    val qjsVoidPtr = quickJsRef.asCPointer()
    val qjsPtrAddress = qjsVoidPtr.toLong()

    val funcDataArray = arrayOf(
        JS_NewString(context, property.name.cstr),
        JS_NewInt64(context, qjsPtrAddress),
        JS_NewInt64(context, handle),
    )

    // Free function data when closing
    quickJs.addManagedJsValues(*funcDataArray)

    val getter = JS_NewCFunctionData(
        ctx = context,
        func = staticCFunction(::invokeGetter),
        length = 0,
        magic = 0,
        data_len = 3,
        data = allocArrayOf<JSValue>(*funcDataArray),
    )

    val setter = if (property.writable) {
        JS_NewCFunctionData(
            ctx = context,
            func = staticCFunction(::invokeSetter),
            length = 0,
            magic = 0,
            data_len = 3,
            data = allocArrayOf<JSValue>(*funcDataArray),
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
    JS_DefinePropertyGetSet(
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
    thisVal: CValue<JSValue>,
    argc: Int,
    argv: CPointer<JSValue>?,
    magic: Int,
    funcData: CPointer<JSValue>?,
): CValue<JSValue> = memScoped {
    ctx ?: return@memScoped JsException()
    funcData ?: return@memScoped JsException()

    val (propName, quickJs, objectHandle) = BindingFunctionData.fromJsValues(ctx, funcData)

    try {
        quickJs
            .onCallBindingGetter(parentHandle = objectHandle, name = propName)
            .toJsValue(context = ctx)
    } catch (e: Throwable) {
        JS_Throw(ctx, ktErrorToJsError(ctx, e))
        JsException()
    }
}

@OptIn(ExperimentalForeignApi::class)
@Suppress("unused_parameter")
private fun invokeSetter(
    ctx: CPointer<JSContext>?,
    thisVal: CValue<JSValue>,
    argc: Int,
    argv: CPointer<JSValue>?,
    magic: Int,
    funcData: CPointer<JSValue>?,
): CValue<JSValue> = memScoped {
    ctx ?: return@memScoped JsException()
    funcData ?: return@memScoped JsException()

    if (argc <= 0) {
        return@memScoped JsException()
    }

    val (propName, quickJs, objectHandle) = BindingFunctionData.fromJsValues(ctx, funcData)

    val value = argv!![0].readValue().toKtValue(ctx)

    try {
        quickJs.onCallBindingSetter(
            parentHandle = objectHandle,
            name = propName,
            value = value,
        )
        JsUndefined()
    } catch (e: Throwable) {
        JS_Throw(ctx, ktErrorToJsError(ctx, e))
        JsException()
    }
}

@OptIn(ExperimentalForeignApi::class)
internal fun objectHandleToStableRef(handle: Long): StableRef<CValue<JSValue>>? {
    val voidPtr = handle.toCPointer<int64_tVar>() ?: return null
    return voidPtr.asStableRef<CValue<JSValue>>()
}

@OptIn(ExperimentalForeignApi::class)
private fun jsValueToObjectHandle(value: CValue<JSValue>): Long {
    val ref = StableRef.create(value)
    return ref.asCPointer().toLong()
}
