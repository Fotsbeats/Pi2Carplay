extern int open(const char *, int, ...);
extern int close(int);
extern int ioctl(int, unsigned long, ...);
extern int socket(int, int, int);
extern int bind(int, const void *, unsigned);
extern int setsockopt(int, int, int, const void *, unsigned);
extern int recvfrom(int, void *, unsigned, int, void *, void *);
extern char *getenv(const char *);
extern int strcmp(const char *, const char *);
extern int puts(const char *);
extern int fflush(void *);
extern int read(int, void *, unsigned);
extern int write(int, const void *, unsigned);
extern int memcmp(const void *, const void *, unsigned);
extern int usleep(unsigned);
struct report32 { unsigned pointer, length, id; };
_Static_assert(sizeof(void *) == 4 && sizeof(struct report32) == 12, "Baseline ARM32 ABI");
static int reports(void) {
    int fd = open("/dev/sd_reverse_hid", 2);
    if (fd < 0) return 2;
    /* Standard relative mouse: registration, input delivery, deletion and rejection paths. */
    unsigned char desc[] = {0x05,1,0x09,2,0xa1,1,0x09,1,0xa1,0,
        0x05,9,0x19,1,0x29,3,0x15,0,0x25,1,0x95,3,0x75,1,0x81,2,
        0x95,1,0x75,5,0x81,1,0x05,1,0x09,0x30,0x09,0x31,0x15,0x81,
        0x25,0x7f,0x75,8,0x95,2,0x81,6,0xc0,0xc0};
    struct report32 report = {(unsigned)desc, sizeof(desc), 73};
    if (ioctl(fd, 0x12703, &report)) return 3;
    /* Replacing an existing handle must not leak another device. */
    if (ioctl(fd, 0x12703, &report)) return 4;
    unsigned char bytes[3] = {0,0,0};
    report.pointer = (unsigned)bytes; report.length = sizeof(bytes);
    if (ioctl(fd, 0x12705, &report) < 0) return 5;
    unsigned remove[2] = {0, 73};
    if (ioctl(fd, 0x12704, remove)) return 6;
    if (ioctl(fd, 0x12705, &report) >= 0) return 7;
    report.length = 0;
    if (ioctl(fd, 0x12703, &report) >= 0) return 8;
    report.length = 65536;
    if (ioctl(fd, 0x12703, &report) >= 0) return 9;
    report.length = 3; report.pointer = 1;
    if (ioctl(fd, 0x12703, &report) >= 0) return 10;
    if (ioctl(fd, 0x99999, &report) >= 0) return 11;
    report.pointer = (unsigned)desc; report.length = sizeof(desc);
    if (ioctl(fd, 0x12703, &report)) return 20;
    remove[0] = 1;
    if (ioctl(fd, 0x12704, remove)) return 21;
    report.id = 74;
    if (ioctl(fd, 0x12703, &report)) return 22;
    close(fd);
    puts("PASS: ARM32 remote HID register/replace/report/remove/all/close and invalid-input checks");
    return 0;
}
static int notifications(void) {
    struct { unsigned short family, pad; unsigned pid, groups; } address = {16,0,53,0};
    struct { int sec, usec; } timeout = {10,0};
    int fd = socket(16,3,26);
    if (fd < 0 || bind(fd, &address, sizeof(address)) ||
        setsockopt(fd,1,20,&timeout,sizeof(timeout))) return 12;
    char buffer[128];
    int ready = 0;
    for (unsigned i=0; i<5; i++) {
        int length = recvfrom(fd,buffer,sizeof(buffer)-1,0,0,0);
        if (length <= 16) return 13;
        buffer[length] = 0;
        if (!strcmp(buffer+16,"hidready")) ready=1;
        if (ready && !strcmp(buffer+16,"disconnected")) {
            close(fd);
            puts("PASS: actual HID set_alt/disable delivered hidready/disconnected on netlink26");
            return 0;
        }
    }
    return 14;
}
static int hid_data(void) {
    int gadget = open("/dev/hidg0", 2|2048);
    int host = open(getenv("AIBOX_TEST_HIDRAW"), 2|2048);
    if (gadget < 0 || host < 0) return 15;
    unsigned char output[6] = {13,11,22,33,44,55}, input[6] = {1,55,44,33,22,11}, got[1024];
    if (write(host, output, sizeof(output)) != sizeof(output)) return 16;
    int count = -1;
    for (unsigned i=0; i<200 && count<0; i++) { count=read(gadget,got,sizeof(got)); usleep(10000); }
    if (count != sizeof(output) || memcmp(got,output,sizeof(output))) return 17;
    unsigned char next[6] = {13,66,77,88,99,111};
    if (write(host,output,sizeof(output)) != sizeof(output) || write(host,next,sizeof(next)) != sizeof(next)) return 23;
    count = read(gadget,got,sizeof(got));
    if (count != sizeof(output) || memcmp(got,output,sizeof(output))) {
        puts("FAIL: burst HID SET_REPORT overwrote the first unread report"); return 24;
    }
    count = read(gadget,got,sizeof(got));
    if (count != sizeof(next) || memcmp(got,next,sizeof(next))) return 25;
    if (write(gadget,input,sizeof(input)) != sizeof(input)) return 18;
    count = -1;
    for (unsigned i=0; i<200 && count<0; i++) { count=read(host,got,sizeof(got)); usleep(10000); }
    if (count != sizeof(input) || memcmp(got,input,sizeof(input))) return 19;
    close(host); close(gadget);
    puts("PASS: exact HID report bytes delivered host SET_REPORT -> gadget read and gadget write -> host interrupt IN");
    puts("PASS: two unread output reports preserved in order");
    return 0;
}
static int detect_reply(void) {
    int host = open(getenv("AIBOX_TEST_HIDRAW"),2|2048);
    if (host < 0) return 26;
    unsigned char request[10] = {14,0,255,85,2,0,238,16,0,0}, reply[1024];
    if (write(host,request,sizeof(request)) != sizeof(request)) return 27;
    for (unsigned i=0;i<500;i++) {
        int count=read(host,reply,sizeof(reply));
        if (count >= 8 && reply[2]==255 && reply[3]==85) {
            puts("PASS: captured Honda iAP2 detection request received a detection response from original core");
            if (getenv("AIBOX_TEST_LINK")) {
                unsigned char syn[26] = {17,0,255,90,0,23,128,212,0,0,60,1,5,255,255,0,0,0,0,0,0,1,0,1,250,0};
                if (write(host,syn,sizeof(syn)) != sizeof(syn)) return 29;
                int received = 0;
                for (unsigned j=0;j<300;j++) {
                    count=read(host,reply,sizeof(reply));
                    if (count>=25 && reply[2]==255 && reply[3]==90 && reply[6]==192) { received=1; break; }
                    usleep(10000);
                }
                if (!received) { puts("FAIL: captured Honda SYN did not receive SYNACK"); return 30; }
                puts("PASS: captured Honda SYN received SYNACK from original core");
                unsigned char ack[14] = {15,0,255,90,0,9,64,212,0,0,0,0,0,0};
                ack[8]=reply[7];
                unsigned sum=0;
                for (unsigned j=2;j<10;j++) sum+=ack[j];
                ack[10]=(unsigned char)(0-sum);
                if (write(host,ack,sizeof(ack)) != sizeof(ack)) return 31;
                for (unsigned j=0;j<300;j++) {
                    count=read(host,reply,sizeof(reply));
                    for (int k=2;k+1<count;k++) if (reply[k]==170 && reply[k+1]==0) {
                        puts("PASS: iAP2 link established and original core requested remote authentication certificate");
                        close(host); return 0;
                    }
                    usleep(10000);
                }
                puts("FAIL: link ACK did not reach authentication certificate request"); return 32;
            }
            close(host); return 0;
        }
        usleep(10000);
    }
    puts("FAIL: original core did not send detection response within five seconds");
    close(host); return 28;
}
int probe_main(void) {
    int result = getenv("AIBOX_TEST_DETECT") ? detect_reply() : getenv("AIBOX_TEST_HIDRAW") ? hid_data() : getenv("AIBOX_TEST_NOTIFY") ? notifications() : reports();
    fflush(0);
    return result;
}
__asm__(".global _start\n.type _start,%function\n_start:\nbl probe_main\nmov r7,#248\nsvc #0\n");
