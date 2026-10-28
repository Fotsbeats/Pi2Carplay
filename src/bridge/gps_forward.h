#include "nmea.h"
#include "gps_stats.h"
static struct nmea_stream vehicle_nmea;
static unsigned gps_forwarded;
static struct gps_stats vehicle_gps;
static void gps_emit(const char *line,unsigned length) {
    gps_count_sentence(&vehicle_gps,line,length);
    char packet[256];packet[0]='\n';
    for(unsigned i=0;i<length;i++)packet[i+1]=line[i];
    packet[length+1]='\n';
    /* RDWR prevents SIGPIPE if GNSS restarts. Closing drops data if no HAL is listening. */
    int fd=open("/dev/box-carplay-gps",2|2048|131072);
    if(fd<0){vehicle_gps.dropped++;return;}
    int sent=write(fd,packet,length+2);close(fd);
    if(sent==(int)length+2){vehicle_gps.forwarded++;if(++gps_forwarded==1)print("Vehicle NMEA accepted into Android GNSS channel\n");}
    else vehicle_gps.dropped++;
}
static void gps_callback(void *session,const char *data,int length,void *context) {
    (void)session;(void)context;
    if(data&&length>0&&length<=4096){
        vehicle_gps.packets++;vehicle_gps.bytes+=(unsigned)length;
        nmea_feed(&vehicle_nmea,data,(unsigned)length,gps_emit);
        if((vehicle_gps.packets-1)%128==0){
            char summary[256];
            snprintf(summary,sizeof(summary),"Vehicle GPS status: packets=%u sentences=%u forwarded=%u dropped=%u rmc_active=%u rmc_void=%u rmc_ticks=%u gga_fix=%u gga_no_fix=%u gsa=%u\n",
                vehicle_gps.packets,vehicle_gps.sentences,vehicle_gps.forwarded,vehicle_gps.dropped,
                vehicle_gps.rmc_active,vehicle_gps.rmc_void,vehicle_gps.rmc_ticks,vehicle_gps.gga_fix,vehicle_gps.gga_no_fix,vehicle_gps.gsa);
            print(summary);
        }
    }
}
