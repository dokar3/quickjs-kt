#ifndef QJS_KT_QUICKJS_CINTEROP_H
#define QJS_KT_QUICKJS_CINTEROP_H

#include <limits.h>
#include <stdint.h>
#include <stdlib.h>

/*
 * Uniform struct wrapper for QuickJS JSValue used by Kotlin/Native cinterop.
 *
 * Requires "quickjs.h" to be included first.
 *
 * Upstream QuickJS enables 64-bit NaN-boxing (typedef uint64_t JSValue) on
 * x86_64, Apple arm64, and riscv, while non-Apple arm64 (e.g. linuxArm64)
 * keeps JSValue as a C struct. Wrapping JSValue in a single-member struct
 * gives Kotlin/Native a uniform CVariable type (CValue<QJSValue>) across all
 * native targets so cinterop commonization works without patching quickjs.h
 * or duplicating nativeMain code.
 */
typedef struct QJSValue {
    JSValue raw;
} QJSValue;

static inline QJSValue qjs_wrap(JSValue v) {
    QJSValue out;
    out.raw = v;
    return out;
}

/* Value tag / primitive accessors and constants */

static inline int JsValueGetNormTag(QJSValue v) {
    return JS_VALUE_GET_NORM_TAG(v.raw);
}

static inline int JsValueGetInt(QJSValue v) {
    return JS_VALUE_GET_INT(v.raw);
}

static inline int JsValueGetBool(QJSValue v) {
    return JS_VALUE_GET_BOOL(v.raw);
}

static inline double JsValueGetFloat64(QJSValue v) {
    return JS_VALUE_GET_FLOAT64(v.raw);
}

static inline void *JsValueGetPtr(QJSValue v) {
    return JS_VALUE_GET_PTR(v.raw);
}

static inline QJSValue JsNull(void) {
    return qjs_wrap(JS_NULL);
}

static inline QJSValue JsUndefined(void) {
    return qjs_wrap(JS_UNDEFINED);
}

static inline QJSValue JsTrue(void) {
    return qjs_wrap(JS_TRUE);
}

static inline QJSValue JsFalse(void) {
    return qjs_wrap(JS_FALSE);
}

static inline QJSValue JsException(void) {
    return qjs_wrap(JS_EXCEPTION);
}

/* JSValue-taking/returning QuickJS API wrappers */

static inline void qjs_free_value(JSContext *ctx, QJSValue v) {
    JS_FreeValue(ctx, v.raw);
}

static inline QJSValue qjs_dup_value(JSContext *ctx, QJSValue v) {
    return qjs_wrap(JS_DupValue(ctx, v.raw));
}

static inline QJSValue qjs_atom_to_string(JSContext *ctx, JSAtom atom) {
    return qjs_wrap(JS_AtomToString(ctx, atom));
}

static inline QJSValue qjs_atom_to_value(JSContext *ctx, JSAtom atom) {
    return qjs_wrap(JS_AtomToValue(ctx, atom));
}

static inline QJSValue qjs_call(
    JSContext *ctx,
    QJSValue func_obj,
    QJSValue this_obj,
    int argc,
    const QJSValue *argv
) {
    if (argc <= 0 || argv == NULL) {
        return qjs_wrap(JS_Call(ctx, func_obj.raw, this_obj.raw, 0, NULL));
    }
    JSValue stack_argv[16];
    JSValue *raw_argv = stack_argv;
    if (argc > 16) {
        raw_argv = (JSValue *)malloc((size_t)argc * sizeof(JSValue));
        if (raw_argv == NULL) {
            return qjs_wrap(JS_ThrowOutOfMemory(ctx));
        }
    }
    for (int i = 0; i < argc; i++) {
        raw_argv[i] = argv[i].raw;
    }
    JSValue res = JS_Call(ctx, func_obj.raw, this_obj.raw, argc, raw_argv);
    if (raw_argv != stack_argv) {
        free(raw_argv);
    }
    return qjs_wrap(res);
}

