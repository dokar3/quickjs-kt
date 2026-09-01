#include <stdlib.h>
#include <string.h>
#include "exception_util.h"
#include "jni_globals_generated.h"
#include "log_util.h"
#include "js_value_util.h"
#include "jni_string_util.h"

jthrowable new_qjs_exception(JNIEnv *env, const char *format, ...) {
    va_list args;
    va_start(args, format);
    int length = vsnprintf(NULL, 0, format, args);
    va_end(args);

    char *result = (char *) malloc(length + 1);

    va_start(args, format);
    vsnprintf(result, length + 1, format, args);
    va_end(args);

    jstring message = jni_string_from_utf8(env, result, (size_t) length);
    if (message == NULL) {
        free(result);
        return NULL;
    }
    jthrowable exception = (*env)->NewObject(env, cls_quick_js_exception(env),
                                             method_quick_js_exception_init(env), message);
    (*env)->DeleteLocalRef(env, message);
    free(result);
    return exception;
}

static void delete_local_ref(JNIEnv *env, jobject object) {
    if (object != NULL) {
        (*env)->DeleteLocalRef(env, object);
    }
}

jthrowable new_js_error_exception(JNIEnv *env,
                                  JSContext *context,
                                  JSValue error,
                                  const char *message,
                                  const char *stack) {
    char *read_stack = NULL;
    if (stack == NULL) {
        js_error_stack(context, error, &read_stack);
    }
    const char *js_stack = stack != NULL ? stack : read_stack;

    jstring j_message = message != NULL
                        ? jni_string_from_utf8_c_string(env, message)
                        : NULL;
    jstring j_stack = js_stack != NULL
                      ? jni_string_from_utf8_c_string(env, js_stack)
                      : NULL;
    free(read_stack);

    jthrowable exception = (*env)->NewObject(env, cls_quick_js_exception(env),
                                             method_quick_js_exception_init_with_stack(env),
                                             j_message, j_stack);
    delete_local_ref(env, j_message);
    delete_local_ref(env, j_stack);
    return exception;
}

void jni_throw_qjs_exception(JNIEnv *env, const char *format, ...) {
    va_list args;
    va_start(args, format);
    int length = vsnprintf(NULL, 0, format, args);
    va_end(args);

    char *result = (char *) malloc(length + 1);

    va_start(args, format);
    vsnprintf(result, length + 1, format, args);
    va_end(args);

    jstring message = jni_string_from_utf8(env, result, (size_t) length);
    if (message != NULL) {
        jthrowable exception = (*env)->NewObject(
                env,
                cls_quick_js_exception(env),
                method_quick_js_exception_init(env),
                message);
        if (exception != NULL) {
            (*env)->Throw(env, exception);
            (*env)->DeleteLocalRef(env, exception);
        }
        (*env)->DeleteLocalRef(env, message);
    }
    free(result);
}

jthrowable try_catch_java_exceptions(JNIEnv *env) {
    jthrowable exception = (*env)->ExceptionOccurred(env);
    if (exception != NULL) {
        (*env)->ExceptionClear(env);
        return exception;
    } else {
        return NULL;
    }
}

int check_js_context_exception(JNIEnv *env, JSContext *context) {
    JSValue exception = JS_GetException(context);
    int tag = JS_VALUE_GET_TAG(exception);
    // Check exception
    if (tag != JS_TAG_NULL && tag != JS_TAG_UNINITIALIZED) {
        char *message = NULL;
        char *stack = NULL;
        js_error_to_string(context, exception, &message, &stack);
        // Throw java exception
        jthrowable java_exception = new_js_error_exception(env, context, exception, message,
                                                          stack);
        JS_FreeValue(context, exception);
        if (java_exception != NULL) {
            (*env)->Throw(env, java_exception);
            (*env)->DeleteLocalRef(env, java_exception);
        } else {
            jni_throw_qjs_exception(env, "%s", message);
        }
        free(stack);
        free(message);
        return 1;
    } else {
        JS_FreeValue(context, exception);
        return 0;
    }
}
