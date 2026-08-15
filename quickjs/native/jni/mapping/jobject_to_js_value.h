#ifndef QJS_KT_JOBJECT_TO_JS_VALUE_H
#define QJS_KT_JOBJECT_TO_JS_VALUE_H

#include "jni.h"
#include "quickjs.h"

/**
 * Convert java JsValue to QuickJS JsValue.
 */
JSValue jobject_to_js_value(JNIEnv *env, JSContext *context, jobject visited_set, jobject value);

/**
 * Convert a Java throwable to a JavaScript Error.
 */
JSValue java_throwable_to_js_error(JNIEnv *env, JSContext *context, jthrowable throwable);

#endif //QJS_KT_JOBJECT_TO_JS_VALUE_H
