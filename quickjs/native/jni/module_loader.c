#include <limits.h>
#include <string.h>
#include "module_loader.h"
#include "mapping/jobject_to_js_value.h"
#include "exception_util.h"
#include "jni_globals.h"
#include "jni_string_util.h"

/**
 * Converts a pending Java callback failure to a JavaScript error so it follows
 * QuickJS's normal operation-scoped exception path.
 */
static int forward_java_exception(JNIEnv *env,
                                  JSContext *context,
                                  const char *message,
                                  const char *module_name) {
    jthrowable exception = try_catch_java_exceptions(env);
    if (exception == NULL) {
        return 0;
    }

    JSValue js_error = jobject_to_js_value(env, context, NULL, exception);
    (*env)->DeleteLocalRef(env, exception);
    if (JS_IsException(js_error)) {
        return 1;
    }
    if (JS_IsUndefined(js_error)) {
        JS_ThrowInternalError(context, message, module_name);
    } else {
        JS_Throw(context, js_error);
    }
    return 1;
}

/** Notifies the host while the normalized name of a failed module is known. */
static void notify_module_load_failed(JNIEnv *env,
                                      Globals *globals,
                                      JSContext *context,
                                      jstring java_name,
                                      const char *module_name) {
    globals->module_load_failure_version++;
    (*env)->CallVoidMethod(
            env,
            globals->module_loader_host,
            globals->on_module_load_failed_method,
            java_name);
    forward_java_exception(
            env, context,
            "Module load failure callback failed for '%s'.", module_name);
}

/** Serializes a source-compiled module and immediately notifies its owner. */
static int notify_module_compiled(JNIEnv *env,
                                  Globals *globals,
                                  JSContext *context,
                                  jstring java_name,
                                  const char *module_name,
                                  JSValue module) {
    size_t bytecode_length;
    uint8_t *bytecode = JS_WriteObject(
            context,
            &bytecode_length,
            module,
            JS_WRITE_OBJ_BYTECODE | JS_WRITE_OBJ_REFERENCE);
    if (bytecode == NULL) {
        JS_ThrowInternalError(context, "Cannot write bytecode for module '%s'.", module_name);
        return 0;
    }
    if (bytecode_length > INT_MAX) {
        js_free(context, bytecode);
        JS_ThrowInternalError(context, "Bytecode for module '%s' is too large.", module_name);
        return 0;
    }

    jbyteArray java_bytecode = (*env)->NewByteArray(env, (jsize) bytecode_length);
    if (java_bytecode == NULL) {
        js_free(context, bytecode);
        if (!forward_java_exception(
                env, context,
                "Cannot allocate bytecode for module '%s'.", module_name)) {
            JS_ThrowInternalError(
                    context, "Cannot allocate bytecode for module '%s'.", module_name);
        }
        return 0;
    }

    (*env)->SetByteArrayRegion(
            env,
            java_bytecode,
            0,
            (jsize) bytecode_length,
            (jbyte *) bytecode);
    js_free(context, bytecode);
    if (forward_java_exception(
            env, context,
            "Cannot copy bytecode for module '%s'.", module_name)) {
        (*env)->DeleteLocalRef(env, java_bytecode);
        return 0;
    }

    (*env)->CallVoidMethod(
            env,
            globals->module_loader_host,
            globals->on_module_compiled_method,
            java_name,
            java_bytecode);
    (*env)->DeleteLocalRef(env, java_bytecode);
    if (forward_java_exception(
            env, context,
            "Module compilation callback failed for '%s'.", module_name)) {
        return 0;
    }
    return 1;
}

