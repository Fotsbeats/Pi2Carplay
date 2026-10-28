extern int ioctl(int, unsigned long, ...);
static int touch_fd = -1;
static int touch_down[2], touch_id, touch_width, touch_height, touch_origin_x, touch_origin_y;
static int touch_view_x, touch_view_y, touch_view_width, touch_view_height;
struct touch_event { long sec, usec; unsigned short type, code; int value; };
static int touch_emit(unsigned short type, unsigned short code, int value) {
    struct touch_event event = {0, 0, type, code, value};
    return write(touch_fd, &event, sizeof(event)) == sizeof(event) ? 0 : -1;
}
static int touch_create(int width, int height, int origin_x, int origin_y, int physical_width, int physical_height) {
    if (physical_width <= 0 || physical_height <= 0 || physical_width > 8192 || physical_height > 8192) return -1;
    if (width <= 0 || height <= 0 || width > 8192 || height > 8192) return -1;
    struct {
        char name[80]; unsigned short bus, vendor, product, version;
        unsigned effects; int max[64], min[64], fuzz[64], flat[64];
    } device = {0};
    const char name[] = "Pi CarPlay touchscreen";
    for (unsigned i = 0; i < sizeof(name); i++) device.name[i] = name[i];
    device.bus = 3; device.vendor = 0x1238; device.product = 1; device.version = 1;
    device.max[0] = device.max[53] = physical_width - 1;
    device.max[1] = device.max[54] = physical_height - 1;
    device.max[47] = 1; device.max[57] = 65535;
    touch_fd = open("/dev/uinput", 2049);
    if (touch_fd < 0) return -1;
    int axes[] = {0, 1, 47, 53, 54, 57};
    if (ioctl(touch_fd, 0x40045564, 1) || ioctl(touch_fd, 0x40045564, 3) ||
        ioctl(touch_fd, 0x40045565, 330) || ioctl(touch_fd, 0x4004556e, 1)) goto fail;
    for (unsigned i = 0; i < sizeof(axes)/sizeof(axes[0]); i++)
        if (ioctl(touch_fd, 0x40045567, axes[i])) goto fail;
    if (write(touch_fd, &device, sizeof(device)) != sizeof(device) || ioctl(touch_fd, 0x5501)) goto fail;
    /* Android maps uinput through the physical viewport, including wm-size letterboxing. */
    if (physical_width * height <= physical_height * width) {
        touch_view_width = physical_width; touch_view_height = height * physical_width / width;
    } else {
        touch_view_height = physical_height; touch_view_width = width * physical_height / height;
    }
    touch_view_x = (physical_width - touch_view_width) / 2;
    touch_view_y = (physical_height - touch_view_height) / 2;
    touch_width = width; touch_height = height; touch_origin_x = origin_x; touch_origin_y = origin_y;
    return 0;
fail:
    close(touch_fd); touch_fd = -1; return -1;
}
static int touch_raw_x(int x) { return touch_view_x + (x * touch_view_width + touch_width/2) / touch_width; }
static int touch_raw_y(int y) { return touch_view_y + (y * touch_view_height + touch_height/2) / touch_height; }
static int touch_point(int slot, int x, int y, int pressed) {
    if (pressed && (x < 0 || y < 0 || x >= touch_width || y >= touch_height)) return -1;
    if (touch_emit(3, 47, slot)) return -1;
    if (pressed != touch_down[slot] && touch_emit(3, 57, pressed ? (++touch_id & 65535) : -1)) return -1;
    if (pressed && (touch_emit(3, 53, touch_raw_x(x)) || touch_emit(3, 54, touch_raw_y(y)))) return -1;
    touch_down[slot] = pressed;
    return 0;
}
static void touch_callback(void *session, int x, int y, int pressed,
                           int x2, int y2, int pressed2, void *context) {
    if (touch_fd < 0) return;
    x -= touch_origin_x; y -= touch_origin_y;
    x2 -= touch_origin_x; y2 -= touch_origin_y;
    if (x == touch_width) x--;
    if (y == touch_height) y--;
    if (x2 == touch_width) x2--;
    if (y2 == touch_height) y2--;
    if ((pressed && (x < 0 || y < 0 || x >= touch_width || y >= touch_height)) ||
        (pressed2 > 0 && (x2 < 0 || y2 < 0 || x2 >= touch_width || y2 >= touch_height))) {
        print("ERROR: HU touch coordinates outside negotiated display\n"); return;
    }
    int error = touch_point(0, x, y, pressed != 0);
    error |= touch_point(1, x2, y2, pressed2 > 0);
    if (!error) {
        if (touch_down[0]) { error |= touch_emit(3, 0, touch_raw_x(x)); error |= touch_emit(3, 1, touch_raw_y(y)); }
        error |= touch_emit(1, 330, touch_down[0] || touch_down[1]);
        error |= touch_emit(0, 0, 0);
    }
    if (error) print("ERROR forwarding HU touch report\n");
}
