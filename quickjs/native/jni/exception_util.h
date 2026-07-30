#ifndef QJS_KT_EXCEPTION_UTIL_H
#define QJS_KT_EXCEPTION_UTIL_H

#include <stdarg.h>
#include "jni.h"
#include "quickjs.h"

jthrowable new_qjs_exception(JNIEnv *env, const char *format, ...);

/**
 * Create a QuickJsException carrying the location and the stack trace of a js
 * error.
 *
 * @param message The already formatted message.
 * @param stack The stack trace of the error, pass NULL to read it from the
 * error, the caller keeps the ownership of it.
 */
jthrowable new_js_error_exception(JNIEnv *env,
                                  JSContext *context,
                                  JSValue error,
                                  const char *message,
                                  const char *stack);

void jni_throw_qjs_exception(JNIEnv *env, const char *format, ...);

jthrowable try_catch_java_exceptions(JNIEnv *env);

/**
 * Check and throw js context exception, if any.
 *
 * @return 1 if there is an exception in the context, 0 otherwise.
 */
int check_js_context_exception(JNIEnv *env, JSContext *context);

#endif //QJS_KT_EXCEPTION_UTIL_H