/** Reads module bytecode supplied by the host loader. */
static JSValue read_module_bytecode(JNIEnv *env,
                                    JSContext *context,
                                    jbyteArray java_bytecode,
                                    const char *module_name) {
    jsize bytecode_length = (*env)->GetArrayLength(env, java_bytecode);
    if (forward_java_exception(
            env, context,
            "Cannot inspect bytecode for module '%s'.", module_name)) {
        return JS_EXCEPTION;
    }
    jbyte *bytecode = (*env)->GetByteArrayElements(env, java_bytecode, NULL);
    if (bytecode == NULL) {
        if (!forward_java_exception(
                env, context,
                "Cannot read bytecode for module '%s'.", module_name)) {
            JS_ThrowInternalError(context, "Cannot read bytecode for module '%s'.", module_name);
        }
        return JS_EXCEPTION;
    }

    JSValue module = JS_ReadObject(
            context,
            (uint8_t *) bytecode,
            (size_t) bytecode_length,
            JS_READ_OBJ_BYTECODE | JS_READ_OBJ_REFERENCE);
    (*env)->ReleaseByteArrayElements(env, java_bytecode, bytecode, JNI_ABORT);
    return module;
}

/** Verifies that cached bytecode belongs to the normalized requested name. */
static int validate_module_name(JSContext *context,
                                JSValue module,
                                const char *requested_name) {
    JSModuleDef *definition = JS_VALUE_GET_PTR(module);
    JSAtom name_atom = JS_GetModuleName(context, definition);
    const char *bytecode_name = JS_AtomToCString(context, name_atom);
    JS_FreeAtom(context, name_atom);
    if (bytecode_name == NULL) {
        JS_ThrowInternalError(context, "Cannot read module bytecode name for '%s'.", requested_name);
        return 0;
    }
    int matches = strcmp(bytecode_name, requested_name) == 0;
    if (!matches) {
        JS_ThrowReferenceError(
                context,
                "Module bytecode name '%s' does not match '%s'.",
                bytecode_name,
                requested_name);
    }
    JS_FreeCString(context, bytecode_name);
    return matches;
}

/** Resolves a module specifier through the runtime-scoped Kotlin normalizer. */
static char *normalize_module(JSContext *context,
                              const char *base_name,
                              const char *requested_name,
                              void *opaque) {
    Globals *globals = (Globals *) opaque;
    JNIEnv *env = get_jni_env();
    if (env == NULL) {
        JS_ThrowInternalError(
                context,
                "Cannot access JNI while normalizing module '%s'.",
                requested_name);
        return NULL;
    }

    jstring java_base_name = jni_string_from_utf8_c_string(env, base_name);
    if (java_base_name == NULL) {
        if (!forward_java_exception(
                env, context,
                "Cannot allocate module base name for '%s'.", requested_name)) {
            JS_ThrowInternalError(
                    context, "Cannot allocate module base name for '%s'.", requested_name);
        }
        return NULL;
    }
    jstring java_requested_name = jni_string_from_utf8_c_string(env, requested_name);
    if (java_requested_name == NULL) {
        if (!forward_java_exception(
                env, context,
                "Cannot allocate requested module name '%s'.", requested_name)) {
            JS_ThrowInternalError(
                    context, "Cannot allocate requested module name '%s'.", requested_name);
        }
        (*env)->DeleteLocalRef(env, java_base_name);
        return NULL;
    }

    jstring java_normalized_name = (jstring) (*env)->CallObjectMethod(
            env,
            globals->module_loader_host,
            globals->normalize_module_method,
            java_base_name,
            java_requested_name);
    (*env)->DeleteLocalRef(env, java_requested_name);
    (*env)->DeleteLocalRef(env, java_base_name);
    if (forward_java_exception(
            env, context,
            "Kotlin module normalizer failed for '%s'.", requested_name)) {
        return NULL;
    }
    if (java_normalized_name == NULL) {
        JS_ThrowInternalError(
                context, "Module normalizer returned null for '%s'.", requested_name);
        return NULL;
    }

    JniUtf8String normalized_name;
    if (!jni_string_to_utf8(env, java_normalized_name, &normalized_name)) {
        if (!forward_java_exception(
                env, context,
                "Cannot read normalized module name for '%s'.", requested_name)) {
            JS_ThrowInternalError(
                    context, "Cannot read normalized module name for '%s'.", requested_name);
        }
        (*env)->DeleteLocalRef(env, java_normalized_name);
        return NULL;
    }
    // The QuickJS module-loader ABI uses C strings and cannot represent NUL in module names.
    if (memchr(normalized_name.data, '\0', normalized_name.length) != NULL) {
        JS_ThrowTypeError(context, "Normalized module name cannot contain NUL.");
        jni_utf8_string_release(&normalized_name);
        (*env)->DeleteLocalRef(env, java_normalized_name);
        return NULL;
    }
    char *result = js_malloc(context, normalized_name.length + 1);
    if (result != NULL) {
        memcpy(result, normalized_name.data, normalized_name.length + 1);
    }
    jni_utf8_string_release(&normalized_name);
    (*env)->DeleteLocalRef(env, java_normalized_name);
    return result;
}

