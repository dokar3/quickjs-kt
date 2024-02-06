#include <string.h>
#include "jni.h"
#include "quickjs.h"
#include "quickjs_jni.h"
#include "jni_globals.h"
#include "jni_globals_generated.h"
#include "binding_bridge.h"
#include "exception_util.h"
#include "log_util.h"
#include "js_value_to_jobject.h"
#include "jobject_to_js_value.h"
#include "js_value_util.h"

JSRuntime *runtime_from_ptr(JNIEnv *env, jlong ptr) {
    if (ptr == 0) {
        jni_throw_exception(env, "JS runtime is destroyed.");
        return NULL;
    }
    return (JSRuntime *) ptr;
}

JSContext *context_from_ptr(JNIEnv *env, jlong ptr) {
    if (ptr == 0) {
        jni_throw_exception(env, "JS context is destroyed.");
        return NULL;
    }
    return (JSContext *) ptr;
}

Globals *globals_from_ptr(JNIEnv *env, jlong ptr) {
    if (ptr == 0) {
        jni_throw_exception(env, "Globals is destroyed.");
        return NULL;
    }
    return (Globals *) ptr;
}

/**
 * Initialize global resources.
 */
JNIEXPORT jlong JNICALL Java_com_dokar_quickjs_QuickJs_initGlobals(JNIEnv *env,
                                                                   jobject this) {
    // Suppress lint: We will free it in releaseGlobals()
#pragma clang diagnostic push
#pragma ide diagnostic ignored "MemoryLeak"
    Globals *globals = malloc(sizeof(Globals));
#pragma clang diagnostic pop

    globals->managed_js_values = NULL;
    globals->defined_js_objects = NULL;
    globals->global_object_refs = NULL;
    globals->created_js_functions = NULL;
    globals->evaluate_result_promise = NULL;

    cache_java_vm(env);

    return (jlong) globals;
}

/**
 * Create a new QuickJS JavaScript runtime.
 *
 * @return Runtime pointer.
 */
JNIEXPORT jlong JNICALL Java_com_dokar_quickjs_QuickJs_newRuntime(JNIEnv *env, jobject this) {
    JSRuntime *runtime = JS_NewRuntime();
    return (jlong) runtime;
}

/**
 * Create a new QuickJS JavaScript Context from the given runtime pointer.
 *
 * @param runtime_ptr The runtime pointer.
 * @return Context pointer.
 */
JNIEXPORT jlong JNICALL
Java_com_dokar_quickjs_QuickJs_newContext(JNIEnv *env, jobject this, jlong runtime_ptr) {
    JSRuntime *runtime = runtime_from_ptr(env, runtime_ptr);
    if (runtime == NULL) {
        return 0;
    }
    JSContext *context = JS_NewContext(runtime);
    return (jlong) context;
}

/**
 * Release globals.
 */
JNIEXPORT void JNICALL
Java_com_dokar_quickjs_QuickJs_releaseGlobals(JNIEnv *env, jobject this, jlong context_ptr,
                                              jlong globals_ptr) {
    if (globals_ptr == 0) {
        return;
    }
    JSContext *context = context_from_ptr(env, context_ptr);
    if (context == NULL) {
        jni_throw_exception(env, "Context is already destroyed.");
        return;
    }

    Globals *globals = globals_from_ptr(env, globals_ptr);

    cvector_vector_type(JSValue)created_js_functions = globals->created_js_functions;
    if (created_js_functions != NULL) {
        size_t size = cvector_size(created_js_functions);
        for (uint32_t i = 0; i < size; i++) {
            JSValue item = created_js_functions[i];
            JS_FreeValue(context, item);
        }
        cvector_free(created_js_functions);
    }

    // Check and free js values that are used by bindings
    cvector_vector_type(JSValue)managed_js_values = globals->managed_js_values;
    if (managed_js_values != NULL) {
        size_t size = cvector_size(managed_js_values);
        for (uint32_t i = 0; i < size; i++) {
            JSValue value = managed_js_values[i];
            JS_FreeValue(context, value);
        }
        cvector_free(managed_js_values);
    }

    if (globals->defined_js_objects != NULL) {
        cvector_free(globals->defined_js_objects);
    }

    // Check and free global jni object refs
    cvector_vector_type(jobject)global_object_refs = globals->global_object_refs;
    if (global_object_refs != NULL) {
        size_t size = cvector_size(global_object_refs);
        for (uint32_t i = 0; i < size; i++) {
            (*env)->DeleteGlobalRef(env, global_object_refs[i]);
        }
        cvector_free(global_object_refs);
    }

    if (globals->evaluate_result_promise != NULL) {
        // Free the result promise even if someone hasn't used it
        JS_FreeValue(context, *(globals->evaluate_result_promise));
        globals->evaluate_result_promise = NULL;
    }

    // Clear Env, class, methodId, fieldId
    clear_java_vm_cache();
    clear_jni_refs_cache(env);

    // Free the globals struct
    free(globals);
}

