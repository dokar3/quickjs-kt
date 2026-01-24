#ifndef QJS_KT_JNI_GLOBALS_H
#define QJS_KT_JNI_GLOBALS_H

#include "jni.h"

void cache_java_vm(JNIEnv *env);

JNIEnv *get_jni_env();

void clear_java_vm_cache();

int get_qjs_instance_count();

#endif //QJS_KT_JNI_GLOBALS_H
