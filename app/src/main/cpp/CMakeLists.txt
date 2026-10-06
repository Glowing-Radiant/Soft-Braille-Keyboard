cmake_minimum_required(VERSION 3.22.1)

project(braillekeyboard C CXX)

# liblouis 3.39.0, vendored unmodified from
# https://github.com/liblouis/liblouis/releases/tag/v3.39.0 (LGPL-2.1+).
# liblouis.h is generated from liblouis.h.in with 16-bit widechars so that
# they map directly onto Java chars (UTF-16).
set(LIBLOUIS_DIR ${CMAKE_CURRENT_SOURCE_DIR}/liblouis)

add_library(louis STATIC
        ${LIBLOUIS_DIR}/commonTranslationFunctions.c
        ${LIBLOUIS_DIR}/compileTranslationTable.c
        ${LIBLOUIS_DIR}/logging.c
        ${LIBLOUIS_DIR}/lou_backTranslateString.c
        ${LIBLOUIS_DIR}/lou_translateString.c
        ${LIBLOUIS_DIR}/maketable.c
        ${LIBLOUIS_DIR}/metadata.c
        ${LIBLOUIS_DIR}/pattern.c
        ${LIBLOUIS_DIR}/utils.c)

target_include_directories(louis PUBLIC ${LIBLOUIS_DIR})

# Tables are extracted at runtime and located via lou_setDataPath(), so the
# compiled-in fallback directory is never expected to exist.
target_compile_definitions(louis PRIVATE
        TABLESDIR="/system/usr/share/liblouis/tables")

target_compile_options(louis PRIVATE -w -fvisibility=hidden)

add_library(louiswrap SHARED louis_jni.c)

target_link_libraries(louiswrap louis log)

# 16 KB page size support (required for 64-bit apps on Android 15+ devices).
target_link_options(louiswrap PRIVATE "-Wl,-z,max-page-size=16384")

# Hunspell 1.7.2, vendored unmodified from
# https://github.com/hunspell/hunspell/releases/tag/v1.7.2 (MPL-1.1, GPL-2+
# or LGPL-2.1+), for checking spelling with bundled and imported
# dictionaries.
set(HUNSPELL_DIR ${CMAKE_CURRENT_SOURCE_DIR}/hunspell)

add_library(hunspell STATIC
        ${HUNSPELL_DIR}/affentry.cxx
        ${HUNSPELL_DIR}/affixmgr.cxx
        ${HUNSPELL_DIR}/csutil.cxx
        ${HUNSPELL_DIR}/filemgr.cxx
        ${HUNSPELL_DIR}/hashmgr.cxx
        ${HUNSPELL_DIR}/hunspell.cxx
        ${HUNSPELL_DIR}/hunzip.cxx
        ${HUNSPELL_DIR}/phonet.cxx
        ${HUNSPELL_DIR}/replist.cxx
        ${HUNSPELL_DIR}/suggestmgr.cxx)

target_include_directories(hunspell PUBLIC ${HUNSPELL_DIR})

# Built into this library rather than exported from a shared one.
target_compile_definitions(hunspell PUBLIC HUNSPELL_STATIC)

target_compile_options(hunspell PRIVATE -w -fvisibility=hidden)

add_library(hunspellwrap SHARED hunspell_jni.cpp)

target_link_libraries(hunspellwrap hunspell)

target_link_options(hunspellwrap PRIVATE "-Wl,-z,max-page-size=16384")
