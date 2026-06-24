cmake_minimum_required(VERSION 3.22.1)
project(llama_jni)

# Build flags for Android CPU-only inference
set(CMAKE_C_FLAGS   "${CMAKE_C_FLAGS}   -O3 -DNDEBUG")
set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -O3 -DNDEBUG -std=c++17")
# 16 KB page-size alignment required for Android 15+ devices
set(CMAKE_SHARED_LINKER_FLAGS "${CMAKE_SHARED_LINKER_FLAGS} -Wl,-z,max-page-size=16384")

set(LLAMA_DIR ${CMAKE_SOURCE_DIR}/llama.cpp)

# Disable all hardware backends — CPU only
set(GGML_METAL        OFF CACHE BOOL "" FORCE)
set(GGML_CUDA         OFF CACHE BOOL "" FORCE)
set(GGML_OPENCL       OFF CACHE BOOL "" FORCE)
set(GGML_BLAS         OFF CACHE BOOL "" FORCE)
set(GGML_KOMPUTE      OFF CACHE BOOL "" FORCE)
set(GGML_RPC          OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_SERVER OFF CACHE BOOL "" FORCE)

add_subdirectory(${LLAMA_DIR} llama_build)

# JNI bridge shared library
add_library(llama_jni SHARED llama_jni.cpp)

target_include_directories(llama_jni PRIVATE
    ${LLAMA_DIR}/include
    ${LLAMA_DIR}/ggml/include
    ${LLAMA_DIR}/common
)

target_link_libraries(llama_jni
    llama
    android
    log
)
