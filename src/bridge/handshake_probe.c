/* Connection diagnostic: original protocol core with Pi USB, video and touch adapters. */
extern void *dlopen(const char *, int);
extern void *dlsym(void *, const char *);
extern char *dlerror(void);

static void print(const char *text) {
    unsigned length = 0;
    if (!text) text = "(null)";
    while (text[length]) length++;
    register long a0 __asm__("r0") = 1;
    register const char *a1 __asm__("r1") = text;
    register unsigned a2 __asm__("r2") = length;
    register long nr __asm__("r7") = 4;
    __asm__ volatile("svc 0" : "+r"(a0) : "r"(a1), "r"(a2), "r"(nr) : "memory");
}


extern int open(const char *, int, ...);
extern int close(int);
extern int fsync(int);
extern int write(int, const void *, unsigned);
extern unsigned sleep(unsigned);
extern int usleep(unsigned);
extern char *getenv(const char *);
extern void *malloc(unsigned);
extern void free(void *);
extern int read(int, void *, unsigned);
extern int unsetenv(const char *);
extern int system(const char *);
struct bridge_poll { int fd; short events, revents; };
extern int poll(struct bridge_poll *, unsigned, int);
extern int sscanf(const char *, const char *, ...);
extern int snprintf(char *, unsigned, const char *, ...);
#include "annexb.h"
#include "touch.h"
#include "display_geometry.h"
struct probe_time { long sec, nsec; };
extern int clock_gettime(int, struct probe_time *);
#include "audio.h"
#include "gps_forward.h"

