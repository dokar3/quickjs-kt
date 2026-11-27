#include <stdlib.h>
#include <string.h>

#include "quickjs_module_import.h"
#include "quickjs_interrupt.h"

typedef struct QjsModuleLoaderState {
    JSModuleNormalizeFunc *normalize;
    QjsModuleLoaderWithTypeFunc *loader;
    void *opaque;
    void *interrupt_state;
} QjsModuleLoaderState;

static int qjs_data_module_init(JSContext *ctx, JSModuleDef *m) {
    JSValue val = JS_GetModulePrivateValue(ctx, m);
    return JS_SetModuleExport(ctx, m, "default", val);
}

static JSModuleDef *qjs_create_data_module(
        JSContext *ctx,
        const char *module_name,
        JSValue val) {
    JSModuleDef *m = JS_NewCModule(ctx, module_name, qjs_data_module_init);
    if (m == NULL) {
        JS_FreeValue(ctx, val);
        return NULL;
    }
    if (JS_AddModuleExport(ctx, m, "default") < 0) {
        JS_FreeValue(ctx, val);
        JS_FreeValue(ctx, JS_MKPTR(JS_TAG_MODULE, m));
        return NULL;
    }
    JS_SetModulePrivateValue(ctx, m, val);
    return m;
}

int qjs_module_check_attributes(JSContext *ctx, void *opaque, JSValueConst attributes) {
    (void) opaque;
    JSPropertyEnum *tab = NULL;
    uint32_t len = 0;
    if (JS_GetOwnPropertyNames(
            ctx,
            &tab,
            &len,
            attributes,
            JS_GPN_ENUM_ONLY | JS_GPN_STRING_MASK) < 0) {
        return -1;
    }

    int ret = 0;
    for (uint32_t i = 0; i < len; i++) {
        size_t cstr_len = 0;
        const char *cstr = JS_AtomToCStringLen(ctx, &cstr_len, tab[i].atom);
        if (cstr == NULL) {
            ret = -1;
            break;
        }
        if (cstr_len != 4 || memcmp(cstr, "type", 4) != 0) {
            JS_ThrowTypeError(ctx, "import attribute '%s' is not supported", cstr);
            ret = -1;
        }
        JS_FreeCString(ctx, cstr);
        if (ret < 0) {
            break;
        }
    }
    JS_FreePropertyEnum(ctx, tab, len);
    return ret;
}

QjsModuleImportType qjs_module_get_import_type(JSContext *ctx, JSValueConst attributes) {
    if (JS_IsUndefined(attributes)) {
        return QJS_MODULE_IMPORT_TYPE_JS;
    }
    JSValue type_val = JS_GetPropertyStr(ctx, attributes, "type");
    if (JS_IsException(type_val)) {
        return QJS_MODULE_IMPORT_TYPE_ERROR;
    }
    if (!JS_IsString(type_val)) {
        JS_FreeValue(ctx, type_val);
        return QJS_MODULE_IMPORT_TYPE_JS;
    }
    size_t len = 0;
    const char *cstr = JS_ToCStringLen(ctx, &len, type_val);
    JS_FreeValue(ctx, type_val);
    if (cstr == NULL) {
        return QJS_MODULE_IMPORT_TYPE_ERROR;
    }

    QjsModuleImportType result;
    if (len == 4 && memcmp(cstr, "text", 4) == 0) {
        result = QJS_MODULE_IMPORT_TYPE_TEXT;
    } else if (len == 4 && memcmp(cstr, "json", 4) == 0) {
        result = QJS_MODULE_IMPORT_TYPE_JSON;
    } else if (len == 5 && memcmp(cstr, "bytes", 5) == 0) {
        result = QJS_MODULE_IMPORT_TYPE_BYTES;
    } else {
        JS_ThrowTypeError(ctx, "unsupported module type: '%s'", cstr);
        result = QJS_MODULE_IMPORT_TYPE_ERROR;
    }
    JS_FreeCString(ctx, cstr);
    return result;
}

JSModuleDef *qjs_new_text_module(
        JSContext *ctx,
        const char *module_name,
        const char *utf8,
        size_t utf8_len) {
    if (utf8 == NULL) {
        if (utf8_len > 0) {
            JS_ThrowTypeError(ctx, "text module buffer is NULL");
            return NULL;
        }
        utf8 = "";
    }
    JSValue val = JS_NewStringLen(ctx, utf8, utf8_len);
    if (JS_IsException(val)) {
        return NULL;
    }
    return qjs_create_data_module(ctx, module_name, val);
}

