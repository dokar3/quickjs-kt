#ifndef QUICKJS_NATIVE_OPERATIONS_H
#define QUICKJS_NATIVE_OPERATIONS_H

#if defined(_WIN32)
#define QUICKJS_NATIVE_OPERATIONS_API __declspec(dllexport)
#else
#define QUICKJS_NATIVE_OPERATIONS_API __attribute__((visibility("default")))
#endif

#ifdef __cplusplus
extern "C" {
#endif

QUICKJS_NATIVE_OPERATIONS_API void quickjs_native_operations_install(
        void *context,
        void *runtime);

QUICKJS_NATIVE_OPERATIONS_API int quickjs_native_operations_execute(
        void *context,
        void *runtime);

QUICKJS_NATIVE_OPERATIONS_API void quickjs_native_operations_uninstall(
        void *context,
        void *runtime);

QUICKJS_NATIVE_OPERATIONS_API int quickjs_native_operations_cleanup_count(void);

QUICKJS_NATIVE_OPERATIONS_API void quickjs_native_operations_reset_cleanup_count(void);

#ifdef __cplusplus
}
#endif

#endif