static inline QJSValue qjs_call_constructor(
    JSContext *ctx,
    QJSValue func_obj,
    int argc,
    const QJSValue *argv
) {
    if (argc <= 0 || argv == NULL) {
        return qjs_wrap(JS_CallConstructor(ctx, func_obj.raw, 0, NULL));
    }
    JSValue stack_argv[16];
    JSValue *raw_argv = stack_argv;
    if (argc > 16) {
        raw_argv = (JSValue *)malloc((size_t)argc * sizeof(JSValue));
        if (raw_argv == NULL) {
            return qjs_wrap(JS_ThrowOutOfMemory(ctx));
        }
    }
    for (int i = 0; i < argc; i++) {
        raw_argv[i] = argv[i].raw;
    }
    JSValue res = JS_CallConstructor(ctx, func_obj.raw, argc, raw_argv);
    if (raw_argv != stack_argv) {
        free(raw_argv);
    }
    return qjs_wrap(res);
}

static inline int qjs_define_property_get_set(
    JSContext *ctx,
    QJSValue this_obj,
    JSAtom prop,
    QJSValue getter,
    QJSValue setter,
    int flags
) {
    return JS_DefinePropertyGetSet(ctx, this_obj.raw, prop, getter.raw, setter.raw, flags);
}

static inline int qjs_define_property_value(
    JSContext *ctx,
    QJSValue this_obj,
    JSAtom prop,
    QJSValue val,
    int flags
) {
    return JS_DefinePropertyValue(ctx, this_obj.raw, prop, val.raw, flags);
}

static inline int qjs_define_property_value_str(
    JSContext *ctx,
    QJSValue this_obj,
    const char *prop,
    QJSValue val,
    int flags
) {
    return JS_DefinePropertyValueStr(ctx, this_obj.raw, prop, val.raw, flags);
}

static inline QJSValue qjs_eval(
    JSContext *ctx,
    const char *input,
    size_t input_len,
    const char *filename,
    int eval_flags
) {
    return qjs_wrap(JS_Eval(ctx, input, input_len, filename, eval_flags));
}

static inline QJSValue qjs_eval_function(JSContext *ctx, QJSValue fun_obj) {
    return qjs_wrap(JS_EvalFunction(ctx, fun_obj.raw));
}

static inline uint8_t *qjs_get_array_buffer(JSContext *ctx, size_t *psize, QJSValue obj) {
    return JS_GetArrayBuffer(ctx, psize, obj.raw);
}

static inline QJSValue qjs_get_exception(JSContext *ctx) {
    return qjs_wrap(JS_GetException(ctx));
}

static inline QJSValue qjs_get_global_object(JSContext *ctx) {
    return qjs_wrap(JS_GetGlobalObject(ctx));
}

static inline int qjs_get_own_property_names(
    JSContext *ctx,
    JSPropertyEnum **ptab,
    uint32_t *plen,
    QJSValue obj,
    int flags
) {
    return JS_GetOwnPropertyNames(ctx, ptab, plen, obj.raw, flags);
}

static inline QJSValue qjs_get_property(JSContext *ctx, QJSValue this_obj, JSAtom prop) {
    return qjs_wrap(JS_GetProperty(ctx, this_obj.raw, prop));
}

static inline QJSValue qjs_get_property_str(JSContext *ctx, QJSValue this_obj, const char *prop) {
    return qjs_wrap(JS_GetPropertyStr(ctx, this_obj.raw, prop));
}

static inline QJSValue qjs_get_property_uint32(JSContext *ctx, QJSValue this_obj, uint32_t idx) {
    return qjs_wrap(JS_GetPropertyUint32(ctx, this_obj.raw, idx));
}

static inline int qjs_has_property(JSContext *ctx, QJSValue this_obj, JSAtom prop) {
    return JS_HasProperty(ctx, this_obj.raw, prop);
}

static inline int qjs_is_array(JSContext *ctx, QJSValue val) {
    return JS_IsArray(ctx, val.raw);
}

static inline JS_BOOL qjs_is_error(JSContext *ctx, QJSValue val) {
    return JS_IsError(ctx, val.raw);
}

static inline JS_BOOL qjs_is_exception(QJSValue v) {
    return JS_IsException(v.raw);
}

