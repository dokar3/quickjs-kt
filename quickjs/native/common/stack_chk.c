#include <stdlib.h>
#include <stdint.h>
#include <windows.h>

#if defined(_WIN32)

// Default stack guard value
uintptr_t __stack_chk_guard = 0x595e9fbd94fda766;

void __stack_chk_fail(void) {
    abort();
}

typedef BOOLEAN (WINAPI *RtlGenRandomFunc)(PVOID, ULONG);

// Initialize the stack guard with a random value
__attribute__((constructor))
static void __stack_chk_init(void) {
    HMODULE hAdvApi32 = LoadLibraryA("advapi32.dll");
    if (hAdvApi32) {
        RtlGenRandomFunc RtlGenRandom = (RtlGenRandomFunc)GetProcAddress(hAdvApi32, "SystemFunction036");
        if (RtlGenRandom) {
            uintptr_t random_guard;
            if (RtlGenRandom(&random_guard, sizeof(random_guard))) {
                __stack_chk_guard = random_guard;
            }
        }
        FreeLibrary(hAdvApi32);
    }
}
#endif
