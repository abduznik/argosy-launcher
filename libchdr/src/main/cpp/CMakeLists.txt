cmake_minimum_required(VERSION 3.22.1)
project(chdr-jni C)

# libchdr deps
set(LIBCHDR_DIR ${CMAKE_CURRENT_SOURCE_DIR}/libchdr)

# zlib (bundled, core only -- no gzip file I/O)
set(ZLIB_DIR ${LIBCHDR_DIR}/deps/zlib-1.3.1)
set(ZLIB_SOURCES
    ${ZLIB_DIR}/adler32.c
    ${ZLIB_DIR}/compress.c
    ${ZLIB_DIR}/crc32.c
    ${ZLIB_DIR}/deflate.c
    ${ZLIB_DIR}/infback.c
    ${ZLIB_DIR}/inffast.c
    ${ZLIB_DIR}/inflate.c
    ${ZLIB_DIR}/inftrees.c
    ${ZLIB_DIR}/trees.c
    ${ZLIB_DIR}/uncompr.c
    ${ZLIB_DIR}/zutil.c
)
add_library(zlib_static STATIC ${ZLIB_SOURCES})
target_include_directories(zlib_static PUBLIC ${ZLIB_DIR})

# lzma (bundled)
set(LZMA_DIR ${LIBCHDR_DIR}/deps/lzma-24.05)
set(LZMA_SOURCES
    ${LZMA_DIR}/src/Alloc.c
    ${LZMA_DIR}/src/Bra86.c
    ${LZMA_DIR}/src/BraIA64.c
    ${LZMA_DIR}/src/CpuArch.c
    ${LZMA_DIR}/src/Delta.c
    ${LZMA_DIR}/src/LzFind.c
    ${LZMA_DIR}/src/Lzma86Dec.c
    ${LZMA_DIR}/src/LzmaDec.c
    ${LZMA_DIR}/src/Sort.c
)
add_library(lzma_static STATIC ${LZMA_SOURCES})
target_include_directories(lzma_static PUBLIC ${LZMA_DIR}/include)
target_compile_definitions(lzma_static PRIVATE _7ZIP_ST)

# zstd (bundled)
set(ZSTD_DIR ${LIBCHDR_DIR}/deps/zstd-1.5.6/lib)
file(GLOB ZSTD_COMMON_SOURCES ${ZSTD_DIR}/common/*.c)
file(GLOB ZSTD_DECOMPRESS_SOURCES ${ZSTD_DIR}/decompress/*.c)
add_library(zstd_static STATIC ${ZSTD_COMMON_SOURCES} ${ZSTD_DECOMPRESS_SOURCES})
target_include_directories(zstd_static PUBLIC ${ZSTD_DIR} ${ZSTD_DIR}/common)

# libchdr
set(LIBCHDR_SOURCES
    ${LIBCHDR_DIR}/src/libchdr_bitstream.c
    ${LIBCHDR_DIR}/src/libchdr_cdrom.c
    ${LIBCHDR_DIR}/src/libchdr_chd.c
    ${LIBCHDR_DIR}/src/libchdr_flac.c
    ${LIBCHDR_DIR}/src/libchdr_huffman.c
)
add_library(chdr_static STATIC ${LIBCHDR_SOURCES})
target_include_directories(chdr_static PUBLIC ${LIBCHDR_DIR}/include)
target_link_libraries(chdr_static PRIVATE zlib_static lzma_static zstd_static)
target_compile_definitions(chdr_static PRIVATE
    FLAC__NO_DLL
    FLAC__HAS_OGG=0
)

# JNI bridge
add_library(chdr-jni SHARED chdr_jni.c)
target_link_libraries(chdr-jni PRIVATE chdr_static zlib_static lzma_static zstd_static log)
target_include_directories(chdr-jni PRIVATE ${LIBCHDR_DIR}/include)