/**
 * Release JavaScript context.
 */
JNIEXPORT void JNICALL
Java_com_dokar_quickjs_QuickJs_releaseContext(JNIEnv *env, jobject this, jlong context_ptr) {
    if (context_ptr == 0) {
        return;
    }
    JSContext *context = context_from_ptr(env, context_ptr);
    JS_FreeContext(context);
}

/**
 * Release JavaScript runtime.
 */
JNIEXPORT void JNICALL
Java_com_dokar_quickjs_QuickJs_releaseRuntime(JNIEnv *env, jobject this, jlong runtime_ptr) {
    if (runtime_ptr == 0) {
        return;
    }
    JSRuntime *runtime = runtime_from_ptr(env, runtime_ptr);
    JS_FreeRuntime(runtime);
}

/**
 * Define an object to the 'parent'.
 */
JNIEXPORT jlong JNICALL
Java_com_dokar_quickjs_QuickJs_defineObject(JNIEnv *env, jobject this,
                                            jlong globals_ptr,
                                            jlong context_ptr,
                                            jlong parent,
                                            jstring name,
                                            jobjectArray properties,
                                            jobjectArray function_names) {
    Globals *globals = globals_from_ptr(env, globals_ptr);
    if (globals == NULL) {
        return -1;
    }
    JSContext *context = context_from_ptr(env, context_ptr);
    if (context == NULL) {
        return -1;
    }
    int64_t parent_index = parent;
    uint32_t defined_size = cvector_size(globals->defined_js_objects);
    if (parent_index >= defined_size) {
        jni_throw_exception(env, "Parent handle out of the bounds.");
        return -1;
    }
    JSValue *parent_val = parent_index < 0 ? NULL : &globals->defined_js_objects[parent_index];
    // The global js value index
    int64_t handle = defined_size;
    JSValue result = define_js_object(env,
                                      context,
                                      globals,
                                      this,
                                      parent_val,
                                      handle,
                                      name,
                                      properties,
                                      function_names);
    // Insert at the target index
    cvector_push_back(globals->defined_js_objects, result);
    // Return the handle
    return handle;
}

/**
 * Define an object to the 'parent'.
 */
JNIEXPORT void JNICALL
Java_com_dokar_quickjs_QuickJs_defineFunction(JNIEnv *env, jobject this,
                                              jlong globals_ptr,
                                              jlong context_ptr,
                                              jstring name,
                                              jboolean is_async) {
    Globals *globals = globals_from_ptr(env, globals_ptr);
    if (globals == NULL) {
        return;
    }
    JSContext *context = context_from_ptr(env, context_ptr);
    if (context == NULL) {
        return;
    }
    define_js_function(env, context, globals, this, name, is_async);
}

/**
 * Run QuickJS GC.
 */
JNIEXPORT void JNICALL
Java_com_dokar_quickjs_QuickJs_gc(JNIEnv *env, jobject this, jlong runtime_ptr) {
    JSRuntime *runtime = runtime_from_ptr(env, runtime_ptr);
    JS_RunGC(runtime);
}

/**
 * Get QuickJS version.
 */
JNIEXPORT jstring JNICALL
Java_com_dokar_quickjs_QuickJs_nativeGetVersion(JNIEnv *env, jobject this) {
#ifdef CONFIG_VERSION
    return (*env)->NewStringUTF(env, CONFIG_VERSION);
#else
    return (*env)->NewStringUTF(env, "not_cofigured");
#endif
}

/**
 * Set memory limit for the runtime.
 */
JNIEXPORT void JNICALL
Java_com_dokar_quickjs_QuickJs_setMemoryLimit(JNIEnv *env, jobject this, jlong runtime_ptr,
                                              jlong byte_count) {
    JSRuntime *runtime = runtime_from_ptr(env, runtime_ptr);
    JS_SetMemoryLimit(runtime, byte_count);
}

