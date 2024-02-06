cmake_minimum_required(VERSION 3.22)

string(REPLACE \\ / CURRENT_DIR ${CMAKE_CURRENT_LIST_DIR})

# Check cmake arguments
if (NOT DEFINED TARGET_PLATFORM)
    message(FATAL_ERROR "'TARGET_PLATFORM' is no set!")
endif ()

function(configure_jni include_sub_dir)
    if (NOT DEFINED PLATFORM_JAVA_HOME)
        message(FATAL_ERROR "'PLATFORM_JAVA_HOME' is no set!")
    endif ()
    string(REPLACE \\ / PLATFORM_JAVA_HOME ${PLATFORM_JAVA_HOME})
    set(JAVA_HOME ${PLATFORM_JAVA_HOME})
    set(ENV{JAVA_HOME} "${JAVA_HOME}")
    include_directories(${JAVA_HOME}/include ${JAVA_HOME}/include/${include_sub_dir})
    set(JAVA_AWT_LIBRARY NotNeeded)
    set(JAVA_JVM_LIBRARY NotNeeded)
    find_package(JNI REQUIRED)
endfunction()

# All sources
set(all_sources "")

# Cross compiling
if (TARGET_PLATFORM STREQUAL "linux_x64")
    set(CMAKE_TOOLCHAIN_FILE "${CURRENT_DIR}/cmake/zig-toolchain-linux_x64.cmake")
    configure_jni("linux")
elseif (TARGET_PLATFORM STREQUAL "linux_aarch64")
    set(CMAKE_TOOLCHAIN_FILE "${CURRENT_DIR}/cmake/zig-toolchain-linux_aarch64.cmake")
    configure_jni("linux")
elseif (TARGET_PLATFORM STREQUAL "macos_x64")
    set(CMAKE_TOOLCHAIN_FILE "${CURRENT_DIR}/cmake/zig-toolchain-macos_x64.cmake")
    configure_jni("darwin")
elseif (TARGET_PLATFORM STREQUAL "macos_aarch64")
    set(CMAKE_TOOLCHAIN_FILE "${CURRENT_DIR}/cmake/zig-toolchain-macos_aarch64.cmake")
    configure_jni("darwin")
elseif (TARGET_PLATFORM STREQUAL "windows_x64")
    configure_jni("win32")
    set(CMAKE_TOOLCHAIN_FILE "${CURRENT_DIR}/cmake/zig-toolchain-windows_x64.cmake")
    # pthread for Windows
    include_directories("../../native/winpthreads/include")
    file(GLOB_RECURSE pthread_sources
            "../../native/winpthreads/src/*.c"
            "../../native/winpthreads/src/*.S"
    )
    list(APPEND all_sources ${pthread_sources})
    # For QuickJS
    add_compile_definitions(CONFIG_WIN32)
elseif (TARGET_PLATFORM STREQUAL "android")
    add_compile_definitions(CONFIG_ANDROID)
endif ()

project(quickjs)

set(CMAKE_C_STANDARD 99)
set(CMAKE_CXX_STANDARD 11)

# QuickJS version define, Gradle cmake cFlags doesn't work on Windows (maybe)
file(READ "../../native/quickjs/VERSION" VERSION_CONTENT)
string(STRIP "${VERSION_CONTENT}" CONFIG_VERSION)
add_compile_definitions(CONFIG_VERSION=\"${CONFIG_VERSION}\")

# QuickJS BigNum
add_compile_definitions(CONFIG_BIGNUM)

include_directories("../../native"
        "../../native/quickjs"
        "../../native/cvector"
        "../../native/jni"
        "../../native/jni/mapping")

# QuickJS sources
file(GLOB_RECURSE quickjs_sources
        "../../native/quickjs/cutils.c"
        "../../native/quickjs/libbf.c"
        "../../native/quickjs/libregexp.c"
        "../../native/quickjs/libunicode.c"
        "../../native/quickjs/quickjs.c"
)
list(APPEND all_sources ${quickjs_sources})

# Bridge sources
file(GLOB_RECURSE bridge_sources
        "../../native/jni/*.c"
        "../../native/jni/mapping/*.c")
list(APPEND all_sources ${bridge_sources})

add_library(quickjs SHARED ${all_sources})

if (TARGET_PLATFORM STREQUAL "android")
    target_link_libraries(quickjs log)
else ()
    target_link_libraries(quickjs)
endif ()
