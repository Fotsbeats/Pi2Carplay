extern int pthread_create(unsigned long *, const void *, void *(*)(void *), void *);
extern int pthread_join(unsigned long, void **);
static volatile int audio_running = 1;
static void *audio_sender(void *library) {
    void *(*start)(int,int,int,int,int,int) = dlsym(library,"carplay_reverse_audio_start");
    void (*push)(void *,void *,unsigned) = dlsym(library,"carplay_reverse_audio_push_frame");
    void (*stop)(void *) = dlsym(library,"carplay_reverse_audio_stop");
    if(!start || !push || !stop) { print("ERROR: audio API missing\n");return 0; }
    void *audio=start(0,0,0,48000,16,2);
    if(!audio) { print("ERROR: media audio start failed\n");return 0; }
    /* The legacy handle's negotiated UDP port is at offset 0xd8. */
    for(int i=0;i<50 && ((volatile int *)audio)[0x36]==-1;i++)usleep(100000);
    if(((volatile int *)audio)[0x36]==-1) { print("ERROR: media audio setup timeout\n");stop(audio);return 0; }
    print("CarPlay media audio ready: 48000 Hz stereo PCM16\n");fsync(1);
    int fd=open(getenv("AIBOX_AUDIO_PIPE"),2048);
    if(fd<0) { print("ERROR: audio capture pipe unavailable\n");stop(audio);return 0; }
    unsigned char pcm[960];unsigned used=0,packets=0,nonzero=0,waiting=0;
    struct bridge_poll input={fd,1,0};
    while(audio_running) {
        int available=poll(&input,1,100);
        if(available==0)continue;
        if(available<0)break;
        int n=read(fd,pcm+used,sizeof(pcm)-used);
        if(n<=0) { if(packets || ++waiting>50)break; usleep(100000);continue; }
        used+=n;
        if(used!=sizeof(pcm))continue;
        for(unsigned i=0;i<sizeof(pcm);i++)if(pcm[i]){nonzero++;break;}
        /* Keep below the original sender's fixed ~1.5 KiB RTP scratch buffer. */
        push(audio,pcm,sizeof(pcm));used=0;packets++;
        if(packets==1)print("First Android PCM packet submitted\n");
    }
    if(used)print("ERROR: incomplete final PCM packet\n");
    char result[128];snprintf(result,sizeof(result),"Audio capture ended: packets=%u non_silent_packets=%u\n",packets,nonzero);print(result);
    close(fd);stop(audio);return 0;
}
