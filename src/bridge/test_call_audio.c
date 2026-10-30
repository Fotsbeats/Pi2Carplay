#include <assert.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
static int next_size=-1,closed,pushed,sent,uid;
static unsigned char next_frame[1000];
static char *test_getenv(const char *name){(void)name;return "1";}
static int test_socket(int a,int b,int c){assert(a==1 && (b&5)==5 && !c);return 10;}
static int test_bind(int fd,const void *p,unsigned n){assert(fd==10 && p && n==110);return 0;}
static int test_listen(int fd,int n){assert(fd==10 && n==1);return 0;}
static int test_accept4(int fd,void *a,void *b,int flags){(void)fd;(void)a;(void)b;(void)flags;return 11;}
static int test_getsockopt(int fd,int a,int b,void *p,unsigned *size){(void)fd;assert(a==1 && b==17 && *size==12);((unsigned *)p)[1]=(unsigned)uid;return 0;}
static int test_send(int fd,const void *p,unsigned n,int flags){(void)p;assert(fd==11 && flags==0x4040);sent++;return (int)n;}
static int test_recv(int fd,void *p,unsigned n,int flags){assert(fd==11 && n==965 && flags==0x40);int result=next_size;if(result>=0)memcpy(p,next_frame,(unsigned)result);next_size=-1;return result;}
static int test_close(int fd){assert(fd>=0);closed++;return 0;}
static int test_unlink(const char *p){assert(strstr(p,"carplay.sock"));return 0;}
static void print(const char *p){(void)p;}
#define getenv test_getenv
#define socket test_socket
#define bind test_bind
#define listen test_listen
#define accept4 test_accept4
#define getsockopt test_getsockopt
#define send test_send
#define recv test_recv
#define close test_close
#define unlink test_unlink
#include "call_audio.h"
static void push(void *audio,void *p,unsigned n){assert(audio==(void *)42 && p && n==320);pushed++;}
static void frame(int type,int size){memset(next_frame,0,sizeof(next_frame));next_frame[0]=(unsigned char)type;next_frame[2]=(unsigned char)size;next_frame[3]=(unsigned char)(size>>8);next_size=size+4;}
int main(void){
 struct call_audio call;call_open(&call);assert(call.server==10 && call.peer==-1);
 uid=2000;call_step(&call,0,0);assert(call.peer==-1 && closed==1);
 uid=0;frame(1,0);call_step(&call,0,0);assert(call.peer==11 && call.requested);
 frame(2,320);call_step(&call,(void *)42,0);assert(!pushed);
 frame(2,320);call_step(&call,(void *)42,1);assert(!pushed && call.playback.used==320);
 frame(2,320);call_step(&call,(void *)42,1);call_playback_tick(&call.playback,100,(void *)42,push);assert(pushed==1);
 call_error(&call,"test microphone read failure");
 frame(3,0);call_step(&call,(void *)42,1);assert(!call.requested);
 frame(1,0);next_frame[2]=1;call_step(&call,0,0);assert(call.peer==-1 && !call.requested);
 frame(2,321);call_step(&call,(void *)42,1);assert(call.peer==-1 && !call.requested);
 assert(call_send(&call,2,next_frame,962)==-1);call_close(&call);assert(call.server==-1);
 puts("PASS: root-only call transport, frame bounds, start/stop, PCM gating and malformed-peer recovery");
}
