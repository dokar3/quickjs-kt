#include <stdlib.h>
#include <stdint.h>

#if defined(_WIN32)

// Stack guard value for stack-protector support on Windows/MinGW.
// A static value is used instead of runtime randomization because this code
// is compiled into a static library linked by Kotlin/Native.
uintptr_t __stack_chk_guard = 0x595e9fbd94fda766;

void __stack_chk_fail(void) {
    // Do nothing/infinite loop to avoid abort() exit code 3 if called.
    // Ideally should print something but we might not have stdout.
    // abort(); 
}

#endif
