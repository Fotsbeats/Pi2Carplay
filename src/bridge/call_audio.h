/* Optional live duplex CarPlay telephony transport. No microphone samples are retained. */
extern int socket(int,int,int);
extern int bind(int,const void *,unsigned);
extern int listen(int,int);
extern int accept4(int,void *,void *,int);
extern int getsockopt(int,int,int,void *,unsigned *);
extern int send(int,const void *,unsigned,int);
extern int recv(int,void *,unsigned,int);
extern int unlink(const char *);
struct call_address { unsigned short family;char path[108]; };
struct call_credentials { int pid;unsigned uid,gid; };
#include "call_playback.h"
struct call_audio {
    int server,peer,requested,failed;
    unsigned down,up,dropped,nonzero;
    struct call_playback playback;
};
#define CALL_AUDIO_SOCKET "/data/vendor/pi2call/carplay.sock"
static int call_send(struct call_audio *c,int type,const void *pcm,unsigned n) {
    unsigned char frame[964];
    if(c->peer<0 || n>960)return -1;
    frame[0]=(unsigned char)type;frame[1]=0;frame[2]=(unsigned char)n;frame[3]=(unsigned char)(n>>8);
    for(unsigned i=0;i<n;i++)frame[4+i]=((const unsigned char *)pcm)[i];
    if(send(c->peer,frame,n+4,0x4040)!=(int)n+4) { c->dropped++;return -1; }
    return 0;
}
static void call_error(struct call_audio *c,const char *message) {
    unsigned n=0;while(message[n] && n<128)n++;
    call_send(c,5,message,n);
}
static void call_open(struct call_audio *c) {
    c->server=c->peer=-1;c->requested=c->failed=0;c->up=c->down=c->dropped=c->nonzero=0;
    c->playback=(struct call_playback){0};
    const char *enabled=getenv("AIBOX_CALLING");
    if(!enabled || enabled[0]!='1')return;
    struct call_address address={1,CALL_AUDIO_SOCKET};
    unlink(CALL_AUDIO_SOCKET);
    c->server=socket(1,5|2048|524288,0);
    if(c->server<0 || bind(c->server,&address,sizeof(address)) || listen(c->server,1)) {
        if(c->server>=0)close(c->server);
        c->server=-1;print("ERROR: CarPlay call socket unavailable\n");
    }
}
static void call_disconnect(struct call_audio *c) {
    if(c->peer>=0)close(c->peer);
    c->peer=-1;c->requested=0;
    c->playback=(struct call_playback){0};
}
static void call_close(struct call_audio *c) {
    call_disconnect(c);
    if(c->server>=0) { close(c->server);unlink(CALL_AUDIO_SOCKET);c->server=-1; }
}
/* Drain a bounded amount each turn so call control cannot starve media or disconnect handling. */
static void call_step(struct call_audio *c,void *audio,int in_call) {
    if(c->server<0)return;
    if(c->peer<0) {
        int peer=accept4(c->server,0,0,2048|524288);
        if(peer>=0) {
            struct call_credentials cred;unsigned size=sizeof(cred);
            if(getsockopt(peer,1,17,&cred,&size) || cred.uid!=0)close(peer);
            else { c->peer=peer;c->requested=c->failed=0;call_send(c,4,0,0); }
        }
    }
    for(int i=0;i<16 && c->peer>=0;i++) {
        unsigned char frame[965];int n=recv(c->peer,frame,sizeof(frame),0x40);
        if(n<0)break;
        if(!n) { call_disconnect(c);break; }
        if(n<4 || frame[1] || (unsigned)(n-4)!=(unsigned)frame[2]+256u*frame[3] || n>964) { call_disconnect(c);break; }
        if(frame[0]==1 && n==4) { c->requested=1;c->failed=0;c->up=c->down=c->dropped=c->nonzero=0;c->playback=(struct call_playback){0};break; }
        if(frame[0]==3 && n==4) { c->requested=0;break; }
        if(frame[0]!=2 || ((n-4)&1)) { call_disconnect(c);break; }
        if(in_call && !c->failed && audio && n>4) { call_playback_put(&c->playback,frame+4,(unsigned)n-4);c->down++; }
    }
}
