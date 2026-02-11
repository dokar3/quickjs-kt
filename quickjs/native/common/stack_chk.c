#include <stdlib.h>
#include <stdint.h>

#if defined(_WIN32)

// Generate a pseudo-random stack canary at compile time.
// Not cryptographically strong, but varies per build which is better
// than a fully static value. Runtime randomization via constructor is
// not viable because it runs before the Windows runtime is initialized
// in the Kotlin/Native static library context.
#define STACK_CHK_SEED ((uint64_t)(__LINE__) * 7 + __COUNTER__ * 13)
#define STACK_CHK_HASH(s) ((s) ^ ((s) >> 16) ^ ((s) << 32))
uintptr_t __stack_chk_guard = STACK_CHK_HASH(STACK_CHK_SEED + \
    (uint64_t)(__DATE__[0]) * 31 + \
    (uint64_t)(__DATE__[2]) * 37 + \
    (uint64_t)(__TIME__[0]) * 41 + \
    (uint64_t)(__TIME__[1]) * 43);

void __stack_chk_fail(void) {
    abort();
}

#endif
