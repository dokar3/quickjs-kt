#include <stdlib.h>
#include <stdint.h>

#if defined(_WIN32)

// Stack guard value for stack-protector support on Windows/MinGW.
// A static value is used instead of runtime randomization because this code
// is compiled into a static library linked by Kotlin/Native. Using
// __attribute__((constructor)) to call LoadLibraryA/GetProcAddress for
// RtlGenRandom caused crashes (exit code 3) since the constructor runs
// before the Windows runtime is fully initialized in that context.
uintptr_t __stack_chk_guard = 0x595e9fbd94fda766;

void __stack_chk_fail(void) {
    abort();
}

#endif
