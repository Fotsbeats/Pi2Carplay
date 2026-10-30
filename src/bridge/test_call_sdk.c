/* On-Pi synthetic dependency check; opens no CarPlay session or microphone. */
extern void *dlopen(const char *,int);
extern void *dlsym(void *,const char *);
extern void *calloc(unsigned,unsigned);
extern void free(void *);
extern int printf(const char *,...);
extern int fflush(void *);
extern int usleep(unsigned);
extern int memcmp(const void *,const void *,unsigned);
#include "call_mic.h"
#define CHECK(condition) do { if(!(condition)){printf("FAIL SDK mic check at line %d\n",__LINE__);fflush(0);return 1;} }while(0)
static int add_packet(void *library,void *jitter,unsigned timestamp,unsigned char *pcm,unsigned bytes) {
    int (*get)(void *,void **)=dlsym(library,"RTPJitterBufferGetFreeNode");
    int (*put)(void *,void *)=dlsym(library,"RTPJitterBufferPutBusyNode");
    void *node=0;
    if(!get || !put || get(jitter,&node) || !node)return -1;
    *(unsigned *)((unsigned char *)node+0xc)=timestamp;
    *(unsigned *)((unsigned char *)node+0x5c8)=bytes;
    *(void **)((unsigned char *)node+0x5d0)=pcm;
    return put(jitter,node);
}
int sdk_mic_check(void) {
    void *library=dlopen("libSdBridge.so",2);CHECK(library);
    int (*format)(unsigned long long,void *,void *)=dlsym(library,"AirPlayAudioFormatToASBD");
    int (*init)(void *,void *,void *,unsigned)=dlsym(library,"RTPJitterBufferInit");
    void (*release)(void *)=dlsym(library,"RTPJitterBufferFree");
    int (*legacy)(void *,void *,unsigned)=dlsym(library,"carplay_reverse_audio_pull_record_frame");
    struct call_mic_api api={dlsym(library,"GetRTPJitterBufferPreparedLen"),dlsym(library,"RTPJitterBufferRead")};
    CHECK(format && init && release && legacy && api.prepared_frames && api.read_bytes);
    unsigned asbd[9]={0};CHECK(!format(0x10,asbd,0));
    unsigned char pcm[322],output[324];for(unsigned i=0;i<sizeof(pcm);i++)pcm[i]=(unsigned char)(i*37+11);
    unsigned char *stream=calloc(1,0x478);CHECK(stream);void *jitter=stream+0x110;
    CHECK(!init(jitter,asbd,asbd,20));CHECK(*(unsigned *)((unsigned char *)jitter+0x7c)==2);
    CHECK(!add_packet(library,jitter,1000,pcm,sizeof(pcm)));
    for(int i=0;i<100 && api.prepared_frames(jitter)!=161;i++)usleep(1000);
    CHECK(api.prepared_frames(jitter)==161);usleep(30000);
    int n=legacy(stream,output,320);printf("Pinned legacy wrapper returned %d bytes for 161 PCM frames\n",n);CHECK(n==161);
    CHECK(call_mic_pull(&api,stream,output,320)==162);CHECK(!memcmp(output,pcm+160,162));
    CHECK(call_mic_pull(&api,stream,output,320)==0);
    CHECK(!add_packet(library,jitter,1161,pcm,sizeof(pcm)));
    for(int i=0;i<100 && api.prepared_frames(jitter)!=161;i++)usleep(1000);
    CHECK(api.prepared_frames(jitter)==161);
    CHECK(call_mic_pull(&api,stream,output,320)==320);CHECK(!memcmp(output,pcm,320));
    CHECK(call_mic_pull(&api,stream,output,320)==2);CHECK(!memcmp(output,pcm+320,2));
    CHECK(call_mic_pull(&api,stream,output,320)==0);
    release(jitter);free(stream);
    printf("PASS: real SDK frame/byte mismatch reproduced and corrected; synthetic PCM only\n");fflush(0);return 0;
}
__asm__(".global _start\n.type _start,%function\n_start:\nbl sdk_mic_check\nmov r7,#248\nsvc #0\n");