static int negotiated_geometry(void *handle, struct display_geometry *g);
static int geometry_equal(const struct display_geometry *a,const struct display_geometry *b) {
    return a->width==b->width&&a->height==b->height&&a->fps==b->fps&&a->x==b->x&&a->y==b->y&&
        a->content_width==b->content_width&&a->content_height==b->content_height;
}
static int stream_display(void *handle) {
    struct display_geometry initial;
    if(negotiated_geometry(handle,&initial))return -1;
    const char *path = getenv("AIBOX_VIDEO_PIPE");
    if (!path) return -1;
    void *(*start)(void) = dlsym(handle, "carplay_reverse_video_start");
    int (*push)(void *, void *, int, int) = dlsym(handle, "carplay_reverse_video_push_frame");
    void (*stop)(void *) = dlsym(handle, "carplay_reverse_video_stop");
    if (!start || !push || !stop) return -1;
    void *video = start();
    if (!video) return -1;
    for (int i = 0; i < 50 && !((volatile int *)video)[0x96]; i++) usleep(100000);
    if (!((volatile int *)video)[0x96]) { stop(video); return -1; }
    int fd = open(path, 2048); /* Nonblocking open also permits recovery if capture fails before opening its writer. */
    unsigned char *frame = malloc(2097172);
    if (fd < 0 || !frame) {
        if (fd >= 0) close(fd);
        free(frame); stop(video); return -1;
    }
    unsigned header_used = 0, payload_used = 0, packet_size = 0, config = 0;
    unsigned char header[12];
    unsigned age_total = 0, age_max = 0, push_total = 0, push_max = 0;
    int frames = 0;
    struct probe_time began, now;
    if (clock_gettime(1, &began)) { close(fd); free(frame); stop(video); return -1; }
    int continuous = getenv("AIBOX_CONTINUOUS") != 0;
    long last_geometry_check=began.sec;
    volatile unsigned char *connected = dlsym(handle,"gClientReady");
    struct bridge_poll input={fd,1,0};
    for (;;) {
        if (clock_gettime(1, &now) || (!continuous && now.sec - began.sec >= 150) || (!frames && now.sec - began.sec >= 10) || (connected && !*connected)) break;
        if(now.sec!=last_geometry_check) {
            struct display_geometry current;last_geometry_check=now.sec;
            if(negotiated_geometry(handle,&current)||!geometry_equal(&initial,&current)) {
                print("Display geometry changed during session; reconnect to renegotiate safely\n");break;
            }
        }
        int available=poll(&input,1,1000);
        if(available==0)continue;
        if(available<0)break;
        int count;
        if (header_used < sizeof(header)) {
            count = read(fd, header + header_used, sizeof(header) - header_used);
            if (count > 0) header_used += count;
            if (header_used == sizeof(header)) {
                packet_size = ((unsigned)header[0]<<24) | ((unsigned)header[1]<<16) | ((unsigned)header[2]<<8) | header[3];
                config = ((unsigned)header[4]<<24) | ((unsigned)header[5]<<16) | ((unsigned)header[6]<<8) | header[7];
                if (!packet_size || packet_size > 2097152 || config > 1) {
                    print("ERROR: invalid encoded packet header\n"); break;
                }
            }
        } else {
            count = read(fd, frame + payload_used, packet_size - payload_used);
            if (count > 0) payload_used += count;
            if (payload_used == packet_size) {
                struct probe_time submitted;
                if (clock_gettime(1, &now)) break;
                unsigned timestamp = ((unsigned)header[8]<<24) | ((unsigned)header[9]<<16) | ((unsigned)header[10]<<8) | header[11];
                unsigned now_ms = (unsigned)now.sec * 1000 + now.nsec / 1000000;
                unsigned age = now_ms - timestamp;
                if (!config && age > 150000) { print("ERROR: invalid capture timestamp\n"); break; }
                if (push(video, frame, packet_size, config)) { print("ERROR submitting video packet\n"); break; }
                if (clock_gettime(1, &submitted)) break;
                unsigned push_ms = (unsigned)submitted.sec * 1000 + submitted.nsec / 1000000 - now_ms;
                if (!config) {
                    age_total += age; if (age > age_max) age_max = age;
                    push_total += push_ms; if (push_ms > push_max) push_max = push_ms;
                }
                if (!config && ++frames == 1) {
                    const char *session_file=getenv("AIBOX_SESSION_FILE");
                    if(session_file) {
                        int marker=open(session_file,577,0600);
                        if(marker<0 || write(marker,"1\n",2)!=2 || fsync(marker)) {
                            if(marker>=0)close(marker); print("ERROR saving established-session marker\n"); break;
                        }
                        close(marker);
                    }
                    print("First framed Android video packet submitted; CarPlay session established\n"); fsync(1);
                }
                header_used = payload_used = 0;
            }
        }
        if (count <= 0) { if(frames)break; usleep(100000); }
    }
    if (frames) {
        char metrics[200];
        snprintf(metrics, sizeof(metrics), "Bridge timing: frames=%d capture_to_submit_mean_ms=%u max_ms=%u protocol_push_mean_ms=%u max_ms=%u\n",
                 frames, age_total / frames, age_max, push_total / frames, push_max);
        print(metrics);
    }
    print(frames ? "Live Android video capture ended\n" : "ERROR: no live frames submitted\n");
    close(fd); free(frame); stop(video);
    return frames ? 0 : -1;
}
static volatile int video_info_ready;
extern int rename(const char *, const char *);
static int negotiated_geometry(void *handle, struct display_geometry *g) {
    void (*dimensions)(int *,int *,int *)=dlsym(handle,"carplay_reverse_get_video_parameters");
    int *active=dlsym(handle,"gActiveViewArea");
    unsigned char *have=dlsym(handle,"gHaveSafeAreas");
    int *areas=dlsym(handle,"gSafeAreas");
    int w=0,h=0,fps=0;
    if(!dimensions||!active||!have||!areas)return -1;
    dimensions(&w,&h,&fps);
    return display_geometry_resolve(g,w,h,fps,*active,have,areas);
}
static int publish_geometry(const struct display_geometry *g) {
    const char *path=getenv("AIBOX_GEOMETRY_FILE");
    if(!path)return -1;
    char temporary[512],line[160];
    int n=snprintf(temporary,sizeof(temporary),"%s.tmp",path);
    if(n<=0||n>=(int)sizeof(temporary))return -1;
    int count=snprintf(line,sizeof(line),"%d %d %d %d %d %d %d\n",
        g->width,g->height,g->fps,g->x,g->y,g->content_width,g->content_height);
    int fd=open(temporary,577,0600);
    if(fd<0)return -1;
    int failed=write(fd,line,(unsigned)count)!=count||fsync(fd);
    if(close(fd))failed=1;
    if(failed||rename(temporary,path))return -1;
    print("Negotiated canvas/fps/content rectangle: ");print(line);
    return 0;
}
static void video_info_callback(void *context, int width, int height, int fps) {
    video_info_ready = 1;
}

