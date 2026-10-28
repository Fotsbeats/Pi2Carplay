#ifndef PI2CARPLAY_DISPLAY_GEOMETRY_H
#define PI2CARPLAY_DISPLAY_GEOMETRY_H
struct display_geometry { int width, height, fps, x, y, content_width, content_height; };
/* Bound allocations and reject unsupported frames instead of silently cropping. */
static int display_geometry_valid(const struct display_geometry *g) {
    return g->width >= 320 && g->height >= 240 && g->width <= 2560 && g->height <= 1440 &&
        !(g->width & 1) && !(g->height & 1) && g->fps >= 1 && g->fps <= 60 &&
        g->x >= 0 && g->y >= 0 && g->content_width >= 320 && g->content_height >= 240 &&
        g->x <= g->width - g->content_width && g->y <= g->height - g->content_height;
}
/* SDK gSafeAreas rows are height,width,x,y; gHaveSafeAreas is one byte per row. */
static int display_geometry_resolve(struct display_geometry *g, int width, int height, int fps,
        int active, const unsigned char *have, const int *areas) {
    *g = (struct display_geometry){width, height, fps > 60 ? 60 : fps, 0, 0, width, height};
    if (!have || !areas || active < -1 || active >= 32) return -1;
    if (active == -1) {
        /* Some receivers advertise no view-area index and no reserved rectangle. */
        for (int i=0;i<32;i++) if (have[i]) return -1;
        return display_geometry_valid(g) ? 0 : -1;
    }
    if (have[active]) {
        const int *a = areas + active * 4;
        g->content_height = a[0]; g->content_width = a[1]; g->x = a[2]; g->y = a[3];
    }
    return display_geometry_valid(g) ? 0 : -1;
}
#endif