static inline JS_BOOL qjs_is_function(JSContext *ctx, QJSValue val) {
    return JS_IsFunction(ctx, val.raw);
}

static inline int qjs_is_instance_of(JSContext *ctx, QJSValue val, QJSValue obj) {
    return JS_IsInstanceOf(ctx, val.raw, obj.raw);
}

static inline JS_BOOL qjs_is_null(QJSValue v) {
    return JS_IsNull(v.raw);
}

static inline JS_BOOL qjs_is_string(QJSValue v) {
    return JS_IsString(v.raw);
}

static inline JS_BOOL qjs_is_undefined(QJSValue v) {
    return JS_IsUndefined(v.raw);
}

static inline QJSValue qjs_json_stringify(
    JSContext *ctx,
    QJSValue obj,
    QJSValue replacer,
    QJSValue space0
) {
    return qjs_wrap(JS_JSONStringify(ctx, obj.raw, replacer.raw, space0.raw));
}

static inline QJSValue qjs_new_array(JSContext *ctx) {
    return qjs_wrap(JS_NewArray(ctx));
}

static inline QJSValue qjs_new_array_buffer(
    JSContext *ctx,
    uint8_t *buf,
    size_t len,
    JSFreeArrayBufferDataFunc *free_func,
    void *opaque,
    JS_BOOL is_shared
) {
    return qjs_wrap(JS_NewArrayBuffer(ctx, buf, len, free_func, opaque, is_shared));
}

static inline QJSValue qjs_new_big_int64(JSContext *ctx, int64_t v) {
    return qjs_wrap(JS_NewBigInt64(ctx, v));
}

static inline QJSValue qjs_new_bool(JSContext *ctx, JS_BOOL val) {
    return qjs_wrap(JS_NewBool(ctx, val));
}

static inline QJSValue qjs_new_error(JSContext *ctx) {
    return qjs_wrap(JS_NewError(ctx));
}

static inline QJSValue qjs_new_float64(JSContext *ctx, double d) {
    return qjs_wrap(JS_NewFloat64(ctx, d));
}

static inline QJSValue qjs_new_int32(JSContext *ctx, int32_t val) {
    return qjs_wrap(JS_NewInt32(ctx, val));
}

static inline QJSValue qjs_new_int64(JSContext *ctx, int64_t val) {
    return qjs_wrap(JS_NewInt64(ctx, val));
}

static inline QJSValue qjs_new_object(JSContext *ctx) {
    return qjs_wrap(JS_NewObject(ctx));
}

static inline QJSValue qjs_new_promise_capability(JSContext *ctx, QJSValue *resolving_funcs) {
    if (resolving_funcs == NULL) {
        return qjs_wrap(JS_ThrowTypeError(ctx, "resolving_funcs must not be NULL"));
    }
    JSValue tmp[2];
    JSValue promise = JS_NewPromiseCapability(ctx, tmp);
    if (!JS_IsException(promise)) {
        resolving_funcs[0].raw = tmp[0];
        resolving_funcs[1].raw = tmp[1];
    }
    return qjs_wrap(promise);
}

static inline QJSValue qjs_new_string(JSContext *ctx, const char *str) {
    return qjs_wrap(JS_NewString(ctx, str));
}

static inline QJSValue qjs_promise_result(JSContext *ctx, QJSValue promise) {
    return qjs_wrap(JS_PromiseResult(ctx, promise.raw));
}

static inline JSPromiseStateEnum qjs_promise_state(JSContext *ctx, QJSValue promise) {
    return JS_PromiseState(ctx, promise.raw);
}

static inline QJSValue qjs_read_object(
    JSContext *ctx,
    const uint8_t *buf,
    size_t buf_len,
    int flags
) {
    return qjs_wrap(JS_ReadObject(ctx, buf, buf_len, flags));
}

static inline int qjs_resolve_module(JSContext *ctx, QJSValue obj) {
    return JS_ResolveModule(ctx, obj.raw);
}

static inline int qjs_set_property_str(
    JSContext *ctx,
    QJSValue this_obj,
    const char *prop,
    QJSValue val
) {
    return JS_SetPropertyStr(ctx, this_obj.raw, prop, val.raw);
}

