/* Pinned SDK: stream jitter buffer +0x110; output ASBD bytes/frame +0x7c. */
struct call_mic_api {
    int (*prepared_frames)(void *);
    int (*read_bytes)(void *,void *,unsigned);
};
static int call_mic_pull(struct call_mic_api *api,void *stream,void *pcm,unsigned capacity) {
    if(!api->prepared_frames || !api->read_bytes || !stream || !pcm ||
            capacity<2 || capacity>960 || (capacity&1))return -1;
    unsigned char *jitter=(unsigned char *)stream+0x110;
    if(*(unsigned *)(jitter+0x7c)!=2)return -2;
    int frames=api->prepared_frames(jitter);
    if(frames<0)return -3;
    if(!frames)return 0;
    unsigned count=(unsigned)frames;
    if(count>capacity/2)count=capacity/2;
    unsigned bytes=count*2;
    return api->read_bytes(jitter,pcm,bytes)==0?(int)bytes:-4;
}
