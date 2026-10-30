/* HFP mSBC: 120 PCM16 samples / 57 encoded bytes, carried in 60-byte H2 frames. */
#include <sbc.h>
struct call_msbc {
    sbc_t encoder,decoder;
    int initialized,last_sequence;
    unsigned sequence;
    unsigned char rx[360],tx[60];
    size_t rx_used,tx_used;
    unsigned lost,underruns;
};
static const unsigned char call_h2_sequence[]={0x08,0x38,0xc8,0xf8};
static void call_msbc_finish(struct call_msbc *s) {
    if(s->initialized){sbc_finish(&s->encoder);sbc_finish(&s->decoder);}s->initialized=0;
}
static int call_msbc_start(struct call_msbc *s) {
    call_msbc_finish(s);memset(s,0,sizeof(*s));s->last_sequence=-1;
    if(sbc_init_msbc(&s->encoder,0))return -1;
    if(sbc_init_msbc(&s->decoder,0)){sbc_finish(&s->encoder);return -1;}
    s->encoder.endian=s->decoder.endian=SBC_LE;s->initialized=1;return 0;
}
static int call_msbc_receive(struct call_msbc *s,const unsigned char *p,size_t n,int bad,
        void (*emit)(const unsigned char *,size_t,void *),void *context) {
    if(!s->initialized || n>255 || s->rx_used+n>sizeof(s->rx))return -1;
    if(bad)memset(s->rx+s->rx_used,0,n);else memcpy(s->rx+s->rx_used,p,n);
    s->rx_used+=n;
    while(s->rx_used>=60) {
        int sequence=-1;
        if(s->rx[0]==1 && s->rx[2]==0xad)for(int i=0;i<4;i++)if(s->rx[1]==call_h2_sequence[i])sequence=i;
        if(sequence<0){memmove(s->rx,s->rx+1,--s->rx_used);continue;}
        if(sequence==s->last_sequence){memmove(s->rx,s->rx+60,s->rx_used-60);s->rx_used-=60;continue;}
        unsigned char pcm[240]={0};size_t written=0;
        if(s->last_sequence>=0) {
            unsigned missing=(unsigned)(sequence-s->last_sequence-1)&3;
            for(unsigned i=0;i<missing;i++){emit(pcm,sizeof(pcm),context);s->lost++;}
        }
        if(sbc_decode(&s->decoder,s->rx+2,57,pcm,sizeof(pcm),&written)!=57 || written!=240){memset(pcm,0,sizeof(pcm));s->lost++;}
        emit(pcm,sizeof(pcm),context);s->last_sequence=sequence;
        memmove(s->rx,s->rx+60,s->rx_used-60);s->rx_used-=60;
    }
    return 0;
}
static int call_msbc_transmit(struct call_msbc *s,struct call_pcm *mic,unsigned char *p,size_t n) {
    if(!s->initialized || n>255)return -1;
    while(n) {
        if(!s->tx_used) {
            unsigned char pcm[240];ssize_t written=0;
            if(call_pcm_take(mic,pcm,sizeof(pcm))!=sizeof(pcm))s->underruns++;
            s->tx[0]=1;s->tx[1]=call_h2_sequence[s->sequence++&3];s->tx[59]=0;
            if(sbc_encode(&s->encoder,pcm,sizeof(pcm),s->tx+2,57,&written)!=240 || written!=57)return -1;
            s->tx_used=60;
        }
        size_t take=n<s->tx_used?n:s->tx_used;memcpy(p,s->tx,take);p+=take;n-=take;
        memmove(s->tx,s->tx+take,s->tx_used-take);s->tx_used-=take;
    }
    return 0;
}