/**
 * Set max stack size for the runtime.
 */
JNIEXPORT void JNICALL
Java_com_dokar_quickjs_QuickJs_setMaxStackSize(JNIEnv *env, jobject this, jlong runtime_ptr,
                                               jlong byte_count) {
    JSRuntime *runtime = runtime_from_ptr(env, runtime_ptr);
    // We need this to update the stack top pointer before updating the max stack size,
    // otherwise, when calling it in a different thread rather than the initialization thread,
    // we may get unexpected stack overflow errors.
    JS_UpdateStackTop(runtime);
    JS_SetMaxStackSize(runtime, byte_count);
}

/**
 * Get the runtime memory usage.
 */
JNIEXPORT jobject JNICALL
Java_com_dokar_quickjs_QuickJs_getMemoryUsage(JNIEnv *env, jobject this, jlong runtime_ptr) {
    JSMemoryUsage memory_usage;
    JSRuntime *runtime = runtime_from_ptr(env, runtime_ptr);
    JS_ComputeMemoryUsage(runtime, &memory_usage);

    jclass cls = cls_memory_usage(env);
    jobject usage = (*env)->NewObject(env, cls, method_memory_usage_init(env),
                                      memory_usage.malloc_limit,
                                      memory_usage.malloc_size,
                                      memory_usage.malloc_count,
                                      memory_usage.memory_used_size,
                                      memory_usage.memory_used_count);
    return usage;
}

jobject handle_eval_result(JNIEnv *env,
                           JSContext *context,
                           Globals *globals,
                           JSValue value,
                           int async) {
    if (check_js_context_exception(env, context)) {
        JS_FreeValue(context, value);
        return NULL;
    }

    if (JS_IsException(value)) {
        // TODO: Handle this case
        return NULL;
    }

    int tag = JS_VALUE_GET_NORM_TAG(value);
    int is_compiled_value = tag == JS_TAG_FUNCTION_BYTECODE || tag == JS_TAG_MODULE;
    if (async && !is_compiled_value) {
        // Ensure the result is a promise
        if (!js_is_promise(context, value)) {
            jni_throw_exception(env, "Require the async eval flag.");
            JS_FreeValue(context, value);
            return NULL;
        }

        if (globals->evaluate_result_promise != NULL) {
            // Free the unused result, if any
            JS_FreeValue(context, *globals->evaluate_result_promise);
        }

        // Save it to the globals
        // Suppress the warning, we know that value will live long enough
        globals->evaluate_result_promise = malloc(sizeof(JSValue));
        *globals->evaluate_result_promise = value;

        // This result should not be used
        return NULL;
    } else {
        jobject result = js_value_to_jobject(env, context, value);
        JS_FreeValue(context, value);
        return result;
    }
}

jobject eval(JNIEnv *env, jlong context_ptr,
             jlong globals_ptr,
             jstring jfilename,
             jstring jcode,
             int eval_flags) {
    const char *filename = (*env)->GetStringUTFChars(env, jfilename, NULL);
    if (filename == NULL) {
        jni_throw_exception(env, "Cannot read filename.");
        return NULL;
    }

    const char *code = (*env)->GetStringUTFChars(env, jcode, NULL);
    if (code == NULL) {
        jni_throw_exception(env, "Cannot read code.");
        return NULL;
    }

    JSContext *context = context_from_ptr(env, context_ptr);
    if (context == NULL) {
        return NULL;
    }

    Globals *globals = globals_from_ptr(env, globals_ptr);
    if (globals == NULL) {
        return NULL;
    }

    // Update the stack top pointer before running the code, otherwise, when calling
    // this in a different thread rather than the initialization, unexpected stack overflow
    // errors may occur.
    JS_UpdateStackTop(JS_GetRuntime(context));

    // Run code
    JSValue value = JS_Eval(context, code, strlen(code), filename, eval_flags);

    // Free strings
    (*env)->ReleaseStringUTFChars(env, jfilename, filename);
    (*env)->ReleaseStringUTFChars(env, jcode, code);

    int async = (eval_flags & JS_EVAL_FLAG_ASYNC) != 0;

    return handle_eval_result(env, context, globals, value, async);
}

