#include <stdlib.h>

#ifdef _WIN32
#include <windows.h>
#else
#include <time.h>
#endif

#include "quickjs.h"
#include "quickjs_interrupt.h"

typedef struct QjsInterruptState {
    volatile int requested;
    volatile int fired;
    // Monotonic millis, 0 = no deadline
    volatile int64_t deadline_ms;
} QjsInterruptState;

static int64_t qjs_now_ms(void) {
#ifdef _WIN32
    return (int64_t) GetTickCount64();
#else
    struct timespec ts;
    clock_gettime(CLOCK_MONOTONIC, &ts);
    return (int64_t) ts.tv_sec * 1000 + ts.tv_nsec / 1000000;
#endif
}

static int qjs_interrupt_handler(JSRuntime *rt, void *opaque) {
    QjsInterruptState *state = (QjsInterruptState *) opaque;
    if (state == NULL) {
        return 0;
    }
    if (state->requested) {
        state->fired = 1;
        return 1;
    }
    int64_t deadline = state->deadline_ms;
    if (deadline > 0 && qjs_now_ms() >= deadline) {
        state->fired = 1;
        return 1;
    }
    return 0;
}

void *qjs_interrupt_install(JSRuntime *rt) {
    QjsInterruptState *state = calloc(1, sizeof(QjsInterruptState));
    if (state == NULL) {
        return NULL;
    }
    JS_SetInterruptHandler(rt, qjs_interrupt_handler, state);
    return state;
}

void qjs_interrupt_free(JSRuntime *rt, void *state) {
    JS_SetInterruptHandler(rt, NULL, NULL);
    free(state);
}

void qjs_interrupt_request(void *state) {
    if (state != NULL) {
        ((QjsInterruptState *) state)->requested = 1;
    }
}

void qjs_interrupt_reset(void *state, int64_t timeout_ms) {
    QjsInterruptState *s = state;
    if (s != NULL) {
        s->requested = 0;
        s->fired = 0;
        s->deadline_ms = timeout_ms > 0 ? qjs_now_ms() + timeout_ms : 0;
    }
}

int qjs_interrupt_fired(void *state) {
    return state != NULL && ((QjsInterruptState *) state)->fired;
}
