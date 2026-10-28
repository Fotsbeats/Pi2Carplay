#include <assert.h>
#include <stdio.h>
#include <string.h>
#include "nmea.h"
static unsigned count;
static char result[256];
static void emit(const char *s,unsigned n){count++;memcpy(result,s,n);result[n]=0;}
static void sentence(char *out,const char *body){unsigned crc=0;for(const char *p=body;*p;p++)crc^=(unsigned char)*p;sprintf(out,"$%s*%02X",body,crc);}
int main(void){
 struct nmea_stream s={0};char line[256];sentence(line,"GNRMC,123519,V,,,,,,,281026,,,N");
 for(unsigned i=0;i<strlen(line);i++)nmea_feed(&s,line+i,1,emit);
 assert(count==1&&!strcmp(result,line));nmea_feed(&s,"\r\n",2,emit);assert(count==1);
 nmea_feed(&s,line,(unsigned)strlen(line),emit);assert(count==2);
 char corrupt[256];strcpy(corrupt,line);corrupt[9]^=1;nmea_feed(&s,corrupt,(unsigned)strlen(corrupt),emit);nmea_feed(&s,"\n",1,emit);assert(count==2);
 char noise[1024];memset(noise,'x',sizeof(noise));noise[0]='$';nmea_feed(&s,noise,sizeof(noise),emit);assert(s.used==0);
 sentence(corrupt,"PTEST,untrusted");nmea_feed(&s,corrupt,(unsigned)strlen(corrupt),emit);nmea_feed(&s,"\n",1,emit);assert(count==2);
 nmea_feed(&s,"$broken\n",8,emit);nmea_feed(&s,line,(unsigned)strlen(line),emit);assert(count==3);
 sentence(line,"GPGGA,123519,4807.038,N,01131.000,E,1,08,0.9,545.4,M,46.9,M,,");
 nmea_feed(&s,line,(unsigned)strlen(line),emit);assert(count==4);
 puts("PASS: fragmented/packet NMEA, checksums, overflow recovery, unknown sentences and no-fix RMC");
}