/**
 * Compile JavaScript code to bytecode.
 */
JNIEXPORT jbyteArray JNICALL
Java_com_dokar_quickjs_QuickJs_compile(JNIEnv *env, jobject this, jlong context_ptr,
                                       jlong globals_ptr,
                                       jstring jfilename,
                                       jstring jcode,
                                       jboolean as_module) {
    int eval_flags = JS_EVAL_FLAG_COMPILE_ONLY | JS_EVAL_FLAG_ASYNC;
    if (as_module) {
        eval_flags |= JS_EVAL_TYPE_MODULE;
    }
    Globals *globals = globals_from_ptr(env, globals_ptr);
    if (globals == NULL) {
        return NULL;
    }
    return eval(env, context_ptr, globals_ptr, jfilename, jcode, eval_flags);
}

/**
 * Evaluate JavaScript code.
 */
JNIEXPORT jobject JNICALL
Java_com_dokar_quickjs_QuickJs_evaluate(JNIEnv *env,
                                        jobject this,
                                        jlong context_ptr,
                                        jlong globals_ptr,
                                        jstring jfilename,
                                        jstring jcode,
                                        jboolean as_module) {
    int eval_flags = JS_EVAL_FLAG_ASYNC;
    eval_flags |= as_module ? JS_EVAL_TYPE_MODULE : JS_EVAL_TYPE_GLOBAL;
    return eval(env, context_ptr, globals_ptr, jfilename, jcode, eval_flags);
}

/**
 * Evaluate compiled bytecode.
 */
JNIEXPORT jobject JNICALL
Java_com_dokar_quickjs_QuickJs_execute(JNIEnv *env, jobject this, jlong context_ptr,
                                       jlong globals_ptr,
                                       jbyteArray jbuffer) {
    JSContext *context = context_from_ptr(env, context_ptr);
    if (context == NULL) {
        return NULL;
    }

    Globals *globals = globals_from_ptr(env, globals_ptr);
    if (globals == NULL) {
        return NULL;
    }

    jlong buf_len = (*env)->GetArrayLength(env, jbuffer);
    jbyte *buffer = (*env)->GetByteArrayElements(env, jbuffer, NULL);

    // Read buffer
    JSValue bytecode = JS_ReadObject(context, (uint8_t *) buffer, buf_len, JS_READ_OBJ_BYTECODE);
    if (JS_IsException(bytecode)) {
        (*env)->ReleaseByteArrayElements(env, jbuffer, buffer, 0);
        jni_throw_exception(env, "Cannot read buffer as bytecode.");
        return NULL;
    }

    JS_UpdateStackTop(JS_GetRuntime(context));

    // Eval
    JSValue value = JS_EvalFunction(context, bytecode);

    (*env)->ReleaseByteArrayElements(env, jbuffer, buffer, 0);

    return handle_eval_result(env, context, globals, value, 1);
}

/**
 * Call a JavaScript function.
 *
 * Promise resolve() and reject() handles are passed as the first two parameters to onCallFunction.
 * Other functions not currently supported.
 */
JNIEXPORT void JNICALL
Java_com_dokar_quickjs_QuickJs_callJsFunction(JNIEnv *env,
                                              jobject this,
                                              jlong context_ptr,
                                              jlong globals_ptr,
                                              jlong handle,
                                              jobjectArray args) {
    JSContext *context = context_from_ptr(env, context_ptr);
    if (context == NULL) {
        return;
    }

    if (handle < 0) {
        jni_throw_exception(env, "Invalid handle: %ld", handle);
        return;
    }

    Globals *globals = globals_from_ptr(env, globals_ptr);
    if (globals == NULL) {
        return;
    }

    if (globals->created_js_functions == NULL) {
        jni_throw_exception(env, "Function not found.");
        return;
    }

    size_t size = cvector_size(globals->created_js_functions);
    uint64_t index = (uint64_t) handle;
    if (index >= size) {
        jni_throw_exception(env, "Invalid handle: %ld", handle);
        return;
    }

    JSValue func = globals->created_js_functions[index];
    if (!JS_IsFunction(context, func)) {
        jni_throw_exception(env, "Can't get a valid function.");
        return;
    }

    // Map args
    int argc = args != NULL ? (*env)->GetArrayLength(env, args) : 0;
    JSValue argv[argc];
    for (int i = 0; i < argc; ++i) {
        jobject element = (*env)->GetObjectArrayElement(env, args, i);
        JSValue item = jobject_to_js_value(env, context, element);
        if (JS_IsException(item)) {
            // Mapping has failed, cleanup and return
            for (int j = 0; j < i; ++j) {
                JS_FreeValue(context, argv[j]);
            }
            (*env)->DeleteLocalRef(env, element);
            jni_throw_exception(env, "Failed to map java type to js type, arg index: %d", i);
            return;
        }
        argv[i] = item;
        (*env)->DeleteLocalRef(env, element);
    }

    JSValue result = JS_Call(context, func, JS_NULL, argc, argv);

    // Free arguments
    for (int i = 0; i < argc; ++i) {
        JS_FreeValue(context, argv[i]);
    }

    // Do nothing with the result
    JS_FreeValue(context, result);
}