JSModuleDef *qjs_new_json_module(
        JSContext *ctx,
        const char *module_name,
        const char *utf8_nul_terminated,
        size_t utf8_len) {
    if (utf8_nul_terminated == NULL) {
        if (utf8_len > 0) {
            JS_ThrowTypeError(ctx, "JSON module buffer is NULL");
            return NULL;
        }
        utf8_nul_terminated = "";
    }
    if (utf8_len > 0 && memchr(utf8_nul_terminated, '\0', utf8_len) != NULL) {
        JS_ThrowSyntaxError(ctx, "Unexpected NUL character in JSON module '%s'", module_name);
        return NULL;
    }
    JSValue val = JS_ParseJSON2(ctx, utf8_nul_terminated, utf8_len, module_name, 0);
    if (JS_IsException(val)) {
        return NULL;
    }
    return qjs_create_data_module(ctx, module_name, val);
}

JSModuleDef *qjs_new_bytes_module(
        JSContext *ctx,
        const char *module_name,
        const uint8_t *bytes,
        size_t len) {
    if (bytes == NULL && len > 0) {
        JS_ThrowTypeError(ctx, "bytes module buffer is NULL");
        return NULL;
    }
    JSValue val = JS_NewUint8ArrayCopy2(ctx, bytes, len, 1);
    if (JS_IsException(val)) {
        return NULL;
    }
    return qjs_create_data_module(ctx, module_name, val);
}

static char *qjs_module_normalize_trampoline(
        JSContext *ctx,
        const char *module_base_name,
        const char *module_name,
        void *opaque) {
    QjsModuleLoaderState *state = (QjsModuleLoaderState *) opaque;
    if (state == NULL || state->normalize == NULL) {
        return NULL;
    }
    return state->normalize(ctx, module_base_name, module_name, state->opaque);
}

static JSModuleDef *qjs_module_loader_trampoline(
        JSContext *ctx,
        const char *module_name,
        void *opaque,
        JSValueConst attributes) {
    QjsModuleLoaderState *state = (QjsModuleLoaderState *) opaque;
    if (state == NULL || state->loader == NULL) {
        JS_ThrowReferenceError(ctx, "could not load module '%s'", module_name);
        return NULL;
    }
    if (qjs_interrupt_check_or_throw(ctx, state->interrupt_state)) {
        return NULL;
    }
    QjsModuleImportType import_type = qjs_module_get_import_type(ctx, attributes);
    if (import_type == QJS_MODULE_IMPORT_TYPE_ERROR) {
        return NULL;
    }
    return state->loader(ctx, module_name, import_type, state->opaque);
}

void *qjs_module_loader_install(
        JSRuntime *rt,
        JSModuleNormalizeFunc *module_normalize,
        QjsModuleLoaderWithTypeFunc *module_loader,
        void *opaque,
        void *interrupt_state) {
    if (rt == NULL) {
        return NULL;
    }
    QjsModuleLoaderState *state = (QjsModuleLoaderState *) malloc(sizeof(QjsModuleLoaderState));
    if (state == NULL) {
        return NULL;
    }
    state->normalize = module_normalize;
    state->loader = module_loader;
    state->opaque = opaque;
    state->interrupt_state = interrupt_state;
    qjs_module_loader_resume(rt, state);
    return state;
}

void qjs_module_loader_resume(JSRuntime *rt, void *state) {
    if (rt == NULL) {
        return;
    }
    QjsModuleLoaderState *loader_state = (QjsModuleLoaderState *) state;
    if (loader_state == NULL) {
        qjs_module_install_attribute_checker(rt);
        return;
    }
    JS_SetModuleLoaderFunc2(
            rt,
            loader_state->normalize != NULL ? qjs_module_normalize_trampoline : NULL,
            loader_state->loader != NULL ? qjs_module_loader_trampoline : NULL,
            qjs_module_check_attributes,
            loader_state);
}

void qjs_module_loader_free(JSRuntime *rt, void *state) {
    if (rt != NULL) {
        JS_SetModuleLoaderFunc(rt, NULL, NULL, NULL);
    }
    free(state);
}

void qjs_module_install_attribute_checker(JSRuntime *rt) {
    if (rt == NULL) {
        return;
    }
    JS_SetModuleLoaderFunc2(rt, NULL, NULL, qjs_module_check_attributes, NULL);
}

void qjs_throw_type_error(JSContext *ctx, const char *message) {
    JS_ThrowTypeError(ctx, "%s", message != NULL ? message : "");
}
