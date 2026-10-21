#pragma once

#ifdef __cplusplus
extern "C" {
#endif

typedef void* xmp_context;

xmp_context xmp_create_context(void);
void xmp_free_context(xmp_context ctx);
int xmp_load_module_from_memory(xmp_context ctx, const void *mem, long size);
int xmp_start_player(xmp_context ctx, int rate, int flags);
void xmp_end_player(xmp_context ctx);
void xmp_release_module(xmp_context ctx);
int xmp_set_position(xmp_context ctx, int pos);
int xmp_play_buffer(xmp_context ctx, void *buffer, int size, int loop);

#ifdef __cplusplus
}
#endif
