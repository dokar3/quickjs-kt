#ifndef QJS_KT_JNI_H
#define QJS_KT_JNI_H

#include "cvector.h"
#include "quickjs.h"
#include "jni.h"

/**
 * Global objects for the wrapped runtime.
 */
typedef struct {
    cvector_vector_type(JSValue)managed_js_values;
    cvector_vector_type(JSValue)defined_js_objects;
    cvector_vector_type(JSValue)created_js_functions;
    cvector_vector_type(jobject)global_object_refs;
    JSValue *evaluate_result_promise;
} Globals;

#endif //QJS_KT_JNI_H
