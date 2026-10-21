#pragma once

/*
 * Minimal GLAD compatibility layer for the UT99DC OpenGL ES renderer on Android.
 *
 * The upstream Dreamcast/desktop CMake expects Thirdparty/glad_es. The Android
 * package does not need a generated GLAD loader because OpenGL ES 2 symbols are
 * provided by libGLESv2 and SDL_GL_GetProcAddress is still available for optional
 * extension lookups. This header keeps the existing NOpenGLESDrv.cpp source intact
 * for the first Android bring-up pass.
 */

#ifndef UT99DC_ANDROID_GLAD_COMPAT_H
#define UT99DC_ANDROID_GLAD_COMPAT_H

#include <GLES2/gl2.h>
#include <GLES2/gl2ext.h>

#ifdef __cplusplus
extern "C" {
#endif

typedef void* (*GLADloadproc)(const char* name);

extern int GLAD_GL_APPLE_texture_format_BGRA8888;
extern int GLAD_GL_EXT_texture_format_BGRA8888;
extern int GLAD_GL_MESA_bgra;

int gladLoadGLES2Loader(GLADloadproc load);

#ifdef __cplusplus
}
#endif

#endif /* UT99DC_ANDROID_GLAD_COMPAT_H */
