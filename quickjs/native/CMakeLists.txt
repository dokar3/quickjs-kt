cmake_minimum_required(VERSION 3.22)

string(REPLACE \\ / CURRENT_DIR ${CMAKE_CURRENT_LIST_DIR})

# Check cmake arguments
if (NOT DEFINED TARGET_PLATFORM)
    message(FATAL_ERROR "'TARGET_PLATFORM' is no set.")
endif ()
# Library type
if (NOT DEFINED LIBRARY_TYPE)
    message(FATAL_ERROR "'LIBRARY_TYPE' is not set.")
endif ()
if (NOT (LIBRARY_TYPE STREQUAL "shared" OR LIBRARY_TYPE STREQUAL "static"))
    message(FATAL_ERROR "'LIBRARY_TYPE' can only be 'shared' or 'static'.")
endif ()
# JNI
option(BUILD_WITH_JNI "Build with the JNI bridge" ON)

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
    if (BUILD_WITH_JNI)
        configure_jni("linux")
    endif ()
elseif (TARGET_PLATFORM STREQUAL "linux_aarch64")
    set(CMAKE_TOOLCHAIN_FILE "${CURRENT_DIR}/cmake/zig-toolchain-linux_aarch64.cmake")
    if (BUILD_WITH_JNI)
        configure_jni("linux")
    endif ()
elseif (TARGET_PLATFORM STREQUAL "macos_x64")
    set(CMAKE_TOOLCHAIN_FILE "${CURRENT_DIR}/cmake/zig-toolchain-macos_x64.cmake")
    if (BUILD_WITH_JNI)
        configure_jni("darwin")
    endif ()
elseif (TARGET_PLATFORM STREQUAL "macos_aarch64")
    set(CMAKE_TOOLCHAIN_FILE "${CURRENT_DIR}/cmake/zig-toolchain-macos_aarch64.cmake")
    if (BUILD_WITH_JNI)
        configure_jni("darwin")
    endif ()
elseif (TARGET_PLATFORM STREQUAL "ios_x64")
    set(CMAKE_SYSTEM_NAME "iOS")
    set(CMAKE_OSX_ARCHITECTURES "x86_64")
elseif (TARGET_PLATFORM STREQUAL "ios_aarch64")
    set(CMAKE_SYSTEM_NAME "iOS")
    set(CMAKE_OSX_ARCHITECTURES "arm64")
elseif (TARGET_PLATFORM STREQUAL "ios_aarch64_simulator")
    set(CMAKE_SYSTEM_NAME "iOS")
    set(CMAKE_OSX_ARCHITECTURES "arm64")
elseif (TARGET_PLATFORM STREQUAL "windows_x64")
    if (BUILD_WITH_JNI)
        configure_jni("win32")
    endif ()
    set(CMAKE_TOOLCHAIN_FILE "${CURRENT_DIR}/cmake/zig-toolchain-windows_x64.cmake")
    include_directories("winpthreads/include")
    if (BUILD_WITH_JNI)
        # pthread for Windows
        file(GLOB_RECURSE pthread_sources
                "winpthreads/src/*.c"
                "winpthreads/src/*.S"
        )
        list(APPEND all_sources ${pthread_sources})
    endif ()
    # For QuickJS
    add_compile_definitions(CONFIG_WIN32)
elseif (TARGET_PLATFORM STREQUAL "android")
    add_compile_definitions(CONFIG_ANDROID)
endif ()

project(quickjs)

set(CMAKE_C_STANDARD 99)
set(CMAKE_CXX_STANDARD 11)

# QuickJS version define, Gradle cmake cFlags doesn't work on Windows (maybe)
file(READ "quickjs/VERSION" VERSION_CONTENT)
string(STRIP "${VERSION_CONTENT}" CONFIG_VERSION)
add_compile_definitions(CONFIG_VERSION=\"${CONFIG_VERSION}\")

# QuickJS BigNum
add_compile_definitions(CONFIG_BIGNUM)

include_directories("quickjs" "common")

if (BUILD_WITH_JNI)
    include_directories(
            "cvector"
            "jni"
            "jni/mapping")
endif ()

# QuickJS sources
file(GLOB_RECURSE quickjs_sources
        "quickjs/cutils.c"
        "quickjs/libbf.c"
        "quickjs/libregexp.c"
        "quickjs/libunicode.c"
        "quickjs/quickjs.c"
        "common/quickjs_version.c"
)
list(APPEND all_sources ${quickjs_sources})

if (BUILD_WITH_JNI)
    # Bridge sources
    file(GLOB_RECURSE bridge_sources
            "jni/*.c"
            "jni/mapping/*.c")
    list(APPEND all_sources ${bridge_sources})
endif ()

if (LIBRARY_TYPE STREQUAL "shared")
    add_library(quickjs SHARED ${all_sources})
    if (TARGET_PLATFORM STREQUAL "android")
        target_link_libraries(quickjs log)
    endif ()
elseif (LIBRARY_TYPE STREQUAL "static")
    add_library(quickjs STATIC ${all_sources})
endif ()
