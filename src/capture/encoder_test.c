#include "encoder_core.h"
#include <assert.h>
#include <fcntl.h>
#include <stdlib.h>
#include <unistd.h>
int main(int argc,char **argv) {
    assert(argc==2);
    int fd=open(argv[1],O_CREAT|O_TRUNC|O_WRONLY,0600);
    assert(fd>=0); close(fd);
    assert(!encoder_open("/nonexistent-directory/encoder-test",1920,720,60));
    unsigned char *rgba=malloc(FRAME_BYTES); assert(rgba);
    for(int y=0;y<720;y++) for(int x=0;x<1920;x++) {
        size_t i=((size_t)y*1920+x)*4;
        rgba[i]=(unsigned char)(x/8); rgba[i+1]=(unsigned char)(y/3);
        rgba[i+2]=64; rgba[i+3]=255;
    }
    Encoder *e=encoder_open(argv[1],1920,720,60); assert(e);
    assert(encoder_frame(e,NULL,999,0)==-1);
    assert(encoder_frame(e,rgba,999,2)==-1);
    for(int i=0;i<8;i++) assert(!encoder_frame(e,rgba,1000+i*33,i==4));
    encoder_close(e); free(rgba); return 0;
}
