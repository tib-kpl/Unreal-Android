cmake_minimum_required(VERSION 3.22.1)
project(Unreal226Android LANGUAGES C CXX)

# Second native engine of the app: the Android-ported UE1 v400 engine, used for
# Unreal Gold 226 data. The v200 engine (app/src/main/cpp) is built separately
# because both engines declare the same CMake targets (Core, Engine, ...).

if(NOT ANDROID_ABI STREQUAL "armeabi-v7a" AND NOT ANDROID_ABI STREQUAL "arm64-v8a")
    message(FATAL_ERROR "Unreal226 supports armeabi-v7a and arm64-v8a, not ${ANDROID_ABI}.")
endif()
if(ANDROID_ABI STREQUAL "armeabi-v7a")
    add_compile_options(-marm)
endif()

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_CXX_EXTENSIONS ON)
set(CMAKE_POSITION_INDEPENDENT_CODE ON)
add_link_options("-Wl,-z,max-page-size=16384" "-Wl,-z,common-page-size=16384")

set(ENGINE_SOURCE_DIR "${CMAKE_CURRENT_LIST_DIR}/Source")
# SDL2 is prepared by the app module (prepareSources) and built here again;
# Gradle keeps one copy of libSDL2.so.
set(SDL2_ROOT "${CMAKE_CURRENT_LIST_DIR}/../../../../app/src/main/cpp/thirdparty/SDL2")
if(NOT EXISTS "${SDL2_ROOT}/CMakeLists.txt")
    message(FATAL_ERROR "SDL2 is missing at ${SDL2_ROOT}. Run the app prepareSources task first.")
endif()

set(UT99_LIBXMP_SO "${CMAKE_CURRENT_LIST_DIR}/../jniLibs/${ANDROID_ABI}/libxmp.so")
add_library(ut99_libxmp SHARED IMPORTED GLOBAL)
set_target_properties(ut99_libxmp PROPERTIES
    IMPORTED_LOCATION "${UT99_LIBXMP_SO}"
    INTERFACE_INCLUDE_DIRECTORIES "${CMAKE_CURRENT_LIST_DIR}/android/include")
set(LIBXMP_INCLUDE_DIR "${CMAKE_CURRENT_LIST_DIR}/android/include" CACHE PATH "" FORCE)
set(LIBXMP_LIBRARY ut99_libxmp CACHE STRING "" FORCE)

set(SDL_SHARED ON CACHE BOOL "" FORCE)
set(SDL_STATIC OFF CACHE BOOL "" FORCE)
set(SDL_TEST OFF CACHE BOOL "" FORCE)
add_subdirectory("${SDL2_ROOT}" "${CMAKE_BINARY_DIR}/SDL2")
if(TARGET SDL2::SDL2)
    set(ENGINE_SDL_LIB SDL2::SDL2)
else()
    set(ENGINE_SDL_LIB SDL2)
endif()
set(SDL2_INCLUDE_DIR "${SDL2_ROOT}/include" CACHE PATH "" FORCE)
set(SDL2_LIBRARY ${ENGINE_SDL_LIB} CACHE STRING "" FORCE)

list(APPEND CMAKE_MODULE_PATH "${ENGINE_SOURCE_DIR}/cmake")
set(USE_SDL ON CACHE BOOL "" FORCE)
set(LOW_MEMORY ON CACHE BOOL "" FORCE)
set(BUILD_UNREALTOURNAMENT ON CACHE BOOL "" FORCE)
set(BUILD_EDITOR OFF CACHE BOOL "" FORCE)
set(BUILD_NOPENGLDRV OFF CACHE BOOL "" FORCE)
set(BUILD_PVRDRV OFF CACHE BOOL "" FORCE)
set(BUILD_NULLSOUNDDRV OFF CACHE BOOL "" FORCE)
set(BUILD_AICADRV OFF CACHE BOOL "" FORCE)
set(BUILD_DCUTIL OFF CACHE BOOL "" FORCE)
set(BUILD_STATIC ON CACHE BOOL "" FORCE)
set(PLATFORM_DREAMCAST OFF CACHE BOOL "" FORCE)

add_compile_definitions(PLATFORM_ANDROID=1 ANDROID=1 PLATFORM_LITTLE_ENDIAN=1 UNREAL_GOLD_226=1)
add_compile_options(-Wno-address-of-packed-member)

add_subdirectory("${ENGINE_SOURCE_DIR}" "${CMAKE_BINARY_DIR}/engine")

# The launcher target keeps its upstream name; ship it as libUnreal226.so.
set_target_properties(UnrealTournament PROPERTIES PREFIX "lib" OUTPUT_NAME "Unreal226")
target_link_libraries(UnrealTournament log android dl)
