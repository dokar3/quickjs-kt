#include <stdlib.h>
#include <string.h>
#include "exception_util.h"
#include "jni_globals_generated.h"
#include "log_util.h"
#include "js_value_util.h"
#include "jni_string_util.h"

static const char formatting_failure_message[] = "Cannot format QuickJS exception message.";

/** Creates a QuickJsException from an explicit-length UTF-8/WTF-8 message. */
static jthrowable new_qjs_exception_from_utf8(JNIEnv *env,
                                              const char *message,
                                              size_t message_length) {
    jstring java_message = jni_string_from_utf8(env, message, message_length);
    if (java_message == NULL) {
        return NULL;
    }
    jthrowable exception = (*env)->NewObject(env, cls_quick_js_exception(env),
                                             method_quick_js_exception_init(env),
                                             java_message);
    (*env)->DeleteLocalRef(env, java_message);
    return exception;
}

/** Throws an explicit-length message and guarantees a fallback when JNI stays silent. */
static void throw_qjs_exception_from_utf8(JNIEnv *env,
                                          const char *message,
                                          size_t message_length) {
    jthrowable exception = new_qjs_exception_from_utf8(env, message, message_length);
    if (exception != NULL) {
        (*env)->Throw(env, exception);
        (*env)->DeleteLocalRef(env, exception);
    } else if (!(*env)->ExceptionCheck(env)) {
        jclass exception_class = cls_quick_js_exception(env);
        if (exception_class != NULL) {
            (*env)->ThrowNew(env, exception_class, formatting_failure_message);
        }
    }
}

/** Formats a message into an owned buffer and rejects all formatting failures. */
static int format_message(const char *format,
                          va_list args,
                          char **result,
                          size_t *result_length) {
    *result = NULL;
    *result_length = 0;

    va_list length_args;
    va_copy(length_args, args);
    int length = vsnprintf(NULL, 0, format, length_args);
    va_end(length_args);
    if (length < 0) {
        return 0;
    }

    size_t capacity = (size_t) length + 1;
    char *buffer = (char *) malloc(capacity);
    if (buffer == NULL) {
        return 0;
    }

    va_list write_args;
    va_copy(write_args, args);
    int written = vsnprintf(buffer, capacity, format, write_args);
    va_end(write_args);
    if (written != length) {
        free(buffer);
        return 0;
    }

    *result = buffer;
    *result_length = (size_t) length;
    return 1;
}

jthrowable new_qjs_exception(JNIEnv *env, const char *format, ...) {
    char *message;
    size_t message_length;
    va_list args;
    va_start(args, format);
    int formatted = format_message(format, args, &message, &message_length);
    va_end(args);

    if (!formatted) {
        return new_qjs_exception_from_utf8(
                env,
                formatting_failure_message,
                sizeof(formatting_failure_message) - 1);
    }

    jthrowable exception = new_qjs_exception_from_utf8(env, message, message_length);
    free(message);
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
                                  size_t message_length,
                                  const char *stack) {
    char *read_stack = NULL;
    if (stack == NULL) {
        js_error_stack(context, error, &read_stack);
    }
    const char *js_stack = stack != NULL ? stack : read_stack;

    jstring j_message = message != NULL
                        ? jni_string_from_utf8(env, message, message_length)
                        : NULL;
    if (message != NULL && j_message == NULL) {
        free(read_stack);
        return NULL;
    }
    jstring j_stack = js_stack != NULL
                      ? jni_string_from_utf8_c_string(env, js_stack)
                      : NULL;
    if (js_stack != NULL && j_stack == NULL) {
        delete_local_ref(env, j_message);
        free(read_stack);
        return NULL;
    }
    free(read_stack);

    jthrowable exception = (*env)->NewObject(env, cls_quick_js_exception(env),
                                             method_quick_js_exception_init_with_stack(env),
                                             j_message, j_stack);
    delete_local_ref(env, j_message);
    delete_local_ref(env, j_stack);
    return exception;
}

void jni_throw_qjs_exception(JNIEnv *env, const char *format, ...) {
    char *message;
    size_t message_length;
    va_list args;
    va_start(args, format);
    int formatted = format_message(format, args, &message, &message_length);
    va_end(args);

    if (formatted) {
        throw_qjs_exception_from_utf8(env, message, message_length);
        free(message);
    } else {
        throw_qjs_exception_from_utf8(
                env,
                formatting_failure_message,
                sizeof(formatting_failure_message) - 1);
    }
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
        size_t message_length = 0;
        char *stack = NULL;
        js_error_to_string(context, exception, &message, &message_length, &stack);
        // Throw java exception
        jthrowable java_exception = new_js_error_exception(
                env,
                context,
                exception,
                message,
                message_length,
                stack);
        JS_FreeValue(context, exception);
        if (java_exception != NULL) {
            (*env)->Throw(env, java_exception);
            (*env)->DeleteLocalRef(env, java_exception);
        } else if (!(*env)->ExceptionCheck(env)) {
            const char *fallback_message = message != NULL
                                           ? message
                                           : formatting_failure_message;
            size_t fallback_message_length = message != NULL
                                             ? message_length
                                             : sizeof(formatting_failure_message) - 1;
            throw_qjs_exception_from_utf8(
                    env,
                    fallback_message,
                    fallback_message_length);
        }
        free(stack);
        free(message);
        return 1;
    } else {
        JS_FreeValue(context, exception);
        return 0;
    }
}
