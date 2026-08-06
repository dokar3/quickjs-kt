#include <stdint.h>
#include <string.h>

#include "jni.h"
#include "quickjs.h"
#include "native_operations.h"

static int cleanup_count;

static JSValue native_sum(JSContext *ctx, JSValueConst this_val, int argc,
                          JSValueConst *argv) {
    int32_t left;
    int32_t right;
    if (argc < 2 || JS_ToInt32(ctx, &left, argv[0]) < 0 ||
        JS_ToInt32(ctx, &right, argv[1]) < 0) {
        return JS_ThrowTypeError(ctx, "nativeSum expects two numbers");
    }
    return JS_NewInt32(ctx, left + right);
}

static JSContext *context_from_pointer(void *pointer) {
    return (JSContext *)pointer;
}

static JSRuntime *runtime_from_pointer(void *pointer) {
    return (JSRuntime *)pointer;
}

static void delete_global_property(JSContext *ctx, JSValue global, const char *name) {
    JSAtom atom = JS_NewAtom(ctx, name);
    JS_DeleteProperty(ctx, global, atom, 0);
    JS_FreeAtom(ctx, atom);
}

QUICKJS_NATIVE_OPERATIONS_API void quickjs_native_operations_install(
        void *context_pointer,
        void *runtime_pointer) {
    JSContext *ctx = context_from_pointer(context_pointer);
    JSRuntime *runtime = runtime_from_pointer(runtime_pointer);
    JSValue global;
    JSValue function;
    JSValue injected;

    JS_UpdateStackTop(runtime);
    global = JS_GetGlobalObject(ctx);
    function = JS_NewCFunction(ctx, native_sum, "nativeSum", 2);
    if (JS_SetPropertyStr(ctx, global, "nativeSum", function) < 0) {
        JS_FreeValue(ctx, global);
        return;
    }

    injected = JS_NewInt32(ctx, 40);
    JS_SetPropertyStr(ctx, global, "nativeInjected", injected);
    JS_FreeValue(ctx, global);
}

QUICKJS_NATIVE_OPERATIONS_API int quickjs_native_operations_execute(
        void *context_pointer,
        void *runtime_pointer) {
    JSContext *ctx = context_from_pointer(context_pointer);
    JSRuntime *runtime = runtime_from_pointer(runtime_pointer);
    const char *source = "nativeInjected + nativeSum(2, 3)";
    JSValue value;
    int32_t result;

    JS_UpdateStackTop(runtime);
    value = JS_Eval(ctx, source, strlen(source), "native-operations.js",
                    JS_EVAL_TYPE_GLOBAL);
    if (JS_IsException(value) || JS_ToInt32(ctx, &result, value) < 0) {
        JS_FreeValue(ctx, value);
        return -1;
    }
    JS_FreeValue(ctx, value);
    return result;
}

QUICKJS_NATIVE_OPERATIONS_API void quickjs_native_operations_uninstall(
        void *context_pointer,
        void *runtime_pointer) {
    JSContext *ctx = context_from_pointer(context_pointer);
    JSRuntime *runtime = runtime_from_pointer(runtime_pointer);
    JSValue global;

    JS_UpdateStackTop(runtime);
    global = JS_GetGlobalObject(ctx);
    delete_global_property(ctx, global, "nativeSum");
    delete_global_property(ctx, global, "nativeInjected");
    JS_FreeValue(ctx, global);
    cleanup_count++;
}

QUICKJS_NATIVE_OPERATIONS_API int quickjs_native_operations_cleanup_count(void) {
    return cleanup_count;
}

QUICKJS_NATIVE_OPERATIONS_API void quickjs_native_operations_reset_cleanup_count(void) {
    cleanup_count = 0;
}

JNIEXPORT void JNICALL
Java_com_dokar_quickjs_nativeintegration_NativeOperations_nativeInstall(
        JNIEnv *env, jclass clazz, jlong context_address, jlong runtime_address) {
    (void)env;
    (void)clazz;
    quickjs_native_operations_install(
            (void *)(uintptr_t)context_address,
            (void *)(uintptr_t)runtime_address);
}

JNIEXPORT jint JNICALL
Java_com_dokar_quickjs_nativeintegration_NativeOperations_nativeExecute(
        JNIEnv *env, jclass clazz, jlong context_address, jlong runtime_address) {
    (void)env;
    (void)clazz;
    return quickjs_native_operations_execute(
            (void *)(uintptr_t)context_address,
            (void *)(uintptr_t)runtime_address);
}

JNIEXPORT void JNICALL
Java_com_dokar_quickjs_nativeintegration_NativeOperations_nativeUninstall(
        JNIEnv *env, jclass clazz, jlong context_address, jlong runtime_address) {
    (void)env;
    (void)clazz;
    quickjs_native_operations_uninstall(
            (void *)(uintptr_t)context_address,
            (void *)(uintptr_t)runtime_address);
}

JNIEXPORT jint JNICALL
Java_com_dokar_quickjs_nativeintegration_NativeOperations_nativeCleanupCount(
        JNIEnv *env, jclass clazz) {
    (void)env;
    (void)clazz;
    return quickjs_native_operations_cleanup_count();
}

JNIEXPORT void JNICALL
Java_com_dokar_quickjs_nativeintegration_NativeOperations_nativeResetCleanupCount(
        JNIEnv *env, jclass clazz) {
    (void)env;
    (void)clazz;
    quickjs_native_operations_reset_cleanup_count();
}
