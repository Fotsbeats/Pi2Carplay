#include "encoder_core.h"
#include <assert.h>
#include <fcntl.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>
int main(int argc,char **argv){
 assert(argc==2);int fd=open(argv[1],O_CREAT|O_TRUNC|O_WRONLY,0600);assert(fd>=0);close(fd);
 unsigned char *i420=malloc(I420_BYTES);assert(i420);memset(i420,128,I420_BYTES);
 Encoder *e=encoder_open(argv[1],1920,720,60);assert(e);int result=encoder_frame_i420(e,i420,1000,1);
 encoder_close(e);free(i420);
 if(result!=-4){fprintf(stderr,"Unexpected two-thread contract result %d\n",result);return 1;}
 puts("Two sliced threads produce multiple VCL NALs; retain one encoder thread for the selected bridge contract.");return 0;
}