/**
 * Loads source or bytecode from the runtime-scoped Kotlin ModuleLoader.

 */
static JSModuleDef *load_module(JSContext *context, const char *module_name, void *opaque) {
    Globals *globals = (Globals *) opaque;
    JNIEnv *env = get_jni_env();
    if (env == NULL) {
        JS_ThrowInternalError(context, "Cannot access JNI while loading module '%s'.", module_name);
        return NULL;
    }

    jstring java_name = jni_string_from_utf8_c_string(env, module_name);
    if (java_name == NULL) {
        if (!forward_java_exception(
                env, context,
                "Cannot allocate module name '%s'.", module_name)) {
            JS_ThrowInternalError(context, "Cannot allocate module name '%s'.", module_name);
        }
        return NULL;
    }

    jobject content = (*env)->CallObjectMethod(
            env,
            globals->module_loader_host,
            globals->load_module_method,
            java_name);
    JSModuleDef *definition = NULL;
    uint64_t failure_version = globals->module_load_failure_version;
    if (forward_java_exception(
            env, context,
            "Kotlin module loader failed for '%s'.", module_name)) {
        goto notify_failure;
    }
    if (content == NULL) {
        JS_ThrowReferenceError(context, "could not load module '%s'", module_name);
        goto notify_failure;
    }

    jstring java_source = (jstring) (*env)->CallObjectMethod(
            env,
            globals->module_loader_host,
            globals->get_module_source_method,
            content);
    if (forward_java_exception(
            env, context,
            "Cannot inspect module source for '%s'.", module_name)) {
        goto notify_failure;
    }

    JSValue module = JS_UNDEFINED;
    if (java_source != NULL) {
        JniUtf8String source;
        if (!jni_string_to_utf8(env, java_source, &source)) {
            if (!forward_java_exception(
                    env, context,
                    "Cannot read module source '%s'.", module_name)) {
                JS_ThrowInternalError(context, "Cannot read module source '%s'.", module_name);
            }
            (*env)->DeleteLocalRef(env, java_source);
            goto notify_failure;
        }
        module = JS_Eval(
                context,
                source.data,
                source.length,
                module_name,
                JS_EVAL_TYPE_MODULE | JS_EVAL_FLAG_COMPILE_ONLY);
        jni_utf8_string_release(&source);
        (*env)->DeleteLocalRef(env, java_source);
        if (!JS_IsException(module) && JS_VALUE_GET_TAG(module) == JS_TAG_MODULE &&
            !notify_module_compiled(
                    env, globals, context, java_name, module_name, module)) {
            JS_FreeValue(context, module);
            module = JS_EXCEPTION;
        }
    } else {
        jbyteArray java_bytecode = (jbyteArray) (*env)->CallObjectMethod(
                env,
                globals->module_loader_host,
                globals->get_module_bytecode_method,
                content);
        if (forward_java_exception(
                env, context,
                "Cannot inspect module bytecode for '%s'.", module_name)) {
            goto notify_failure;
        }
        if (java_bytecode == NULL) {
            JS_ThrowTypeError(context, "Unsupported module content for '%s'.", module_name);
            goto notify_failure;
        }
        module = read_module_bytecode(env, context, java_bytecode, module_name);
        (*env)->DeleteLocalRef(env, java_bytecode);
        if (!JS_IsException(module) && JS_VALUE_GET_TAG(module) == JS_TAG_MODULE &&
            !validate_module_name(context, module, module_name)) {
            JS_FreeValue(context, module);
            module = JS_EXCEPTION;
        }
    }

    if (JS_IsException(module)) {
        goto notify_failure;
    }
    if (JS_VALUE_GET_TAG(module) != JS_TAG_MODULE) {
        JS_ThrowTypeError(context, "Content for '%s' is not an ES module.", module_name);
        JS_FreeValue(context, module);
        goto notify_failure;
    }

    definition = JS_VALUE_GET_PTR(module);
    JS_FreeValue(context, module);
    goto cleanup_refs;

notify_failure:
    if (failure_version == globals->module_load_failure_version) {
        notify_module_load_failed(env, globals, context, java_name, module_name);
    }

cleanup_refs:
    if (content != NULL) {
        (*env)->DeleteLocalRef(env, content);
    }
    (*env)->DeleteLocalRef(env, java_name);
    return definition;
}

