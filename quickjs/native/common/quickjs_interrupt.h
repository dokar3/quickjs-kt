#ifndef QJS_KT_QUICKJS_INTERRUPT_H
#define QJS_KT_QUICKJS_INTERRUPT_H

#include <stdint.h>

/*
 * Evaluation interrupt support on top of JS_SetInterruptHandler().
 * Requires "quickjs.h" to be included first.
 *
 * The handler only reads a few volatile fields, so it's cheap to call on
 * every check and safe to flag from other threads without locks.
 */

/* Install the handler after JS_NewRuntime(). Returns the state handle, NULL on OOM. */
void *qjs_interrupt_install(JSRuntime *rt);

/* Unregister the handler and free the state. Call before JS_FreeRuntime(). */
void qjs_interrupt_free(JSRuntime *rt, void *state);

/* Abort the running evaluation at its next interrupt check. */
void qjs_interrupt_request(void *state);

/* Clear the flags and set the deadline to now + timeout_ms (<= 0 for none). */
void qjs_interrupt_reset(void *state, int64_t timeout_ms);

/* 1 if the handler aborted an evaluation since the last reset. */
int qjs_interrupt_fired(void *state);

#endif // QJS_KT_QUICKJS_INTERRUPT_H
