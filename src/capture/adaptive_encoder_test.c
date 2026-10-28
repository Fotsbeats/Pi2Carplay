#include "encoder_core.h"
#include <assert.h>
#include <fcntl.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>
int main(int argc,char **argv) {
    assert(argc==2);
    int sizes[][3]={{800,480,30},{1280,720,30},{1282,722,60},{1920,720,60},{1920,1080,60},{2560,1440,30}};
    assert(!encoder_open("unused",3840,2160,60));
    assert(!encoder_open("unused",1281,720,60));
    assert(!encoder_open("unused",1280,720,0));
    for(unsigned i=0;i<sizeof(sizes)/sizeof(sizes[0]);i++) {
        int w=sizes[i][0],h=sizes[i][1],fps=sizes[i][2]; char path[512];
        assert(snprintf(path,sizeof(path),"%s/%dx%d-%d.bin",argv[1],w,h,fps)<(int)sizeof(path));
        int fd=open(path,O_CREAT|O_TRUNC|O_WRONLY,0600); assert(fd>=0);close(fd);
        unsigned char *yuv=malloc((size_t)w*h*3/2);assert(yuv);memset(yuv,128,(size_t)w*h*3/2);
        Encoder *e=encoder_open(path,w,h,fps);assert(e);
        assert(encoder_width(e)==w && encoder_height(e)==h);
        for(int frame=0;frame<3;frame++)assert(!encoder_frame_i420(e,yuv,1000+frame*1000/fps,frame==2));
        encoder_close(e);free(yuv);
    }
    return 0;
}