int install_module_loader(JNIEnv *env, JSRuntime *runtime, Globals *globals, jobject call_host) {
    jclass host_class = (*env)->GetObjectClass(env, call_host);
    if (host_class == NULL) {
        return 0;
    }
    jmethodID has_module_normalizer_method = (*env)->GetMethodID(
            env,
            host_class,
            "hasModuleNormalizer",
            "()Z");
    jmethodID normalize_module_method = (*env)->GetMethodID(
            env,
            host_class,
            "normalizeModule",
            "(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;");
    jmethodID load_module_method = (*env)->GetMethodID(
            env,
            host_class,
            "loadModule",
            "(Ljava/lang/String;)Ljava/lang/Object;");
    jmethodID get_module_source_method = (*env)->GetMethodID(
            env,
            host_class,
            "getModuleSource",
            "(Ljava/lang/Object;)Ljava/lang/String;");
    jmethodID get_module_bytecode_method = (*env)->GetMethodID(
            env,
            host_class,
            "getModuleBytecode",
            "(Ljava/lang/Object;)[B");
    jmethodID on_module_compiled_method = (*env)->GetMethodID(
            env,
            host_class,
            "onModuleCompiled",
            "(Ljava/lang/String;[B)V");
    jmethodID on_module_load_failed_method = (*env)->GetMethodID(
            env,
            host_class,
            "onModuleLoadFailed",
            "(Ljava/lang/String;)V");

    if (has_module_normalizer_method == NULL ||
        normalize_module_method == NULL ||
        load_module_method == NULL ||
        get_module_source_method == NULL ||
        get_module_bytecode_method == NULL ||
        on_module_compiled_method == NULL ||
        on_module_load_failed_method == NULL) {
        (*env)->DeleteLocalRef(env, host_class);
        return 0;
    }

    jboolean has_module_normalizer = (*env)->CallBooleanMethod(
            env, call_host, has_module_normalizer_method);
    (*env)->DeleteLocalRef(env, host_class);
    if ((*env)->ExceptionCheck(env)) {
        return 0;
    }

    globals->module_loader_host = call_host;
    globals->normalize_module_method = normalize_module_method;
    globals->load_module_method = load_module_method;
    globals->get_module_source_method = get_module_source_method;
    globals->get_module_bytecode_method = get_module_bytecode_method;
    globals->on_module_compiled_method = on_module_compiled_method;
    globals->on_module_load_failed_method = on_module_load_failed_method;
    JS_SetModuleLoaderFunc(
            runtime,
            has_module_normalizer ? normalize_module : NULL,
            load_module,
            globals);
    return 1;
}