static void network_callback(void *context, int state) {
    if (state != 1) return;
    int result = system("ip link set eth1 up && ip -6 route replace fe80::/64 dev eth1 table 2999 && "
                        "{ ip -6 rule del pref 10500 oif eth1 lookup 2999 2>/dev/null; "
                        "ip -6 rule add pref 10500 oif eth1 lookup 2999; }");
    print(result ? "ERROR configuring Honda IPv6 route\n" : "Honda IPv6 route configured\n");
}

static int send_test_image(void *handle) {
    const char *path = getenv("AIBOX_VIDEO_FILE");
    if (!path) return 0;
    void *(*start)(void) = dlsym(handle, "carplay_reverse_video_start");
    int (*push)(void *, void *, int, int) = dlsym(handle, "carplay_reverse_video_push_frame");
    if (!start || !push) return -1;
    int fd = open(path, 0);
    if (fd < 0) return -1;
    unsigned char *data = malloc(2097152);
    unsigned char *frame = malloc(2097168);
    if (!frame) { close(fd); free(data); free(frame); return -1; }
    int size = read(fd, data, 2097152);
    close(fd);
    int idr = -1;
    int sps = 0, pps = 0;
    for (int i = 0; i + 5 < size; i++) {
        if (data[i] || data[i+1] || data[i+2] || data[i+3] != 1) continue;
        int type = data[i+4] & 31;
        if (type == 7) sps = 1;
        if (type == 8) pps = 1;
        if (type == 5) { idr = i; break; }
    }
    if (size <= 0 || size == 2097152 || idr < 0 || !sps || !pps) {
        print("ERROR: test image needs Annex B SPS, PPS and IDR under 2 MiB\n");
        free(data); free(frame); return -1;
    }
    void *video = start();
    if (!video) { free(data); free(frame); return -1; }
    /* The returned legacy video handle exposes its stream-start flag at word 0x96. */
    for (int i = 0; i < 50 && !((volatile int *)video)[0x96]; i++) usleep(100000);
    if (!((volatile int *)video)[0x96]) {
        print("ERROR: CarPlay video stream did not become ready\n");
        free(data); free(frame); return -1;
    }
    int result = push(video, data, idr, 1);
    if (!result) {
        for (int i = idr; i < size; i++) frame[i-idr] = data[i];
        result = push(video, frame, size-idr, 0);
    }
    print(result ? "ERROR sending Android test image\n" : "Android test image submitted to CarPlay\n");
    free(data); free(frame);
    return result;
}
static volatile int core_ready;
static int switch_callback(void *context, int state) {
    /* Keep USB hidden until HID and the netlink receiver are initialized. */
    if (!state && !core_ready) return 1;
    const char *role = state == 1 ? "host" : "device";
    print(state == 1 ? "USB role request: host\n" : "USB role request: device\n");
    /* Allow the control status response to finish before changing the controller. */
    usleep(250000);
    int fd = open("/sys/class/usb_role/1000480000.usb-role-switch/role", 1);
    if (fd < 0) { print("ERROR opening role switch\n"); return 0; }
    int expected = state == 1 ? 4 : 6;
    int count = write(fd, role, expected);
    close(fd);
    if (count != expected) { print("ERROR writing USB role\n"); return 0; }
    print("USB role written successfully\n");
    return 1;
}
int probe_main(void) {
    if(getenv("AIBOX_GPS_SELF_TEST")) {
        const char *sentence="$GPRMC,123519,V,,,,,,,281026,,,N*51";
        unsigned length=0;while(sentence[length])length++;
        gps_callback(0,sentence,(int)length,0);
        return gps_forwarded==1?0:23;
    }
    const char *bus = getenv("AIBOX_OTG_BUS");
    if (!bus || !bus[0]) { print("OTG bus required\n"); return 2; }
    void *handle = dlopen("libSdBridge.so", 2);
    if (!handle) { print(dlerror()); return 3; }
    void *(*get_session)(void) = dlsym(handle, "getCarplayReverseSessionHandle");
    void (*set_delegate)(void *, void *) = dlsym(handle, "CarplayReverseSessionSetDelegate");
    void (*cfg)(const char *, const char *) = dlsym(handle, "sd_cfg_set_config");
    void (*init)(void) = dlsym(handle, "carplay_reverse_server_init");
    void (*video_notify)(void *, void *) = dlsym(handle, "CarplayReverseSessionRegisterNotifyScreenVideoInfoCallbacks");
    if (!get_session || !set_delegate || !cfg || !init || !video_notify) return 4;
    video_notify((void *)video_info_callback, 0);
    void *delegate[17] = {0};
    delegate[10] = (void *)switch_callback;
    delegate[11] = (void *)network_callback;
    delegate[6] = (void *)touch_callback;
    delegate[15] = (void *)gps_callback;
    void *session = get_session();
    if (!session) return 5;
    set_delegate(session, delegate);
    cfg("USB_NETLINK_PORT", "26");
    cfg("REVERSE_OTG_PORT", bus);
    cfg("REVERSE_USB_NET", "eth1");
    cfg("REVERSE_WIFI_NET", "wlan0");
    cfg("REVERSE_USB_HOST_TIMEOUT", "7");
    cfg("REVERSE_HTTP_TIMEOUT", "7");
    cfg("REVERSE_OEM_GPS", "1");
    print("Starting limited head-unit connection probe with optional Android video/touch\n");
    init();
    /* Child Android tools are 64-bit and cannot inherit the private ARM32 runtime. */
    unsetenv("LD_PRELOAD");
    unsetenv("LD_LIBRARY_PATH");
    core_ready = 1;
    if (!switch_callback(0, 0)) return 6;
    if (getenv("AIBOX_HOLD_USB") && getenv("AIBOX_HOLD_USB")[0] == '1') {
        int fd = open("/sys/module/aibox_usb/parameters/connect_ready",1);
        if (fd < 0 || write(fd,"1",1) != 1) return 7;
        close(fd);
        print("USB connection gate released after protocol initialization\n");
    }
    if (fsync(1)) print("Diagnostic output is not a regular synced log file\n");
    volatile unsigned char *ready = dlsym(handle, "gClientReady");
    int image_sent = 0;
    audio_running = 1;
    for (unsigned i = 0; i < 180; i++) {
        sleep(1);
        if (!image_sent && ready && *ready && video_info_ready &&
            (getenv("AIBOX_VIDEO_FILE") || getenv("AIBOX_VIDEO_PIPE"))) {
            image_sent = 1;
            struct display_geometry geometry;
            int physical_width = 0, physical_height = 0;
            const char *physical = getenv("AIBOX_PHYSICAL_SIZE");
            if (!physical || sscanf(physical,"%dx%d",&physical_width,&physical_height)!=2 ||
                    negotiated_geometry(handle,&geometry)) {
                print("ERROR: unsupported or invalid negotiated display geometry\n"); break;
            }
            if(touch_create(geometry.content_width,geometry.content_height,geometry.x,geometry.y,
                    physical_width,physical_height) || publish_geometry(&geometry)) {
                print("ERROR preparing negotiated display/touch\n");break;
            }
            print("Android touchscreen created from negotiated geometry\n");
            if (getenv("AIBOX_VIDEO_PIPE")) {
                unsigned long audio_thread = 0;
                int audio_started = getenv("AIBOX_AUDIO_PIPE") && !pthread_create(&audio_thread,0,audio_sender,handle);
                stream_display(handle);
                audio_running = 0;
                if(audio_started) pthread_join(audio_thread,0);
                break;
            }
            else send_test_image(handle);
        }
    }
    print("Connection probe elapsed; exiting all protocol threads\n");
    if (touch_fd >= 0) { ioctl(touch_fd, 0x5502); close(touch_fd); }
    return image_sent ? 0 : 9;
}
__asm__(".global _start\n.type _start,%function\n_start:\nbl probe_main\nmov r7,#248\nsvc #0\n");
