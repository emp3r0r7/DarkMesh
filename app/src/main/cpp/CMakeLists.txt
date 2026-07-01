cmake_minimum_required(VERSION 3.22.1)

project(darkmesh_compression)

add_library(
    darkmesh_compression
    SHARED
    jni_bridge.cpp
)

target_link_options(
    darkmesh_compression
    PRIVATE
    "-Wl,-z,max-page-size=16384"
)

add_library(unishox2 SHARED IMPORTED)
set_target_properties(
    unishox2
    PROPERTIES
    IMPORTED_LOCATION
    ${CMAKE_SOURCE_DIR}/../jniLibs/${ANDROID_ABI}/libunishox2.so
    IMPORTED_SONAME
    libunishox2.so
)

find_library(log-lib log)

target_link_libraries(
    darkmesh_compression
    unishox2
    ${log-lib}
)
