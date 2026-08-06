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

if (DEFINED QUICKJS_HEADER_DIR)
    include_directories("${QUICKJS_HEADER_DIR}")
else()
    include_directories("${QUICKJS_ROOT}/native/quickjs")
endif()

include_directories(
        "${JAVA_HOME}/include"
        "${JAVA_HOME}/include/darwin"
        "${JAVA_HOME}/include/linux"
        "${JAVA_HOME}/include/win32"
)

add_library(quickjs_native_operations SHARED native_operations.c)

set(QUICKJS_LINK_LIBRARY "${QUICKJS_LIBRARY}")
if (WIN32 AND MSVC)
    if ("${QUICKJS_LIBRARY}" MATCHES "\\.[dD][lL][lL]$")
        set(IMPLIB_DEF "${CMAKE_CURRENT_BINARY_DIR}/libquickjs.def")
        set(IMPLIB_LIB "${CMAKE_CURRENT_BINARY_DIR}/libquickjs.lib")

        find_program(DUMPBIN_EXECUTABLE dumpbin)
        find_program(LIB_EXECUTABLE lib)

        if (DUMPBIN_EXECUTABLE AND LIB_EXECUTABLE)
            execute_process(
                COMMAND "${DUMPBIN_EXECUTABLE}" /EXPORTS "${QUICKJS_LIBRARY}"
                OUTPUT_VARIABLE DUMPBIN_OUT
                RESULT_VARIABLE DUMPBIN_RES
            )
            if (DUMPBIN_RES EQUAL 0)
                file(WRITE "${IMPLIB_DEF}" "EXPORTS\n")
                string(REPLACE "\r\n" "\n" DUMPBIN_OUT "${DUMPBIN_OUT}")
                string(REPLACE "\n" ";" DUMPBIN_LINES "${DUMPBIN_OUT}")
                foreach(line ${DUMPBIN_LINES})
                    if (line MATCHES "^ +[0-9]+ +[0-9A-Fa-f]+ +[0-9A-Fa-f]+ +[A-Za-z0-9_]+")
                        string(REGEX REPLACE "^ +([0-9]+) +[0-9A-Fa-f]+ +[0-9A-Fa-f]+ +([A-Za-z0-9_]+).*" "\\1" ORD "${line}")
                        string(REGEX REPLACE "^ +([0-9]+) +[0-9A-Fa-f]+ +[0-9A-Fa-f]+ +([A-Za-z0-9_]+).*" "\\2" FUNC_NAME "${line}")
                        file(APPEND "${IMPLIB_DEF}" "    ${FUNC_NAME} @${ORD}\n")
                    endif()
                endforeach()

                if (CMAKE_SIZEOF_VOID_P EQUAL 8)
                    set(LIB_MACHINE_FLAG "/machine:x64")
                else()
                    set(LIB_MACHINE_FLAG "/machine:x86")
                endif()

                execute_process(
                    COMMAND "${LIB_EXECUTABLE}" "/def:${IMPLIB_DEF}" "/out:${IMPLIB_LIB}" "${LIB_MACHINE_FLAG}"
                    RESULT_VARIABLE LIB_RES
                )
                if (LIB_RES EQUAL 0)
                    set(QUICKJS_LINK_LIBRARY "${IMPLIB_LIB}")
                endif()
            endif()
        endif()
    endif()
endif()

target_link_libraries(quickjs_native_operations PRIVATE "${QUICKJS_LINK_LIBRARY}")
get_filename_component(QUICKJS_LIBRARY_DIR "${QUICKJS_LIBRARY}" DIRECTORY)

set_target_properties(quickjs_native_operations PROPERTIES
        LIBRARY_OUTPUT_DIRECTORY "${NATIVE_OUTPUT_DIR}"
        RUNTIME_OUTPUT_DIRECTORY "${NATIVE_OUTPUT_DIR}"
        ARCHIVE_OUTPUT_DIRECTORY "${NATIVE_OUTPUT_DIR}"
        LIBRARY_OUTPUT_DIRECTORY_DEBUG "${NATIVE_OUTPUT_DIR}"
        RUNTIME_OUTPUT_DIRECTORY_DEBUG "${NATIVE_OUTPUT_DIR}"
        ARCHIVE_OUTPUT_DIRECTORY_DEBUG "${NATIVE_OUTPUT_DIR}"
        LIBRARY_OUTPUT_DIRECTORY_RELEASE "${NATIVE_OUTPUT_DIR}"
        RUNTIME_OUTPUT_DIRECTORY_RELEASE "${NATIVE_OUTPUT_DIR}"
        ARCHIVE_OUTPUT_DIRECTORY_RELEASE "${NATIVE_OUTPUT_DIR}"
        BUILD_RPATH "${QUICKJS_LIBRARY_DIR}"
        INSTALL_RPATH "${QUICKJS_LIBRARY_DIR}"
)

if (WIN32)
    set_target_properties(quickjs_native_operations PROPERTIES PREFIX "")
endif ()
