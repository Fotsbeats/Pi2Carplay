#define PI2CALL_HOST_TEST
#include "call_hci.c"
#include <assert.h>
#include <math.h>
static unsigned char decoded[240*40];static size_t decoded_used;
static void collect_pcm(const unsigned char *p,size_t n,void *unused){(void)unused;assert(decoded_used+n<=sizeof(decoded));memcpy(decoded+decoded_used,p,n);decoded_used+=n;}
static void test_msbc(void) {
    struct call_msbc codec={0};struct call_pcm q={0};unsigned char frames[60*20];
    assert(!call_msbc_start(&codec));decoded_used=0;
    for(unsigned frame=0;frame<20;frame++) {
        unsigned char pcm[240];for(unsigned i=0;i<120;i++){int16_t v=(int16_t)(sin(i*3.141592653589793/2)*6000);pcm[2*i]=(unsigned char)v;pcm[2*i+1]=(unsigned char)(v>>8);}
        call_pcm_put(&q,pcm,sizeof(pcm));
        assert(!call_msbc_transmit(&codec,&q,frames+frame*60,17));
        assert(!call_msbc_transmit(&codec,&q,frames+frame*60+17,43));
    }
    for(unsigned at=0;at<sizeof(frames);){unsigned n=23;if(at+n>sizeof(frames))n=sizeof(frames)-at;assert(!call_msbc_receive(&codec,frames+at,n,0,collect_pcm,0));at+=n;}
    assert(decoded_used==20*240 && !codec.lost && !codec.underruns);
    double energy=0,sine=0,cosine=0;for(unsigned i=240;i<decoded_used/2;i++){int16_t v=(int16_t)(decoded[2*i]|decoded[2*i+1]<<8);energy+=(double)v*v;sine+=v*sin(i*3.141592653589793/2);cosine+=v*cos(i*3.141592653589793/2);}
    assert(energy>1e9 && 2*(sine*sine+cosine*cosine)/(decoded_used/2-240)/energy>0.8);
    /* Duplicate H2 packets do not invent three missing frames. */
    assert(!call_msbc_receive(&codec,frames+19*60,60,0,collect_pcm,0));assert(decoded_used==20*240);
    call_msbc_finish(&codec);assert(!call_msbc_start(&codec));decoded_used=0;
    assert(!call_msbc_receive(&codec,frames,60,0,collect_pcm,0));
    assert(!call_msbc_receive(&codec,frames+120,60,0,collect_pcm,0));assert(codec.lost==1 && decoded_used==720);
    unsigned char junk[5]={0};assert(!call_msbc_receive(&codec,junk,5,1,collect_pcm,0));
    assert(!call_msbc_receive(&codec,frames+180,60,0,collect_pcm,0));assert(decoded_used==960);
    q.used=0;assert(!call_msbc_transmit(&codec,&q,junk,5) && codec.underruns==1);
    call_msbc_finish(&codec);puts("PASS: mSBC 4 kHz tone, split H2 frames, sequence gaps, duplicate rejection, resynchronization and underrun silence");
}
int main(void) {
    test_msbc();
    struct call_pcm q={0};unsigned char pcm[4000],out[2000];
    for(unsigned i=0;i<sizeof(pcm);i++)pcm[i]=(unsigned char)i;
    call_pcm_put(&q,pcm,sizeof(pcm));assert(q.used==1920);
    assert(call_pcm_take(&q,out,2000)==1920 && !q.used);
    assert(!memcmp(out,pcm+2080,1920));for(int i=1920;i<2000;i++)assert(!out[i]);
    int fds[2];assert(!socketpair(AF_UNIX,SOCK_SEQPACKET,0,fds));hci_fd=fds[0];
    unsigned char version[]={1,1,0x10,0},got[300];
    struct iovec init_iov[]={{version,1},{version+1,3}};
    assert(writev(hci_fd,init_iov,2)==4 && !routing);
    int n=(int)syscall(SYS_read,fds[1],got,sizeof(got));assert(n==4 && !memcmp(got,version,4));
    unsigned char cmd[]={1,0x29,4,21,1,2,3,4,5,6,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0};
    assert(call_hci_length(cmd,sizeof(cmd))==sizeof(cmd));
    target_valid=1;memcpy(target,cmd+4,6);assert(target_command(cmd,sizeof(cmd)));
    target[0]=9;assert(!target_command(cmd,sizeof(cmd)));target[0]=1;
    unsigned char connection[]={4,3,11,0,33,0,1,2,3,4,5,6,1,0};size_t event_size=sizeof(connection);
    incoming(connection,&event_size);assert(acl==33);
    connection[4]=34;incoming(connection,&event_size);assert(acl==34 && peers[0].handle==-1);
    unsigned char setup[]={1,0x28,4,17,34,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0};
    assert(target_command(setup,sizeof(setup)));setup[4]=33;assert(!target_command(setup,sizeof(setup)));
    unsigned char disconnected[]={4,5,4,0,34,0,0x13};event_size=sizeof(disconnected);incoming(disconnected,&event_size);assert(acl==-1);
    struct iovec iov[]={{cmd,1},{cmd+1,sizeof(cmd)-1}};
    assert(writev(hci_fd,iov,2)==sizeof(cmd));
    n=(int)syscall(SYS_read,fds[1],got,sizeof(got));
    assert(n==9 && got[1]==0x1c && got[4]==1 && routing);
    unsigned char ack[20]={4,0x0e,4,1,0x1c,0xfc,0};size_t bytes=7;
    incoming(ack,&bytes);assert(bytes==4 && ack[1]==0x13 && !routing);
    n=(int)syscall(SYS_read,fds[1],got,sizeof(got));assert(n==sizeof(cmd) && !memcmp(cmd,got,n));
    unsigned char enhanced[63]={1,0x3d,4,59};enhanced[4]=34;acl=34;enhanced[4+10]=5;enhanced[4+50]=enhanced[4+51]=1;
    struct iovec enhanced_iov[]={{enhanced,1},{enhanced+1,62}};assert(writev(hci_fd,enhanced_iov,2)==63);
    assert(syscall(SYS_read,fds[1],got,sizeof(got))==9);unsigned char ok[]={4,0x0e,4,1,0x1c,0xfc,0};bytes=7;incoming(ok,&bytes);
    assert(syscall(SYS_read,fds[1],got,sizeof(got))==63);
    assert(got[4+10]==3 && got[4+15]==3 && got[4+32]==3 && got[4+37]==3 && got[4+50]==0 && got[4+51]==0);
    assert(got[4+24]==0x40 && got[4+25]==0x1f && got[4+28]==0x40 && got[4+29]==0x1f);
    active=13;received=transmitted=0;unsigned char sco[12]={3,13,0,8,1,2,3,4,5,6,7,8};bytes=12;
    call_pcm_put(&mic,pcm,8);incoming(sco,&bytes);assert(bytes==4 && received==1 && transmitted==1);
    n=(int)syscall(SYS_read,fds[1],got,sizeof(got));assert(n==12 && !memcmp(got+4,pcm,8));
    unsigned char bad[]={3,13,0,8,1};bytes=sizeof(bad);incoming(bad,&bytes);assert(bytes==sizeof(bad) && received==1);
    end_audio();assert(active==-1 && !mic.used);
    close(fds[0]);close(fds[1]);puts("PASS: bounded PCM, silence, HCI validation, target isolation, command credits, controller routing and duplex SCO");
}
