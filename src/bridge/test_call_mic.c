#include <assert.h>
#include <stdio.h>
#include <string.h>
#include "call_mic.h"
static unsigned stream[0x478/4],available,reads;
static int status;
static int frames(void *jitter){assert(jitter==(unsigned char *)stream+0x110);return (int)available;}
static int read_bytes(void *jitter,void *pcm,unsigned bytes){assert(jitter==(unsigned char *)stream+0x110);assert(bytes<=320 && !(bytes&1));available-=bytes/2;memset(pcm,0x55,bytes);reads++;return status;}
int main(void){
 struct call_mic_api api={frames,read_bytes};unsigned char pcm[324];memset(pcm,0xcc,sizeof(pcm));stream[(0x110+0x7c)/4]=2;
 available=161;assert(call_mic_pull(&api,stream,pcm,320)==320 && available==1);
 assert(call_mic_pull(&api,stream,pcm,320)==2 && available==0);
 assert(call_mic_pull(&api,stream,pcm,320)==0 && reads==2);
 for(unsigned i=320;i<sizeof(pcm);i++)assert(pcm[i]==0xcc);
 assert(call_mic_pull(&api,stream,pcm,319)==-1);
 stream[(0x110+0x7c)/4]=4;assert(call_mic_pull(&api,stream,pcm,320)==-2);stream[(0x110+0x7c)/4]=2;
 available=(unsigned)-1;assert(call_mic_pull(&api,stream,pcm,320)==-3);
 available=10;status=-1;assert(call_mic_pull(&api,stream,pcm,320)==-4);
 puts("PASS: SDK frame counts become aligned PCM bytes, including odd frame counts, empty buffers, bounds and errors");
}
