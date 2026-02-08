#include <stdlib.h>
#include <stdint.h>

#if defined(_WIN32)
void __stack_chk_fail(void) {
    abort();
}

uintptr_t __stack_chk_guard = 0x595e9fbd94fda766;
#endif
