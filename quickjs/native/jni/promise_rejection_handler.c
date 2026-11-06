#include <jni.h>
#include "promise_rejection_handler.h"
#include "exception_util.h"
#include "jni_globals.h"
#include "jni_globals_generated.h"
#include "js_value_to_jobject.h"
#include "quickjs_interrupt.h"
#include "quickjs_jni.h"

void promise_rejection_handler(JSContext *ctx, JSValue promise,
                               JSValue reason,
                               int is_handled, void *opaque) {
    Globals *globals = (Globals *) opaque;
    // An interrupt takes precedence over any rejection, don't run more JS for it.
    if (!is_handled && qjs_interrupt_poll(globals->interrupt_state)) {
        return;
    }
    JNIEnv *env = get_jni_env();
    if (env == NULL) {
        return;
    }
    jobject host = globals->global_object_refs[0];
    jlong promise_id = (jlong) (uintptr_t) JS_VALUE_GET_PTR(promise);
    if (!is_handled) {
        jobject java_reason = js_value_to_jobject(env, ctx, reason);
        jthrowable conversion_error = try_catch_java_exceptions(env);
        if (conversion_error != NULL) {
            // The tracker can't propagate errors: report the conversion failure as
            // the rejection reason and drop any JS exception it left behind.
            JS_FreeValue(ctx, JS_GetException(ctx));
            if (java_reason != NULL) {
                (*env)->DeleteLocalRef(env, java_reason);
            }
            java_reason = conversion_error;
        }
        if (!qjs_interrupt_poll(globals->interrupt_state)) {
            (*env)->CallVoidMethod(env, host,
                                   method_quick_js_set_unhandled_promise_rejection(env),
                                   promise_id,
                                   java_reason);
        }
        if (java_reason != NULL) {
            (*env)->DeleteLocalRef(env, java_reason);
        }
    } else {
        (*env)->CallVoidMethod(env, host,
                               method_quick_js_clear_handled_promise_rejection(env),
                               promise_id);
    }
}
