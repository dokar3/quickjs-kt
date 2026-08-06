cmake_minimum_required(VERSION 3.22)

project(quickjs_native_operations C)

if (NOT DEFINED JAVA_HOME)
    message(FATAL_ERROR "JAVA_HOME is required")
endif ()
if (NOT DEFINED QUICKJS_ROOT)
    message(FATAL_ERROR "QUICKJS_ROOT is required")
endif ()
if (NOT DEFINED QUICKJS_LIBRARY)
    message(FATAL_ERROR "QUICKJS_LIBRARY is required")
endif ()
if (NOT DEFINED NATIVE_OUTPUT_DIR)
    message(FATAL_ERROR "NATIVE_OUTPUT_DIR is required")
endif ()

include_directories(
        "${JAVA_HOME}/include"
        "${JAVA_HOME}/include/darwin"
        "${JAVA_HOME}/include/linux"
        "${JAVA_HOME}/include/win32"
        "${QUICKJS_ROOT}/native/quickjs"
)

add_library(quickjs_native_operations SHARED native_operations.c)
target_link_libraries(quickjs_native_operations PRIVATE "${QUICKJS_LIBRARY}")
get_filename_component(QUICKJS_LIBRARY_DIR "${QUICKJS_LIBRARY}" DIRECTORY)

set_target_properties(quickjs_native_operations PROPERTIES
        LIBRARY_OUTPUT_DIRECTORY "${NATIVE_OUTPUT_DIR}"
        RUNTIME_OUTPUT_DIRECTORY "${NATIVE_OUTPUT_DIR}"
        ARCHIVE_OUTPUT_DIRECTORY "${NATIVE_OUTPUT_DIR}"
        BUILD_RPATH "${QUICKJS_LIBRARY_DIR}"
        INSTALL_RPATH "${QUICKJS_LIBRARY_DIR}"
)

if (WIN32)
    set_target_properties(quickjs_native_operations PROPERTIES PREFIX "")
endif ()
