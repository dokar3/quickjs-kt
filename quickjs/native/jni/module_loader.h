#ifndef QJS_KT_MODULE_LOADER_H
#define QJS_KT_MODULE_LOADER_H

#include "jni.h"
#include "quickjs.h"
#include "quickjs_jni.h"

/**
 * Installs the host-backed module loader for the runtime.
 *
 * Returns 1 on success and 0 when a required host method cannot be resolved.
 * A failed JNI lookup may leave an exception pending for the caller to handle.
 */
int install_module_loader(JNIEnv *env, JSRuntime *runtime, Globals *globals, jobject call_host);

#endif //QJS_KT_MODULE_LOADER_H