static inline int qjs_set_property_uint32(
    JSContext *ctx,
    QJSValue this_obj,
    uint32_t idx,
    QJSValue val
) {
    return JS_SetPropertyUint32(ctx, this_obj.raw, idx, val.raw);
}

static inline QJSValue qjs_throw(JSContext *ctx, QJSValue obj) {
    return qjs_wrap(JS_Throw(ctx, obj.raw));
}

static inline int qjs_to_bool(JSContext *ctx, QJSValue val) {
    return JS_ToBool(ctx, val.raw);
}

static inline const char *qjs_to_c_string(JSContext *ctx, QJSValue val1) {
    return JS_ToCString(ctx, val1.raw);
}

static inline int qjs_to_float64(JSContext *ctx, double *pres, QJSValue val) {
    return JS_ToFloat64(ctx, pres, val.raw);
}

static inline int qjs_to_int32(JSContext *ctx, int32_t *pres, QJSValue val) {
    return JS_ToInt32(ctx, pres, val.raw);
}

static inline int qjs_to_int64(JSContext *ctx, int64_t *pres, QJSValue val) {
    return JS_ToInt64(ctx, pres, val.raw);
}

static inline int qjs_to_int64_ext(JSContext *ctx, int64_t *pres, QJSValue val) {
    return JS_ToInt64Ext(ctx, pres, val.raw);
}

static inline JS_BOOL qjs_value_is_nan(QJSValue v) {
    return JS_VALUE_IS_NAN(v.raw);
}

static inline uint8_t *qjs_write_object(
    JSContext *ctx,
    size_t *psize,
    QJSValue obj,
    int flags
) {
    return JS_WriteObject(ctx, psize, obj.raw, flags);
}

/* CFunctionData trampoline: stores callback pointer + data_len in 3 immediate int32 slots */

typedef QJSValue QJSCFunctionData(
    JSContext *ctx,
    QJSValue this_val,
    int argc,
    QJSValue *argv,
    int magic,
    QJSValue *func_data
);

static JSValue qjs_c_function_data_trampoline(
    JSContext *ctx,
    JSValueConst this_val,
    int argc,
    JSValueConst *argv,
    int magic,
    JSValue *func_data
) {
    uint64_t lo = (uint32_t)JS_VALUE_GET_INT(func_data[0]);
    uint64_t hi = (uint32_t)JS_VALUE_GET_INT(func_data[1]);
    QJSCFunctionData *cb = (QJSCFunctionData *)(uintptr_t)((hi << 32) | lo);
    if (cb == NULL) {
        return JS_ThrowInternalError(ctx, "Missing CFunctionData callback");
    }
    int data_len = JS_VALUE_GET_INT(func_data[2]);
    int safe_argc = (argc > 0 && argv != NULL) ? argc : 0;

    QJSValue stack_argv[16];
    QJSValue *q_argv = NULL;
    if (safe_argc > 0) {
        q_argv = stack_argv;
        if (safe_argc > 16) {
            q_argv = (QJSValue *)malloc((size_t)safe_argc * sizeof(QJSValue));
            if (q_argv == NULL) {
                return JS_ThrowOutOfMemory(ctx);
            }
        }
        for (int i = 0; i < safe_argc; i++) {
            q_argv[i].raw = argv[i];
        }
    }

    QJSValue stack_data[8];
    QJSValue *q_data = NULL;
    if (data_len > 0) {
        q_data = stack_data;
        if (data_len > 8) {
            q_data = (QJSValue *)malloc((size_t)data_len * sizeof(QJSValue));
            if (q_data == NULL) {
                if (q_argv != NULL && q_argv != stack_argv) {
                    free(q_argv);
                }
                return JS_ThrowOutOfMemory(ctx);
            }
        }
        for (int i = 0; i < data_len; i++) {
            q_data[i].raw = func_data[3 + i];
        }
    }

    QJSValue res = cb(ctx, qjs_wrap(this_val), safe_argc, q_argv, magic, q_data);

    if (q_data != NULL && q_data != stack_data) {
        free(q_data);
    }
    if (q_argv != NULL && q_argv != stack_argv) {
        free(q_argv);
    }
    return res.raw;
}

