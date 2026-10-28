#include <assert.h>
#include "display_geometry.h"
int main(void) {
    struct display_geometry g;
    unsigned char have[32] = {0}; int areas[128] = {0};
    have[1] = 1; areas[4] = 720; areas[5] = 1744; areas[6] = 176;
    assert(!display_geometry_resolve(&g,1920,720,60,1,have,areas));
    assert(g.x == 176 && g.content_width == 1744 && g.content_height == 720);
    assert(!display_geometry_resolve(&g,1280,720,30,0,have,areas));
    assert(g.x == 0 && g.content_width == 1280 && g.fps == 30);
    have[2] = 1; areas[8] = 600; areas[9] = 1000; areas[10] = 100; areas[11] = 60;
    assert(!display_geometry_resolve(&g,1280,720,60,2,have,areas));
    assert(g.x == 100 && g.y == 60 && g.content_width == 1000);
    assert(display_geometry_resolve(&g,800,480,60,2,have,areas));
    assert(display_geometry_resolve(&g,1280,720,60,32,have,areas));
    assert(display_geometry_resolve(&g,1281,720,60,0,have,areas));
    assert(display_geometry_resolve(&g,3840,2160,60,0,have,areas));
    assert(display_geometry_resolve(&g,1280,720,0,0,have,areas));
    assert(!display_geometry_resolve(&g,1280,720,120,0,have,areas) && g.fps == 60);
    assert(display_geometry_resolve(&g,1280,720,60,-1,have,areas));
    have[1]=have[2]=0;
    assert(!display_geometry_resolve(&g,1280,720,60,-1,have,areas));
    assert(g.x==0 && g.content_width==1280);
    return 0;
}
