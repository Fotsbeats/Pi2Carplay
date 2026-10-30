extern int pthread_create(unsigned long *, const void *, void *(*)(void *), void *);
extern int pthread_join(unsigned long, void **);
static volatile int audio_running = 1;
#include "call_audio.h"
#include "call_mic.h"
static void *audio_sender(void *library) {
    void *(*start)(int,int,int,int,int,int) = dlsym(library,"carplay_reverse_audio_start");
    void (*push)(void *,void *,unsigned) = dlsym(library,"carplay_reverse_audio_push_frame");
    void (*stop)(void *) = dlsym(library,"carplay_reverse_audio_stop");
    struct call_mic_api microphone={dlsym(library,"GetRTPJitterBufferPreparedLen"),dlsym(library,"RTPJitterBufferRead")};
    if(!start || !push || !stop) { print("ERROR: audio API missing\n");return 0; }
    void *audio=start(0,0,0,48000,16,2);
    if(!audio) { print("ERROR: media audio start failed\n");return 0; }
    /* The legacy handle's negotiated UDP port is at offset 0xd8. */
    for(int i=0;i<50 && ((volatile int *)audio)[0x36]==-1;i++)usleep(100000);
    if(((volatile int *)audio)[0x36]==-1) { print("ERROR: media audio setup timeout\n");stop(audio);return 0; }
    print("CarPlay media audio ready: 48000 Hz stereo PCM16\n");fsync(1);
    int fd=open(getenv("AIBOX_AUDIO_PIPE"),2048);
    if(fd<0) { print("ERROR: audio capture pipe unavailable\n");stop(audio);return 0; }
    struct call_audio call;call_open(&call);
    int in_call=0;
    unsigned char pcm[960];unsigned used=0,packets=0,nonzero=0,waiting=0;
    struct bridge_poll input={fd,1,0};
    while(audio_running) {
        call_step(&call,audio,in_call);
        if(call.requested!=in_call) {
            if(in_call) {
                char stats[240];snprintf(stats,sizeof(stats),"CarPlay call ended: playback_packets=%u microphone_packets=%u nonzero_mic_packets=%u dropped=%u paced_packets=%u underruns=%u dropped_pcm_bytes=%u\n",call.down,call.up,call.nonzero,call.dropped,call.playback.packets,call.playback.underruns,call.playback.dropped);print(stats);
            }
            if(audio)stop(audio);
            audio=0;in_call=call.requested;used=0;
            if(in_call && microphone.prepared_frames && microphone.read_bytes)audio=start(1,1,0,16000,16,1);
            else if(!in_call)audio=start(0,0,0,48000,16,2);
            if(audio)for(int i=0;i<50 && audio_running && ((volatile int *)audio)[0x36]==-1;i++)usleep(100000);
            if(!audio || ((volatile int *)audio)[0x36]==-1) {
                print("ERROR: audio mode setup failed\n");
                if(audio)stop(audio);
                audio=0;
                if(!in_call)break;
                call_error(&call,"CarPlay telephony setup failed");call.requested=0;call.failed=1;continue;
            }
            print(in_call?"CarPlay telephony duplex ready: 16000 Hz mono PCM16\n":"CarPlay media audio resumed\n");
            call_send(&call,4,0,0);
        }
        if(in_call && audio && !call.failed) {
            struct probe_time now;
            if(clock_gettime(1,&now)) { call_error(&call,"Call playback clock unavailable");call.requested=0;call.failed=1;continue; }
            call_playback_tick(&call.playback,(unsigned)now.sec*1000+(unsigned)now.nsec/1000000,audio,push);
            int n=call_mic_pull(&microphone,audio,pcm,320);
            if(n<0) {
                char message[128];snprintf(message,sizeof(message),"CarPlay microphone read failed (code %d)",n);
                print("ERROR: ");print(message);print("\n");call_error(&call,message);
                call.requested=0;call.failed=1;continue;
            }
            if(n) {
                for(int i=0;i<n;i++)if(pcm[i]){call.nonzero++;break;}
                if(!call_send(&call,2,pcm,(unsigned)n)) { call.up++;if(call.up==1)print("First CarPlay microphone PCM received\n"); }
            }
        }
        int available=poll(&input,1,in_call?5:100);
        if(available==0)continue;
        if(available<0)break;
        int n=read(fd,pcm+used,sizeof(pcm)-used);
        if(n<=0) { if(packets || ++waiting>50)break; usleep(100000);continue; }
        if(in_call)continue;
        used+=n;
        if(used!=sizeof(pcm))continue;
        for(unsigned i=0;i<sizeof(pcm);i++)if(pcm[i]){nonzero++;break;}
        /* Keep below the original sender's fixed ~1.5 KiB RTP scratch buffer. */
        push(audio,pcm,sizeof(pcm));used=0;packets++;
        if(packets==1)print("First Android PCM packet submitted\n");
    }
    if(used)print("ERROR: incomplete final PCM packet\n");
    char result[128];snprintf(result,sizeof(result),"Audio capture ended: packets=%u non_silent_packets=%u\n",packets,nonzero);print(result);
    close(fd);if(audio)stop(audio);call_close(&call);return 0;
}
