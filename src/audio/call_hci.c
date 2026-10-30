/* Pi H4 controller transport: retain Android HFP signalling, handle CVSD/mSBC in software. */
#define _GNU_SOURCE
#include <errno.h>
#include <fcntl.h>
#include <pthread.h>
#include <stdarg.h>
#include <stdio.h>
#include <stdlib.h>
#include <stdatomic.h>
#include <sys/socket.h>
#include <sys/stat.h>
#include <sys/syscall.h>
#include <sys/uio.h>
#include <sys/un.h>
#include <time.h>
#include <unistd.h>
#include "call_pcm.h"
#include "call_msbc.h"
#ifdef __ANDROID__
#include <android/log.h>
#define LOG(...) __android_log_print(ANDROID_LOG_INFO,"Pi2CallHci",__VA_ARGS__)
#else
#define LOG(...) fprintf(stderr,__VA_ARGS__)
#endif
#define CALL_SOCKET "/data/vendor/pi2call/hci.sock"
static pthread_mutex_t lock=PTHREAD_MUTEX_INITIALIZER;
static _Atomic int hci_fd=-1;
static int client=-1, target_valid, active=-1, acl=-1, routing;
static unsigned char target[6], pending[260];
static size_t pending_size;
static struct call_pcm mic;
static struct call_msbc msbc;
static int wideband;
static unsigned long received, transmitted, underruns, drops;
static struct { int handle; unsigned char address[6]; } peers[32];
static unsigned peer_next;
static long raw_write(int fd,const void *p,size_t n) { return send(fd,p,n,MSG_DONTWAIT|MSG_NOSIGNAL); }
static void send_frame(int type,const void *p,size_t n) {
    unsigned char frame[1024];
    if(client<0 || n>sizeof(frame)-4) return;
    frame[0]=(unsigned char)type;frame[1]=0;frame[2]=(unsigned char)n;frame[3]=(unsigned char)(n>>8);
    if(n)memcpy(frame+4,p,n);
    if(send(client,frame,n+4,MSG_DONTWAIT|MSG_NOSIGNAL)!=(ssize_t)(n+4))drops++;
}
static void end_audio(void) {
    if(active>=0) {
        LOG("SCO ended: rx=%lu tx=%lu underruns=%lu drops=%lu",received,transmitted,underruns,drops);
        unsigned stats[]={(unsigned)received,(unsigned)transmitted,(unsigned)underruns,(unsigned)drops,msbc.lost};
        send_frame(3,stats,sizeof(stats));
    }
    active=-1;mic.used=0;call_msbc_finish(&msbc);wideband=0;
}
static void decoded_pcm(const unsigned char *pcm,size_t n,void *unused){(void)unused;send_frame(2,pcm,n);}
static int target_command(const unsigned char *p,size_t n) {
    if(!target_valid || n<6 || p[0]!=1) return 0;
    unsigned op=p[1]+256u*p[2];
    if(op==0x0429 || op==0x043e) return n>=10 && !memcmp(p+4,target,6);
    if(op==0x0428 || op==0x043d) return acl>=0 && (p[4]+256u*p[5])==(unsigned)acl;
    return 0;
}
static void replace_with_credit_event(unsigned char *p,size_t *n) {
    /* A valid zero-handle completion keeps consumed SCO/vendor events out of Android's PCM path. */
    const unsigned char empty[]={4,0x13,1,0};memcpy(p,empty,sizeof(empty));*n=sizeof(empty);
}
static void incoming(unsigned char *p,size_t *n) {
    if(call_hci_length(p,*n)!=(int)*n) return;
    if(p[0]==4 && p[1]==0x0e && *n>=7 && p[4]==0x1c && p[5]==0xfc && routing) {
        int status=p[6];
        if(!status && raw_write(hci_fd,pending,pending_size)==(long)pending_size) {
            LOG("Controller SCO transport enabled; command forwarded");
            replace_with_credit_event(p,n);
        } else {
            LOG("Controller SCO transport rejected: status=%d errno=%d",status,errno);
            unsigned char failed[]={4,0x0f,4,0x1f,1,pending[1],pending[2]};
            memcpy(p,failed,sizeof(failed));*n=sizeof(failed);
        }
        routing=0;pending_size=0;return;
    }
    if(p[0]==4 && p[1]==3 && *n>=14 && !p[3] && p[12]==1) {
        int handle=p[4]+256*p[5];
        for(unsigned i=0;i<32;i++)if(peers[i].handle==handle || !memcmp(peers[i].address,p+6,6))peers[i].handle=-1;
        unsigned slot=peer_next++%32;peers[slot].handle=p[4]+256*p[5];memcpy(peers[slot].address,p+6,6);
        if(target_valid && !memcmp(target,p+6,6))acl=peers[slot].handle;
    }
    if(p[0]==4 && p[1]==0x2c && *n==20 && !p[3] && target_valid && !memcmp(target,p+6,6)) {
        if(p[19]!=2 && p[19]!=3) { LOG("Unsupported SCO air mode=%u; no PCM forwarded",p[19]);send_frame(5,0,0);return; }
        end_audio();active=p[4]+256*p[5];mic.used=0;received=transmitted=underruns=drops=0;
        wideband=p[19]==3;
        if(wideband && call_msbc_start(&msbc)){active=-1;send_frame(5,0,0);return;}
        unsigned rate=wideband?16000:8000;send_frame(1,&rate,4);LOG("SCO active: codec=%s rate=%u",wideband?"mSBC":"CVSD",rate);
    }
    if(p[0]==4 && p[1]==5 && *n==7 && !p[3]) {
        int handle=p[4]+256*p[5];
        if(handle==active)end_audio();
        if(handle==acl)acl=-1;
        for(unsigned i=0;i<32;i++)if(peers[i].handle==handle)peers[i].handle=-1;
    }
    if(p[0]==3 && *n>=4 && active>=0 && (p[1]+256*(p[2]&15))==active) {
        unsigned bytes=p[3];
        if(bytes && (wideband || !(bytes&1))) {
            unsigned char output[259]={3,(unsigned char)active,(unsigned char)(active>>8),(unsigned char)bytes};
            /* Errored incoming packets become silence, preserving the controller's clock. */
            if(wideband) {
                if(call_msbc_receive(&msbc,p+4,bytes,p[2]&0x30,decoded_pcm,0) || call_msbc_transmit(&msbc,&mic,output+4,bytes)){send_frame(5,0,0);drops++;}
                underruns=msbc.underruns;
            } else {
                if(p[2]&0x30)memset(p+4,0,bytes);
                send_frame(2,p+4,bytes);
                if(call_pcm_take(&mic,output+4,bytes)!=bytes)underruns++;
            }
            received++;
            if(raw_write(hci_fd,output,bytes+4)==(long)(bytes+4))transmitted++;else drops++;
        }
        replace_with_credit_event(p,n);
    }
}
int bind(int fd,const struct sockaddr *address,socklen_t size) {
    int result=(int)syscall(SYS_bind,fd,address,size);
    if(!result && size>=6 && address->sa_family==31) {
        unsigned short channel;memcpy(&channel,(const unsigned char *)address+4,2);
        if(channel==1) {
            pthread_mutex_lock(&lock);hci_fd=fd;acl=-1;end_audio();routing=0;peer_next=0;
            for(unsigned i=0;i<32;i++)peers[i].handle=-1;
            unsigned char state[]={1,1};send_frame(8,state,2);pthread_mutex_unlock(&lock);LOG("HCI user transport attached");
        }
    }
    return result;
}
ssize_t read(int fd,void *buffer,size_t capacity) {
    ssize_t got=syscall(SYS_read,fd,buffer,capacity);
    if(got>0 && fd==hci_fd && capacity>=7) {
        size_t n=(size_t)got;pthread_mutex_lock(&lock);incoming(buffer,&n);pthread_mutex_unlock(&lock);got=(ssize_t)n;
    }
    return got;
}
ssize_t writev(int fd,const struct iovec *iov,int count) {
    if(fd!=hci_fd || count!=2 || iov[0].iov_len!=1 || iov[1].iov_len>259)
        return syscall(SYS_writev,fd,iov,count);
    unsigned char p[260];size_t n=1+iov[1].iov_len;
    memcpy(p,iov[0].iov_base,1);memcpy(p+1,iov[1].iov_base,iov[1].iov_len);
    pthread_mutex_lock(&lock);
    if(call_hci_length(p,n)==(int)n && target_command(p,n)) {
        unsigned op=p[1]+256u*p[2];
        /* Enhanced HCI commands explicitly name both controller data paths. */
        if(op==0x043d && n==63) { p[4+50]=0;p[4+51]=0; }
        if(op==0x043e && n==67) { p[4+54]=0;p[4+55]=0; }
        if((op==0x043d && n==63) || (op==0x043e && n==67)) {
            unsigned shift=op==0x043e?4:0;
            if(p[4+10+shift]==5 || p[4+10+shift]==3) {
                /* mSBC is encoded in software: every controller coding field is transparent. */
                for(unsigned i=0;i<4;i++)p[4+(unsigned[]){10,15,32,37}[i]+shift]=3;
                for(unsigned i=0;i<2;i++){unsigned at=4+24+shift+i*4;p[at]=0x40;p[at+1]=0x1f;p[at+2]=p[at+3]=0;}
            }
        }
        if(!routing) {
            const unsigned char route[]={1,0x1c,0xfc,5,1,0,0,0,0};
            memcpy(pending,p,n);pending_size=n;
            if(raw_write(fd,route,sizeof(route))==(long)sizeof(route)) { routing=1;pthread_mutex_unlock(&lock);return (ssize_t)n; }
            pending_size=0;pthread_mutex_unlock(&lock);return -1;
        }
        pthread_mutex_unlock(&lock);errno=EBUSY;return -1;
    }
    pthread_mutex_unlock(&lock);return syscall(SYS_writev,fd,iov,count);
}
int close(int fd) {
    if(fd!=hci_fd)return (int)syscall(SYS_close,fd);
    pthread_mutex_lock(&lock);
    if(fd==hci_fd) { end_audio();hci_fd=-1;acl=-1;routing=0;unsigned char state[]={1,0};send_frame(8,state,2); }
    pthread_mutex_unlock(&lock);return (int)syscall(SYS_close,fd);
}
#ifndef PI2CALL_HOST_TEST
static void *serve(void *unused) {
    (void)unused;
    int server=socket(AF_UNIX,SOCK_SEQPACKET|SOCK_CLOEXEC,0);
    struct sockaddr_un address={.sun_family=AF_UNIX};strcpy(address.sun_path,CALL_SOCKET);
    struct stat previous;
    if(!lstat(CALL_SOCKET,&previous) && S_ISSOCK(previous.st_mode) && previous.st_uid==geteuid())unlink(CALL_SOCKET);
    if(server<0 || bind(server,(void *)&address,sizeof(address)) || listen(server,1)) { LOG("Call socket unavailable: %d",errno);if(server>=0)close(server);return 0; }
    for(;;) {
        int peer=accept4(server,0,0,SOCK_CLOEXEC);
        if(peer<0) { if(errno==EINTR)continue;break; }
        struct ucred cred;socklen_t size=sizeof(cred);
        if(getsockopt(peer,SOL_SOCKET,SO_PEERCRED,&cred,&size) || cred.uid!=0) { close(peer);continue; }
        pthread_mutex_lock(&lock);client=peer;target_valid=0;mic.used=0;
        unsigned char state[]={1,(unsigned char)(hci_fd>=0)};send_frame(8,state,2);
        pthread_mutex_unlock(&lock);
        unsigned char frame[1024];ssize_t n;
        while((n=recv(peer,frame,sizeof(frame),0))>0) {
            if(n<4 || frame[1] || frame[2]+256*frame[3]!=n-4)break;
            pthread_mutex_lock(&lock);
            if(frame[0]==0 && n==10) {
                if(!target_valid || memcmp(target,frame+4,6)) {
                    end_audio();memcpy(target,frame+4,6);target_valid=1;acl=-1;
                    for(unsigned i=0;i<32;i++)if(peers[i].handle>=0 && !memcmp(peers[i].address,target,6))acl=peers[i].handle;
                }
            } else if(frame[0]==4 && active>=0 && !((n-4)&1))call_pcm_put(&mic,frame+4,(size_t)n-4);
            else if(frame[0]==6 && n==4) { target_valid=0;end_audio();acl=-1; }
            else if(frame[0]==7 && n==4 && active>=0) { unsigned rate=wideband?16000:8000;send_frame(1,&rate,4); }
            pthread_mutex_unlock(&lock);
        }
        pthread_mutex_lock(&lock);client=-1;target_valid=0;mic.used=0;pthread_mutex_unlock(&lock);close(peer);
    }
    close(server);return 0;
}
__attribute__((constructor)) static void initialize(void) {
    for(unsigned i=0;i<32;i++)peers[i].handle=-1;
    pthread_t thread;if(!pthread_create(&thread,0,serve,0))pthread_detach(thread);
}
#endif
