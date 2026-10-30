/* PCM16LE at the private call transport boundary. */
#ifndef PI2CALL_PCM_H
#define PI2CALL_PCM_H
#include <stddef.h>
#include <stdint.h>
#include <string.h>
#define CALL_PCM_CAPACITY 1920
struct call_pcm { unsigned char data[CALL_PCM_CAPACITY]; size_t used; };
static void call_pcm_put(struct call_pcm *q, const unsigned char *p, size_t n) {
    if (n > CALL_PCM_CAPACITY) { p += n-CALL_PCM_CAPACITY; n=CALL_PCM_CAPACITY; }
    if (q->used+n > CALL_PCM_CAPACITY) {
        size_t drop=q->used+n-CALL_PCM_CAPACITY;
        memmove(q->data,q->data+drop,q->used-drop); q->used-=drop;
    }
    memcpy(q->data+q->used,p,n); q->used+=n;
}
static size_t call_pcm_take(struct call_pcm *q, unsigned char *p, size_t n) {
    size_t got=n<q->used?n:q->used;
    memcpy(p,q->data,got); memset(p+got,0,n-got);
    memmove(q->data,q->data+got,q->used-got); q->used-=got; return got;
}
/* Validate H4 packet boundaries before inspecting controller payloads. */
static int call_hci_length(const unsigned char *p,size_t n) {
    if (!n) return 0;
    if (p[0]==1 && n>=4) return (int)(4+p[3]);
    if (p[0]==2 && n>=5) return (int)(5+p[3]+256u*p[4]);
    if (p[0]==3 && n>=4) return (int)(4+p[3]);
    if (p[0]==4 && n>=3) return (int)(3+p[2]);
    if (p[0]==5 && n>=5) return (int)(5+p[3]+256u*(p[4]&63));
    return 0;
}
#endif
