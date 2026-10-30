#include <assert.h>
#include <stdio.h>
#include <string.h>
#include "call_playback.h"
static unsigned packets;static unsigned char last[320];
static void push(void *audio,void *p,unsigned n){assert(audio==(void *)42 && n==320);memcpy(last,p,n);packets++;}
int main(void) {
    struct call_playback q={0};unsigned char pcm[960];
    for(unsigned i=0;i<sizeof(pcm);i++)pcm[i]=(unsigned char)i;
    call_playback_put(&q,pcm,240);call_playback_tick(&q,100,(void *)42,push);assert(!packets);
    call_playback_put(&q,pcm+240,240);call_playback_tick(&q,108,(void *)42,push);assert(!packets);
    call_playback_put(&q,pcm+480,240);call_playback_tick(&q,115,(void *)42,push);
    assert(packets==1 && !memcmp(last,pcm,320) && q.used==400);
    call_playback_tick(&q,115,(void *)42,push);call_playback_tick(&q,124,(void *)42,push);assert(packets==1);
    call_playback_tick(&q,125,(void *)42,push);assert(packets==2 && !memcmp(last,pcm+320,320));
    call_playback_tick(&q,135,(void *)42,push);assert(packets==3 && q.underruns==1 && !q.used);
    assert(!memcmp(last,pcm+640,80));for(unsigned i=80;i<320;i++)assert(!last[i]);
    call_playback_put(&q,pcm,960);call_playback_tick(&q,1000,(void *)42,push);assert(packets==4);
    call_playback_tick(&q,1000,(void *)42,push);assert(packets==4);
    call_playback_put(&q,pcm,960);call_playback_put(&q,pcm,960);assert(q.used==1920 && q.dropped==640);
    q.next=0xfffffff8u;call_playback_tick(&q,0xfffffff8u,(void *)42,push);assert(packets==5);
    call_playback_tick(&q,1,(void *)42,push);assert(packets==5);
    call_playback_tick(&q,2,(void *)42,push);assert(packets==6);
    puts("PASS: bounded voice jitter buffer, 10 ms pacing, PCM continuity, underrun silence, no catch-up bursts and clock wrap");
}
