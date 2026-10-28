#include <assert.h>
static int open(const char *, int);
static int close(int);
static int write(int, const void *, unsigned);
static void print(const char *);
#include "touch.h"
static struct touch_event events[64];
static unsigned count;
static int errors;
static int open(const char *path, int flags) { return 4; }
static int close(int fd) { return 0; }
int ioctl(int fd, unsigned long op, ...) { return 0; }
static int write(int fd, const void *data, unsigned size) {
    if (size == sizeof(struct touch_event)) events[count++] = *(const struct touch_event *)data;
    return size;
}
static void print(const char *text) { errors++; }
int main(void) {
    assert(touch_create(1744, 720, 176, 0, 1920, 1080) == 0);
    touch_callback(0, 276, 200, 1, -1, -1, -1, 0);
    assert(count == 9 && events[1].code == 57 && events[1].value == 1);
    assert(events[2].code == 53 && events[2].value == 110);
    assert(events[7].code == 330 && events[7].value == 1);
    count = 0;
    touch_callback(0, 296, 220, 1, 476, 400, 1, 0);
    assert(touch_down[0] && touch_down[1]);
    assert(events[4].code == 57 && events[4].value == 2);
    count = 0;
    touch_callback(0, 296, 220, 0, -1, -1, -1, 0);
    assert(!touch_down[0] && !touch_down[1]);
    assert(events[1].value == -1 && events[3].value == -1);
    assert(events[4].code == 330 && events[4].value == 0);
    assert(events[5].type == 0 && events[5].code == 0);
    count = 0;
    touch_callback(0, 9000, 0, 1, -1, -1, -1, 0);
    assert(errors == 1);
    count = 0;
    touch_callback(0, 176, 0, 1, -1, -1, -1, 0);
    assert(events[2].value == 0);
    count = 0;
    touch_callback(0, 1920, 720, 1, -1, -1, -1, 0);
    assert(events[1].value == 1919 && events[2].value == 935);
    count = 0;
    touch_callback(0, 100, 0, 1, -1, -1, -1, 0);
    assert(count == 0 && errors == 2);
    assert(touch_view_x == 0 && touch_view_y == 144 && touch_view_width == 1920 && touch_view_height == 792);
    for (int y=0; y<720; y++) {
        int logical = ((touch_raw_y(y)-144)*720+396)/792;
        assert(logical-y >= -1 && logical-y <= 1);
    }
    assert(touch_create(1744,720,176,0,1744,720)==0);
    assert(touch_raw_x(500)==500 && touch_raw_y(300)==300);
    assert(touch_create(1280,720,0,0,1920,1080)==0);
    assert(touch_raw_x(0)==0 && touch_raw_y(0)==0);
    assert(touch_raw_x(1279)==1919 && touch_raw_y(719)==1079);
    assert(touch_create(1000,600,100,60,1920,1080)==0);
    assert(touch_view_x==60 && touch_view_y==0);
    count=0;touch_callback(0,100,60,1,-1,-1,-1,0);
    assert(count>0);
    assert(touch_create(0,720,0,0,1920,1080)==-1);
    return 0;
}
