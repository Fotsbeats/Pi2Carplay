/* Diagnostic only: forward the original calls and trace bounded HID packet prefixes. */
extern void *dlsym(void *, const char *);
extern int readlink(const char *, char *, unsigned);
extern int snprintf(char *, unsigned, const char *, ...);
static int (*original_read)(int, void *, unsigned);
static int (*original_write)(int, const void *, unsigned);
static unsigned traced;
__attribute__((constructor)) static void initialize(void) {
    /* Bionic ARM32 RTLD_NEXT is -2; -1 means RTLD_DEFAULT and would find ourselves. */
    original_read = dlsym((void *)-2, "read");
    original_write = dlsym((void *)-2, "write");
}
static void trace(int fd, const void *buffer, int count, char direction) {
    if (count <= 0 || !original_write || traced >= 64) return;
    char path[40], target[80], line[120];
    snprintf(path,sizeof(path),"/proc/self/fd/%d",fd);
    int size = readlink(path,target,sizeof(target)-1);
    if (size != 10) return;
    target[size] = 0;
    const char expected[] = "/dev/hidg0";
    for (unsigned i=0;i<10;i++) if (target[i] != expected[i]) return;
    if (__sync_fetch_and_add(&traced,1) >= 64) return;
    int length = snprintf(line,sizeof(line),"HID %c bytes=%d prefix=",direction,count);
    const unsigned char *bytes = buffer;
    const char hex[] = "0123456789abcdef";
    for (int i=0;i<count && i<16;i++) {
        line[length++] = hex[bytes[i] >> 4];
        line[length++] = hex[bytes[i] & 15];
    }
    line[length++] = '\n';
    original_write(2,line,length);
}
int read(int fd, void *buffer, unsigned size) {
    if (!original_read) return -1;
    int result = original_read(fd,buffer,size);
    trace(fd,buffer,result,'R');
    return result;
}
int write(int fd, const void *buffer, unsigned size) {
    if (!original_write) return -1;
    int result = original_write(fd,buffer,size);
    trace(fd,buffer,result,'W');
    return result;
}
