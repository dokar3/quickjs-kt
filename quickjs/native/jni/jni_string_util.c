#include <limits.h>
#include <stdint.h>
#include <stdlib.h>
#include <string.h>
#include "jni_string_util.h"

/** Raises OutOfMemoryError after a native allocation failure if no exception is pending. */
static void throw_out_of_memory(JNIEnv *env) {
    if ((*env)->ExceptionCheck(env)) {
        return;
    }
    jclass error_class = (*env)->FindClass(env, "java/lang/OutOfMemoryError");
    if (error_class != NULL) {
        (*env)->ThrowNew(env, error_class, "Cannot allocate JNI string conversion buffer.");
        (*env)->DeleteLocalRef(env, error_class);
    }
}

/** Writes one Unicode code point as UTF-8/WTF-8, preserving surrogates as three bytes. */
static size_t write_utf8(char *output, uint32_t code_point) {
    if (code_point < 0x80) {
        output[0] = (char) code_point;
        return 1;
    }
    if (code_point < 0x800) {
        output[0] = (char) (0xC0 | (code_point >> 6));
        output[1] = (char) (0x80 | (code_point & 0x3F));
        return 2;
    }
    if (code_point < 0x10000) {
        output[0] = (char) (0xE0 | (code_point >> 12));
        output[1] = (char) (0x80 | ((code_point >> 6) & 0x3F));
        output[2] = (char) (0x80 | (code_point & 0x3F));
        return 3;
    }
    output[0] = (char) (0xF0 | (code_point >> 18));
    output[1] = (char) (0x80 | ((code_point >> 12) & 0x3F));
    output[2] = (char) (0x80 | ((code_point >> 6) & 0x3F));
    output[3] = (char) (0x80 | (code_point & 0x3F));
    return 4;
}

int jni_string_to_utf8(JNIEnv *env, jstring value, JniUtf8String *result) {
    if (result == NULL) {
        return 0;
    }
    result->data = NULL;
    result->length = 0;
    if (value == NULL) {
        return 0;
    }

    jsize utf16_length = (*env)->GetStringLength(env, value);
    const jchar *utf16 = (*env)->GetStringChars(env, value, NULL);
    if (utf16 == NULL) {
        return 0;
    }

    if ((size_t) utf16_length > (SIZE_MAX - 1) / 3) {
        (*env)->ReleaseStringChars(env, value, utf16);
        throw_out_of_memory(env);
        return 0;
    }
    size_t capacity = (size_t) utf16_length * 3 + 1;
    char *utf8 = (char *) malloc(capacity);
    if (utf8 == NULL) {
        (*env)->ReleaseStringChars(env, value, utf16);
        throw_out_of_memory(env);
        return 0;
    }

    size_t output_length = 0;
    for (jsize index = 0; index < utf16_length; index++) {
        uint32_t code_point = utf16[index];
        if (code_point >= 0xD800 && code_point <= 0xDBFF && index + 1 < utf16_length) {
            uint32_t low = utf16[index + 1];
            if (low >= 0xDC00 && low <= 0xDFFF) {
                code_point = 0x10000 + ((code_point - 0xD800) << 10) + (low - 0xDC00);
                index++;
            }
        }
        output_length += write_utf8(utf8 + output_length, code_point);
    }
    utf8[output_length] = '\0';
    (*env)->ReleaseStringChars(env, value, utf16);

    result->data = utf8;
    result->length = output_length;
    return 1;
}

void jni_utf8_string_release(JniUtf8String *value) {
    if (value == NULL) {
        return;
    }
    free(value->data);
    value->data = NULL;
    value->length = 0;
}

/** Reads one strict UTF-8/WTF-8 code point, consuming one byte as U+FFFD on failure. */
static uint32_t read_utf8(const uint8_t *input, size_t remaining, size_t *consumed) {
    uint8_t first = input[0];
    if (first < 0x80) {
        *consumed = 1;
        return first;
    }

    size_t length;
    uint32_t code_point;
    uint32_t minimum;
    if (first >= 0xC2 && first <= 0xDF) {
        length = 2;
        code_point = first & 0x1F;
        minimum = 0x80;
    } else if (first >= 0xE0 && first <= 0xEF) {
        length = 3;
        code_point = first & 0x0F;
        minimum = 0x800;
    } else if (first >= 0xF0 && first <= 0xF4) {
        length = 4;
        code_point = first & 0x07;
        minimum = 0x10000;
    } else {
        *consumed = 1;
        return 0xFFFD;
    }

    if (remaining < length) {
        *consumed = 1;
        return 0xFFFD;
    }
    for (size_t index = 1; index < length; index++) {
        uint8_t next = input[index];
        if ((next & 0xC0) != 0x80) {
            *consumed = 1;
            return 0xFFFD;
        }
        code_point = (code_point << 6) | (next & 0x3F);
    }
    if (code_point < minimum || code_point > 0x10FFFF) {
        *consumed = 1;
        return 0xFFFD;
    }

    *consumed = length;
    return code_point;
}

jstring jni_string_from_utf8(JNIEnv *env, const char *value, size_t length) {
    if (value == NULL) {
        return NULL;
    }
    if (length > INT_MAX) {
        throw_out_of_memory(env);
        return NULL;
    }
    if (length == 0) {
        const jchar empty = 0;
        return (*env)->NewString(env, &empty, 0);
    }

    if (length > SIZE_MAX / sizeof(jchar)) {
        throw_out_of_memory(env);
        return NULL;
    }
    jchar *utf16 = (jchar *) malloc(length * sizeof(jchar));
    if (utf16 == NULL) {
        throw_out_of_memory(env);
        return NULL;
    }

    size_t input_index = 0;
    jsize output_length = 0;
    while (input_index < length) {
        size_t consumed;
        uint32_t code_point = read_utf8(
                (const uint8_t *) value + input_index,
                length - input_index,
                &consumed);
        input_index += consumed;
        if (code_point <= 0xFFFF) {
            utf16[output_length++] = (jchar) code_point;
        } else {
            code_point -= 0x10000;
            utf16[output_length++] = (jchar) (0xD800 + (code_point >> 10));
            utf16[output_length++] = (jchar) (0xDC00 + (code_point & 0x3FF));
        }
    }

    jstring result = (*env)->NewString(env, utf16, output_length);
    free(utf16);
    return result;
}

jstring jni_string_from_utf8_c_string(JNIEnv *env, const char *value) {
    return value != NULL ? jni_string_from_utf8(env, value, strlen(value)) : NULL;
}
