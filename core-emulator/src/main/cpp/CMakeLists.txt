cmake_minimum_required(VERSION 3.22.1)

project("gopher64")

add_library(gopher64 SHARED
        gopher64_jni.cpp
)

find_library(log-lib log)

target_link_libraries(gopher64
        ${log-lib}
)
