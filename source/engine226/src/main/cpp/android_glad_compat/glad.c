#include "glad.h"

#include <string.h>

int GLAD_GL_APPLE_texture_format_BGRA8888 = 0;
int GLAD_GL_EXT_texture_format_BGRA8888 = 0;
int GLAD_GL_MESA_bgra = 0;

static int has_extension(const char* extensions, const char* extension)
{
    const size_t extension_len = strlen(extension);
    const char* start = extensions;

    if (!extensions || !extension || extension_len == 0) {
        return 0;
    }

    for (;;) {
        const char* where = strstr(start, extension);
        const char* terminator = NULL;

        if (!where) {
            return 0;
        }

        terminator = where + extension_len;
        if ((where == start || *(where - 1) == ' ') && (*terminator == ' ' || *terminator == '\0')) {
            return 1;
        }

        start = terminator;
    }
}

int gladLoadGLES2Loader(GLADloadproc load)
{
    const char* extensions = (const char*)glGetString(GL_EXTENSIONS);
    (void)load;

    GLAD_GL_APPLE_texture_format_BGRA8888 = has_extension(extensions, "GL_APPLE_texture_format_BGRA8888");
    GLAD_GL_EXT_texture_format_BGRA8888 = has_extension(extensions, "GL_EXT_texture_format_BGRA8888");
    GLAD_GL_MESA_bgra = has_extension(extensions, "GL_MESA_bgra");

    return 1;
}
