static int annexb_start(const unsigned char *data, int size, int from, int *prefix) {
    for (int i = from; i + 3 <= size; i++) {
        if (data[i] || data[i+1]) continue;
        if (data[i+2] == 1) { *prefix = 3; return i; }
        if (i + 4 <= size && !data[i+2] && data[i+3] == 1) {
            *prefix = 4; return i;
        }
    }
    return -1;
}