/**
 * Try to execute a pending JS job.
 *
 * @return true if executed, false if no job, or failed to execute.
 */
JNIEXPORT jboolean JNICALL
Java_com_dokar_quickjs_QuickJs_tryExecutePendingJob(JNIEnv *env,
                                                    jobject this,
                                                    jlong context_ptr) {
    JSContext *context = context_from_ptr(env, context_ptr);
    if (context == NULL) {
        return JNI_FALSE;
    }
    // Try find pending jobs to execute
    JSContext *ctx;
    int ret = JS_ExecutePendingJob(JS_GetRuntime(context), &ctx);
    if (ret == 0) {
        // No jobs
        return JNI_FALSE;
    }
    if (ret < 0) {
        jni_throw_exception(env, "Failed to execute pending jobs.");
        return JNI_FALSE;
    }
    return JNI_TRUE;
}

/**
 * Try get result from the evaluate result promise. This function cannot be called multiple times.
 */
JNIEXPORT jobject JNICALL
Java_com_dokar_quickjs_QuickJs_tryResolveExecuteResult(JNIEnv *env,
                                                       jobject this,
                                                       jlong context_ptr,
                                                       jlong globals_ptr) {
    JSContext *context = context_from_ptr(env, context_ptr);
    if (context == NULL) {
        return NULL;
    }
    Globals *globals = globals_from_ptr(env, globals_ptr);
    if (globals == NULL) {
        return NULL;
    }
    if (globals->evaluate_result_promise == NULL) {
        jni_throw_exception(env, "Result promise not found. Have you evaluated a script?");
        return NULL;
    }
    JSValue result_promise = *globals->evaluate_result_promise;
    if (!js_is_promise(context, result_promise)) {
        JS_FreeValue(context, result_promise);
        globals->evaluate_result_promise = NULL;
        jni_throw_exception(env, "Invalid result promise object.");
        return NULL;
    }
    JSPromiseStateEnum state = JS_PromiseState(context, result_promise);
    jobject result;
    if (state == JS_PROMISE_FULFILLED) {
        JSValue js_result = js_promise_get_fulfilled_value(context, result_promise);
        if (JS_IsException(js_result)) {
            // Is it safe to ignore the exception? This happens when executing a compiled module.
            result = NULL;
        } else {
            result = js_value_to_jobject(env, context, js_result);
        }
        JS_FreeValue(context, js_result);
    } else if (state == JS_PROMISE_REJECTED) {
        JSValue js_result = JS_PromiseResult(context, result_promise);
        result = js_value_to_jobject(env, context, js_result);

        // Avoid resetting jni exception to null
        if (!JS_IsNull(js_result)) {
            jthrowable error;
            if ((*env)->IsInstanceOf(env, result, cls_throwable(env))) {
                error = (jthrowable) result;
            } else {
                const char *str = JS_ToCString(context, js_result);
                error = new_java_error(env, str);
                JS_FreeCString(context, str);
            }
            // Set exception
            jmethodID set_exception_method = method_quick_js_set_java_exception(env);
            (*env)->CallVoidMethod(env, this, set_exception_method, error);
        }

        JS_FreeValue(context, js_result);
    } else {
        // Code bug!
        log("The result promise is still pending, it may be a bug in the bridge library.");
        result = (*env)->NewStringUTF(env, "Promise { <state>: \"pending\" }");
    }
    // Clear the promise
    JS_FreeValue(context, result_promise);
    globals->evaluate_result_promise = NULL;
    return result;
}