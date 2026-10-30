#include <assert.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
struct bridge_poll { int fd;short events,revents; };
static int mode,turn,starts,stops,media_packets,call_packets,mic_packets,errors,start_sent,stop_sent,pcm_sent;
static int handles[4][0x478/4];
struct probe_time { long sec,nsec; };
static int clock_gettime(int clock,struct probe_time *now){assert(clock==1);now->sec=1;now->nsec=turn*10000000;return 0;}
static void print(const char *s){(void)s;}
static int fsync(int fd){(void)fd;return 0;}
static int usleep(unsigned n){(void)n;return 0;}
static char *mock_getenv(const char *s){return !strcmp(s,"AIBOX_CALLING")?"1":"pipe";}
static int mock_open(const char *s,int flags){assert(!strcmp(s,"pipe") && flags==2048);return 10;}
static int mock_close(int fd){assert(fd==10 || fd==20 || fd==21);return 0;}
static int mock_read(int fd,void *p,unsigned n){assert(fd==10 && n<=960);memset(p,1,n);return (int)n;}
static int mock_poll(struct bridge_poll *,unsigned,int);
static void *mock_start(int type,int record,int latency,int rate,int bits,int channels){
 assert(starts<4 && !latency && bits==16);
 if(starts==1){assert(type==1 && record==1 && rate==16000 && channels==1);starts++;if(mode==1)return NULL;return handles[1];}
 assert(type==0 && !record && rate==48000 && channels==2);return handles[starts++];
}
static void mock_stop(void *s){assert(s);stops++;}
static void mock_push(void *s,void *p,unsigned n){assert(p);if(s==handles[1]){assert(n==320);call_packets++;}else{assert(n==960);media_packets++;}}
static int mock_frames(void *s){assert(s==(unsigned char *)handles[1]+0x110);return mode==2?-1:160;}
static int mock_read_bytes(void *s,void *p,unsigned n){assert(s==(unsigned char *)handles[1]+0x110 && n==320);memset(p,1,n);return 0;}
static void *mock_dlsym(void *library,const char *name){
 assert(library==(void *)42);
 if(!strcmp(name,"carplay_reverse_audio_start"))return mock_start;
 if(!strcmp(name,"carplay_reverse_audio_stop"))return mock_stop;
 if(!strcmp(name,"carplay_reverse_audio_push_frame"))return mock_push;
 if(!strcmp(name,"GetRTPJitterBufferPreparedLen"))return mock_frames;
 if(!strcmp(name,"RTPJitterBufferRead"))return mock_read_bytes;
 return NULL;
}
static int mock_socket(int a,int b,int c){assert(a==1 && (b&5)==5 && !c);return 20;}
static int mock_bind(int fd,const void *p,unsigned n){assert(fd==20 && p && n==110);return 0;}
static int mock_listen(int fd,int n){assert(fd==20 && n==1);return 0;}
static int mock_accept4(int fd,void *a,void *b,int flags){assert(fd==20);(void)a;(void)b;(void)flags;return 21;}
static int mock_getsockopt(int fd,int a,int b,void *p,unsigned *n){assert(fd==21 && a==1 && b==17 && *n==12);memset(p,0,12);return 0;}
static int mock_send(int fd,const void *p,unsigned n,int flags){assert(fd==21 && flags==0x4040);if(((unsigned char *)p)[0]==2)mic_packets++;if(((unsigned char *)p)[0]==5)errors++;return (int)n;}
static int mock_recv(int fd,void *p,unsigned n,int flags){
 assert(fd==21 && n==965 && flags==0x40);unsigned char *f=p;memset(f,0,n);
 if(!start_sent){start_sent=1;f[0]=1;return 4;}
 if(turn==1 && pcm_sent<2){pcm_sent++;f[0]=2;f[2]=64;f[3]=1;return 324;}
 if(turn>=2 && !stop_sent){stop_sent=1;f[0]=3;return 4;}
 return -1;
}
static int mock_unlink(const char *p){(void)p;return 0;}
#define getenv mock_getenv
#define open mock_open
#define close mock_close
#define read mock_read
#define poll mock_poll
#define dlsym mock_dlsym
#define socket mock_socket
#define bind mock_bind
#define listen mock_listen
#define accept4 mock_accept4
#define getsockopt mock_getsockopt
#define send mock_send
#define recv mock_recv
#define unlink mock_unlink
#include "audio.h"
static int mock_poll(struct bridge_poll *p,unsigned n,int timeout){assert(p->fd==10 && n==1 && timeout>0);if(turn++==3){audio_running=0;return 0;}return 1;}
int main(void){
 for(mode=0;mode<4;mode++){
  turn=starts=stops=media_packets=call_packets=mic_packets=errors=start_sent=stop_sent=pcm_sent=0;
  for(int i=0;i<4;i++){handles[i][0x36]=123;handles[i][(0x110+0x7c)/4]=2;}
  if(mode==3)handles[1][0x36]=-1;
  audio_running=1;audio_sender((void *)42);
  assert(starts==3 && media_packets>0);
  if(!mode)assert(stops==3 && call_packets==1 && mic_packets==2 && !errors);
  else assert(stops==(mode==1?2:3) && !call_packets && !mic_packets && errors==1);
 }
 puts("PASS: media-to-call-to-media lifecycle, duplex PCM and rejected-call recovery");
}
