#include "encoder_core.h"
#include <libyuv.h>
#include <assert.h>
#include <fcntl.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>
static void create(const char *p){int fd=open(p,O_CREAT|O_TRUNC|O_WRONLY,0600);assert(fd>=0);close(fd);}
int main(int argc,char **argv){
 assert(argc==3);create(argv[1]);create(argv[2]);
 unsigned char *rgba=malloc(FRAME_BYTES),*i420=malloc(I420_BYTES);assert(rgba&&i420);
 for(int y=0;y<720;y++)for(int x=0;x<1920;x++){
  size_t i=((size_t)y*1920+x)*4;rgba[i]=x/8;rgba[i+1]=y/3;rgba[i+2]=64;rgba[i+3]=255;
 }
 assert(!ABGRToI420(rgba,1920*4,i420,1920,i420+1920*720,960,i420+1920*720*5/4,960,1920,-720));
 Encoder *a=encoder_open(argv[1],1920,720,60),*b=encoder_open(argv[2],1920,720,60);assert(a&&b);
 assert(encoder_frame_i420(b,NULL,0,0)==-1);assert(encoder_frame_i420(b,i420,0,2)==-1);
 for(int i=0;i<8;i++){
  assert(!encoder_frame(a,rgba,1000+i*33,i==4));
  assert(!encoder_frame_i420(b,i420,1000+i*33,i==4));
 }
 encoder_close(a);encoder_close(b);free(rgba);free(i420);return 0;
}
