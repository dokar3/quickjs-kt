#ifndef QJS_KT_JNI_H
#define QJS_KT_JNI_H

#include <pthread.h>
#include "cvector.h"
#include "quickjs.h"
#include "jni.h"

/**
 * Global objects for the wrapped runtime.
 */
typedef struct {
    /**
     * The owning QuickJs instance used by the synchronous module loader.
     */
    jobject module_loader_host;
    /** QuickJs.loadModule(String) method used by the module loader. */
    jmethodID load_module_method;
    /** QuickJs.getModuleSource(Object) content accessor. */
    jmethodID get_module_source_method;
    /** QuickJs.getModuleBytecode(Object) content accessor. */
    jmethodID get_module_bytecode_method;
    /** QuickJs.onModuleCompiled(String, ByteArray) notification callback. */
    jmethodID on_module_compiled_method;
    /** QuickJs.onModuleLoadFailed(String) failure callback. */
    jmethodID on_module_load_failed_method;
    /** Monotonic counter used to suppress parent notifications after a nested failure. */
    uint64_t module_load_failure_version;
    /**
     * Some JS values, used by C functions.
     */
    cvector_vector_type(JSValue)managed_js_values;
    /**
     * Defined JS objects, keep them to support nested define.
     */
    cvector_vector_type(JSValue)defined_js_objects;
    /**
     * Promise resolve/reject functions.
     */
    cvector_vector_type(JSValue)created_js_functions;
    /**
     * Global JNI refs.
     */
    cvector_vector_type(jobject)global_object_refs;
    /**
     * Result promises of eval calls. The index is exposed to Kotlin as an evaluation handle.
     */
    cvector_vector_type(JSValue)evaluate_result_promises;
    /**
     * Whether the corresponding evaluation result slot is reserved by Kotlin.
     */
    cvector_vector_type(uint8_t)evaluate_result_active;
    /**
     * The mutex which is used to protect the JS stack in a multi-threaded environment.
     * Scopes with a JS_UpdateStackTop() call are required to be locked.
     */
    pthread_mutex_t js_mutex;
} Globals;

#endif //QJS_KT_JNI_H
