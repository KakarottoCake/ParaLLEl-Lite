cmake_minimum_required(VERSION 3.22.1)

project("flips_patcher")

add_library(
    flips_patcher
    SHARED
    jni_bps.cpp
    libbps.cpp
    crc32.cpp
)

find_library(
    log-lib
    log
)

target_link_libraries(
    flips_patcher
    ${log-lib}
)
