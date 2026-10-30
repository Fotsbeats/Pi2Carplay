/* 16 kHz mono PCM16: absorb producer bursts, submit 10 ms on a monotonic clock. */
struct call_playback {
    unsigned char pcm[1920];
    unsigned used,started,next,packets,underruns,dropped;
};
static void call_playback_put(struct call_playback *q,const unsigned char *p,unsigned n) {
    if(q->used+n>sizeof(q->pcm)) {
        unsigned drop=q->used+n-sizeof(q->pcm);
        for(unsigned i=drop;i<q->used;i++)q->pcm[i-drop]=q->pcm[i];
        q->used-=drop;q->dropped+=drop;
    }
    for(unsigned i=0;i<n;i++)q->pcm[q->used++]=p[i];
}
static void call_playback_tick(struct call_playback *q,unsigned now,void *audio,
        void (*push)(void *,void *,unsigned)) {
    if(!q->started) {
        if(q->used<640)return;
        q->started=1;q->next=now;
    }
    if((int)(now-q->next)<0)return;
    unsigned char pcm[320];unsigned take=q->used<sizeof(pcm)?q->used:sizeof(pcm);
    for(unsigned i=0;i<sizeof(pcm);i++)pcm[i]=i<take?q->pcm[i]:0;
    for(unsigned i=take;i<q->used;i++)q->pcm[i-take]=q->pcm[i];
    q->used-=take;if(take<sizeof(pcm))q->underruns++;
    push(audio,pcm,sizeof(pcm));q->packets++;
    q->next+=10;
    /* A scheduler stall must not become a burst of accelerated playback. */
    if((int)(now-q->next)>=0)q->next=now+10;
}
