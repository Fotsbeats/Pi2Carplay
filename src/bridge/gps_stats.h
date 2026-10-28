/* Sentence/fix-status counters only; no coordinates or raw NMEA retained. */
struct gps_stats {
    unsigned packets, bytes, sentences, forwarded, dropped;
    unsigned rmc_active, rmc_void, rmc_ticks, gga_fix, gga_no_fix, gsa;
    char utc[6];
};
static const char *gps_field(const char *s,unsigned length,unsigned field) {
    for(unsigned i=0;i<length;i++)if(s[i]==','&&--field==0)return i+1<length?s+i+1:0;
    return 0;
}
static void gps_count_sentence(struct gps_stats *s,const char *line,unsigned length) {
    s->sentences++;
    if(line[3]=='R'&&line[4]=='M'&&line[5]=='C') {
        const char *status=gps_field(line,length,2),*utc=gps_field(line,length,1);
        if(status&&*status=='A')s->rmc_active++;
        else if(status&&*status=='V')s->rmc_void++;
        if(utc&&(unsigned)(line+length-utc)>=6) {
            unsigned valid=1,changed=0;
            for(unsigned i=0;i<6;i++){if(utc[i]<'0'||utc[i]>'9')valid=0;if(utc[i]!=s->utc[i])changed=1;}
            if(valid&&changed){s->rmc_ticks++;for(unsigned i=0;i<6;i++)s->utc[i]=utc[i];}
        }
    } else if(line[3]=='G'&&line[4]=='G'&&line[5]=='A') {
        const char *quality=gps_field(line,length,6);
        if(quality&&*quality>='1'&&*quality<='8')s->gga_fix++;
        else s->gga_no_fix++;
    } else if(line[3]=='G'&&line[4]=='S'&&line[5]=='A')s->gsa++;
}
