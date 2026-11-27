#ifndef QJS_KT_QUICKJS_MODULE_IMPORT_H
#define QJS_KT_QUICKJS_MODULE_IMPORT_H

#include <stddef.h>
#include <stdint.h>
#include "../quickjs/quickjs.h"

/**
 * Supported ES module import types mapped from QuickJS import attributes.
 */
typedef enum QjsModuleImportType {
    QJS_MODULE_IMPORT_TYPE_ERROR = -1,
    QJS_MODULE_IMPORT_TYPE_JS = 0,
    QJS_MODULE_IMPORT_TYPE_TEXT = 1,
    QJS_MODULE_IMPORT_TYPE_JSON = 2,
    QJS_MODULE_IMPORT_TYPE_BYTES = 3,
} QjsModuleImportType;

/**
 * Validates that import attributes only contain supported keys ("type").
 *
 * Conforms to ECMA-262 HostGetSupportedImportAttributes: only keys are checked
 * here; attribute values are validated when loading the module.
 */
int qjs_module_check_attributes(JSContext *ctx, void *opaque, JSValueConst attributes);

/**
 * Resolves the QjsModuleImportType from QuickJS import attributes.
 *
 * Returns QJS_MODULE_IMPORT_TYPE_JS when attributes are absent or have no
 * "type" property, or QJS_MODULE_IMPORT_TYPE_ERROR after throwing a TypeError
 * for an unsupported type value.
 */
QjsModuleImportType qjs_module_get_import_type(JSContext *ctx, JSValueConst attributes);

/**
 * Creates a data module exporting a default JS string from UTF-8/WTF-8 bytes.
 */
JSModuleDef *qjs_new_text_module(
        JSContext *ctx,
        const char *module_name,
        const char *utf8,
        size_t utf8_len);

/**
 * Creates a data module exporting a default parsed JSON value.
 *
 * utf8_nul_terminated must be NUL-terminated at utf8_nul_terminated[utf8_len].
 */
JSModuleDef *qjs_new_json_module(
        JSContext *ctx,
        const char *module_name,
        const char *utf8_nul_terminated,
        size_t utf8_len);

/**
 * Creates a data module exporting a default immutable Uint8Array copy of bytes.
 */
JSModuleDef *qjs_new_bytes_module(
        JSContext *ctx,
        const char *module_name,
        const uint8_t *bytes,
        size_t len);

/**
 * Module loader callback receiving the parsed QjsModuleImportType instead of
 * a raw JSValueConst attributes parameter (safe across all cinterop ABIs).
 */
typedef JSModuleDef *QjsModuleLoaderWithTypeFunc(
        JSContext *ctx,
        const char *module_name,
        QjsModuleImportType import_type,
        void *opaque);

/**
 * Installs a module loader with import attribute checking and returns an
 * owned state handle that must be freed with qjs_module_loader_free().
 */
void *qjs_module_loader_install(
        JSRuntime *rt,
        JSModuleNormalizeFunc *module_normalize,
        QjsModuleLoaderWithTypeFunc *module_loader,
        void *opaque,
        void *interrupt_state);

/**
 * Re-installs a previously created module loader state (e.g. after clearing
 * pending jobs on interrupt). Falls back to attribute checking only when state
 * is NULL.
 */
void qjs_module_loader_resume(JSRuntime *rt, void *state);

/**
 * Clears the runtime's module loader hooks and frees state.
 */
void qjs_module_loader_free(JSRuntime *rt, void *state);

/**
 * Installs import attribute key checking on a runtime without a module loader.
 */
void qjs_module_install_attribute_checker(JSRuntime *rt);

/**
 * Throws a JavaScript TypeError with a plain message string (non-variadic for cinterop).
 */
void qjs_throw_type_error(JSContext *ctx, const char *message);

#endif // QJS_KT_QUICKJS_MODULE_IMPORT_H
