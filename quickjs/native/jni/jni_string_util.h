#ifndef QJS_KT_JNI_STRING_UTIL_H
#define QJS_KT_JNI_STRING_UTIL_H

#include <stddef.h>
#include "jni.h"

/**
 * A UTF-8/WTF-8 buffer produced from a JNI string.
 *
 * data is allocated with malloc and NUL-terminated for convenience. The content
 * itself may contain NUL, so callers must use length.
 */
typedef struct JniUtf8String {
    char *data;
    size_t length;
} JniUtf8String;

/**
 * Converts a Java UTF-16 string to UTF-8/WTF-8 accepted by QuickJS.
 *
 * Valid surrogate pairs are encoded as standard UTF-8. Unpaired surrogates are
 * preserved as WTF-8 to retain JavaScript UTF-16 code-unit semantics. Returns 1
 * on success and 0 on failure while preserving or raising a JNI exception.
 * Callers must release successful results with jni_utf8_string_release().
 */
int jni_string_to_utf8(JNIEnv *env, jstring value, JniUtf8String *result);

/** Releases a buffer returned by jni_string_to_utf8() and clears the struct. */
void jni_utf8_string_release(JniUtf8String *value);

/**
 * Converts length-delimited UTF-8/WTF-8 to a Java UTF-16 string.
 *
 * Supports supplementary characters, unpaired surrogates, and embedded NUL.
 * Invalid UTF-8 bytes are replaced with U+FFFD.
 */
jstring jni_string_from_utf8(JNIEnv *env, const char *value, size_t length);

/** Converts a NUL-terminated UTF-8/WTF-8 C string without embedded NUL. */
jstring jni_string_from_utf8_c_string(JNIEnv *env, const char *value);

#endif // QJS_KT_JNI_STRING_UTIL_H