static inline QJSValue qjs_new_c_function_data(
    JSContext *ctx,
    QJSCFunctionData *func,
    int length,
    int magic,
    int data_len,
    const QJSValue *data
) {
    if (func == NULL) {
        return qjs_wrap(JS_ThrowTypeError(ctx, "CFunctionData callback must not be NULL"));
    }
    int safe_data_len = (data_len > 0 && data != NULL) ? data_len : 0;
    if (safe_data_len > INT_MAX - 3) {
        return qjs_wrap(JS_ThrowRangeError(ctx, "CFunctionData data_len overflow"));
    }
    int total_len = safe_data_len + 3;
    JSValue stack_data[11];
    JSValue *raw_data = stack_data;
    if (total_len > 11) {
        raw_data = (JSValue *)malloc((size_t)total_len * sizeof(JSValue));
        if (raw_data == NULL) {
            return qjs_wrap(JS_ThrowOutOfMemory(ctx));
        }
    }

    uint64_t addr = (uint64_t)(uintptr_t)func;
    raw_data[0] = JS_NewInt32(ctx, (int32_t)(uint32_t)(addr & 0xffffffffu));
    raw_data[1] = JS_NewInt32(ctx, (int32_t)(uint32_t)(addr >> 32));
    raw_data[2] = JS_NewInt32(ctx, safe_data_len);
    for (int i = 0; i < safe_data_len; i++) {
        raw_data[3 + i] = data[i].raw;
    }

    JSValue fn = JS_NewCFunctionData(
        ctx,
        qjs_c_function_data_trampoline,
        length,
        magic,
        total_len,
        raw_data
    );
    if (raw_data != stack_data) {
        free(raw_data);
    }
    return qjs_wrap(fn);
}

/* HostPromiseRejectionTracker trampoline with per-runtime state handle */

typedef void QJSHostPromiseRejectionTracker(
    JSContext *ctx,
    QJSValue promise,
    QJSValue reason,
    JS_BOOL is_handled,
    void *opaque
);

typedef struct QJSPromiseRejectionTrackerState {
    QJSHostPromiseRejectionTracker *cb;
    void *opaque;
} QJSPromiseRejectionTrackerState;

static void qjs_promise_rejection_trampoline(
    JSContext *ctx,
    JSValueConst promise,
    JSValueConst reason,
    JS_BOOL is_handled,
    void *opaque
) {
    QJSPromiseRejectionTrackerState *state = (QJSPromiseRejectionTrackerState *)opaque;
    if (state != NULL && state->cb != NULL) {
        state->cb(ctx, qjs_wrap(promise), qjs_wrap(reason), is_handled, state->opaque);
    }
}

static inline void *qjs_promise_rejection_tracker_install(
    JSRuntime *rt,
    QJSHostPromiseRejectionTracker *cb,
    void *opaque
) {
    QJSPromiseRejectionTrackerState *state =
        (QJSPromiseRejectionTrackerState *)malloc(sizeof(QJSPromiseRejectionTrackerState));
    if (state == NULL) {
        return NULL;
    }
    state->cb = cb;
    state->opaque = opaque;
    JS_SetHostPromiseRejectionTracker(rt, qjs_promise_rejection_trampoline, state);
    return state;
}

static inline void qjs_promise_rejection_tracker_suspend(JSRuntime *rt) {
    JS_SetHostPromiseRejectionTracker(rt, NULL, NULL);
}

static inline void qjs_promise_rejection_tracker_resume(JSRuntime *rt, void *state) {
    if (state != NULL) {
        JS_SetHostPromiseRejectionTracker(rt, qjs_promise_rejection_trampoline, state);
    }
}

static inline void qjs_promise_rejection_tracker_free(JSRuntime *rt, void *state) {
    JS_SetHostPromiseRejectionTracker(rt, NULL, NULL);
    free(state);
}

#endif // QJS_KT_QUICKJS_CINTEROP_H
