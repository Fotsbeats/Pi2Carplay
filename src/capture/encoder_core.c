/* Same selected AVC contract; direct native input replaces the raw RGBA pipe. */
#include "encoder_core.h"
#include <errno.h>
#include <fcntl.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <time.h>
#include <unistd.h>
#include <x264.h>
#include <libyuv.h>

struct Encoder {
    x264_t *codec;
    x264_picture_t picture;
    int picture_allocated, fd, frames, width, height, fps, bitrate, codec_frames, force_idr;
    uint64_t total_ms, max_ms;
};
static void put32(unsigned char *p, uint32_t n) {
    p[0]=n>>24; p[1]=n>>16; p[2]=n>>8; p[3]=n;
}
static int write_all(int fd, const void *data, size_t size) {
    const unsigned char *p=data;
    while(size) {
        ssize_t n=write(fd,p,size);
        if(n<0 && errno==EINTR) continue;
        if(n<=0) return -1;
        p+=n; size-=(size_t)n;
    }
    return 0;
}
static int packet(Encoder *e, const void *data, int size, int config, uint32_t stamp) {
    if(size<=0 || size>2*1024*1024) return -1;
    unsigned char header[12];
    put32(header,(uint32_t)size); put32(header+4,(uint32_t)config); put32(header+8,stamp);
    return write_all(e->fd,header,sizeof(header)) || write_all(e->fd,data,(size_t)size) ? -1:0;
}
int encoder_width(const Encoder *e) { return e ? e->width : 0; }
int encoder_height(const Encoder *e) { return e ? e->height : 0; }
static x264_t *create_codec(int width,int height,int fps,int bitrate) {
    x264_param_t p;
    if(x264_param_default_preset(&p,"ultrafast","zerolatency")) return NULL;
    p.i_width=width; p.i_height=height; p.i_csp=X264_CSP_I420;
    p.i_fps_num=fps; p.i_fps_den=1; p.b_vfr_input=0;
#ifndef ENCODER_THREADS
#define ENCODER_THREADS 1
#endif
    p.i_threads=ENCODER_THREADS; p.b_sliced_threads=ENCODER_THREADS>1; p.i_bframe=0;
    p.i_keyint_max=1080000; p.b_repeat_headers=0; p.b_annexb=1;
    p.rc.i_rc_method=X264_RC_ABR; p.rc.i_bitrate=bitrate;
    p.rc.i_vbv_max_bitrate=bitrate; p.rc.i_vbv_buffer_size=bitrate;
    p.i_log_level=X264_LOG_WARNING;
    if(x264_param_apply_profile(&p,"baseline")) return NULL;
    return x264_encoder_open(&p);
}
static int emit_headers(Encoder *e) {
    x264_nal_t *nals; int count, size=0; unsigned char config[4096];
    if(x264_encoder_headers(e->codec,&nals,&count)<0) return -1;
    for(int i=0;i<count;i++) if(nals[i].i_type==NAL_SPS || nals[i].i_type==NAL_PPS) {
        if(nals[i].i_payload<=0 || nals[i].i_payload>(int)sizeof(config)-size) return -1;
        memcpy(config+size,nals[i].p_payload,(size_t)nals[i].i_payload); size+=nals[i].i_payload;
    }
    return packet(e,config,size,1,0);
}
Encoder *encoder_open(const char *path, int width, int height, int fps) {
    if(!path||width<320||height<240||width>2560||height>1440||(width&1)||(height&1)||fps<1||fps>60) return NULL;
    Encoder *e=calloc(1,sizeof(*e));
    if(!e) return NULL;
    e->fd=-1;e->width=width;e->height=height;e->fps=fps;e->bitrate=4000;
    e->codec=create_codec(width,height,fps,e->bitrate);
    if(!e->codec)goto fail;
    e->fd=open(path,O_WRONLY|O_CLOEXEC);
    if(e->fd<0 || emit_headers(e))goto fail;
    return e;
fail:
    encoder_close(e); return NULL;
}
int encoder_reconfigure(Encoder *e,int fps,int bitrate) {
    if(!e||fps<1||fps>60||(bitrate!=4000&&bitrate!=8000))return -1;
    if(fps==e->fps&&bitrate==e->bitrate)return 0;
    if(fps!=e->fps) {
        /* FPS is not x264-reconfigurable: replace only the codec on the same framed stream. */
        x264_t *next=create_codec(e->width,e->height,fps,bitrate);
        if(!next)return -1;
        x264_encoder_close(e->codec);e->codec=next;e->codec_frames=0;
        if(emit_headers(e))return -1;
    } else {
        x264_param_t p;x264_encoder_parameters(e->codec,&p);
        p.rc.i_bitrate=p.rc.i_vbv_max_bitrate=p.rc.i_vbv_buffer_size=bitrate;
        if(x264_encoder_reconfig(e->codec,&p))return -1;
    }
    e->fps=fps;e->bitrate=bitrate;e->force_idr=1;
    fprintf(stderr,"JNI x264 mode: fps=%d bitrate_kbps=%d\n",fps,bitrate);
    return 0;
}
static int encode_picture(Encoder *e, x264_picture_t *picture, uint32_t stamp, int force) {
    picture->i_type=(force||e->force_idr)?X264_TYPE_IDR:X264_TYPE_AUTO;
    picture->i_pts=e->codec_frames;
    x264_nal_t *nals; x264_picture_t output; int count, vcl=0;
    if(x264_encoder_encode(e->codec,&nals,&count,picture,&output)<0) return -3;
    int selected=-1;
    for(int i=0;i<count;i++) if(nals[i].i_type==NAL_SLICE || nals[i].i_type==NAL_SLICE_IDR) {
        if(++vcl>1) return -4;
        selected=i;
    }
    if(vcl!=1) return -5;
    if(packet(e,nals[selected].p_payload,nals[selected].i_payload,0,stamp)) return -4;
    struct timespec now; clock_gettime(CLOCK_MONOTONIC,&now);
    uint32_t age=(uint32_t)((uint64_t)now.tv_sec*1000+now.tv_nsec/1000000)-stamp;
    e->force_idr=0;e->codec_frames++;e->frames++; e->total_ms+=age; if(age>e->max_ms) e->max_ms=age;
    if(e->frames%300==0) fprintf(stderr,"JNI x264: frames=%d mean_ms=%llu max_ms=%llu\n",
        e->frames,(unsigned long long)(e->total_ms/e->frames),(unsigned long long)e->max_ms);
    return 0;
}
int encoder_frame(Encoder *e, const unsigned char *rgba, uint32_t stamp, int force) {
    if(!e || !rgba || force<0 || force>1) return -1;
    if(!e->picture_allocated) {
        if(x264_picture_alloc(&e->picture,X264_CSP_I420,e->width,e->height)) return -2;
        e->picture_allocated=1;
    }
    if(ABGRToI420(rgba,e->width*4,e->picture.img.plane[0],e->picture.img.i_stride[0],
        e->picture.img.plane[1],e->picture.img.i_stride[1],e->picture.img.plane[2],
        e->picture.img.i_stride[2],e->width,-e->height)) return -2;
    return encode_picture(e,&e->picture,stamp,force);
}
int encoder_frame_i420(Encoder *e, const unsigned char *i420, uint32_t stamp, int force) {
    if(!e || !i420 || force<0 || force>1) return -1;
    x264_picture_t picture;
    x264_picture_init(&picture);
    picture.img.i_csp=X264_CSP_I420;
    picture.img.i_plane=3;
    picture.img.plane[0]=(unsigned char *)i420;
    picture.img.plane[1]=(unsigned char *)i420+e->width*e->height;
    picture.img.plane[2]=(unsigned char *)i420+e->width*e->height*5/4;
    picture.img.i_stride[0]=e->width;
    picture.img.i_stride[1]=picture.img.i_stride[2]=e->width/2;
    return encode_picture(e,&picture,stamp,force);
}
void encoder_close(Encoder *e) {
    if(!e) return;
    fprintf(stderr,"JNI x264 final: frames=%d mean_ms=%llu max_ms=%llu\n",e->frames,
        (unsigned long long)(e->frames?e->total_ms/e->frames:0),(unsigned long long)e->max_ms);
    if(e->picture_allocated) x264_picture_clean(&e->picture);
    if(e->codec) x264_encoder_close(e->codec);
    if(e->fd>=0) close(e->fd);
    free(e);
}
