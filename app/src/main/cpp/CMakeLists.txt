# The Quran speech recogniser's engine: whisper.cpp (ggml-org, MIT),
# fetched at build time from its v1.9.4 release and checked by SHA-256,
# and a small JNI bridge (stt.cpp).
cmake_minimum_required(VERSION 3.22.1)
project(mushafstt C CXX)

include(FetchContent)
FetchContent_Declare(
    whisper
    URL https://github.com/ggml-org/whisper.cpp/archive/refs/tags/v1.9.4.tar.gz
    URL_HASH SHA256=57e280cee375ab02425b806ad5146b99f6eb9357e3c2b31357c8a6af2e2e44ae
)
set(BUILD_SHARED_LIBS OFF CACHE BOOL "" FORCE)
set(WHISPER_BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(WHISPER_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(WHISPER_BUILD_SERVER OFF CACHE BOOL "" FORCE)
set(GGML_NATIVE OFF CACHE BOOL "" FORCE)
set(GGML_OPENMP OFF CACHE BOOL "" FORCE)
FetchContent_MakeAvailable(whisper)

add_library(mushafstt SHARED stt.cpp)
target_link_libraries(mushafstt PRIVATE whisper log)
# Only the bridge's functions are visible outside the library.
target_compile_options(mushafstt PRIVATE -fvisibility=hidden)
target_link_options(mushafstt PRIVATE -Wl,--exclude-libs,ALL -Wl,-z,max-page-size=16384)
