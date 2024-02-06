#include "jni_globals.h"
#include "log_util.h"

static JavaVM *vm = NULL;

void cache_java_vm(JNIEnv *env) {
    (*env)->GetJavaVM(env, &vm);
}

JNIEnv *get_jni_env() {
    if (vm == NULL) {
        log("Cannot get jni env because the vm is not cached.");
        return NULL;
    }
    JNIEnv *env = NULL;
    int attached = 0;
    jint get_env_result = (*vm)->GetEnv(vm, (void **) &env, JNI_VERSION_1_6);
    if (get_env_result == JNI_OK) {
        attached = 1;
    } else if (get_env_result == JNI_EDETACHED) {
        // Got a warning on Android Studio when casting &env to (void **)
#pragma clang diagnostic push
#pragma clang diagnostic ignored "-Wincompatible-pointer-types"
        if ((*vm)->AttachCurrentThread(vm, (void **) &env, NULL) == JNI_OK) {
#pragma clang diagnostic pop
            attached = 1;
        } else {
            log("Failed to attach current thread.");
        }
    } else if (get_env_result == JNI_EVERSION) {
        log("Unsupported JNI version.");
    }
    if (attached == 0) {
        return NULL;
    }
    return env;
}


void clear_java_vm_cache() {
    vm = NULL;
}