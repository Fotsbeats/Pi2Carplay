#include <assert.h>
#include <stdio.h>
#include <string.h>
#include "gps_stats.h"
static void feed(struct gps_stats *s,const char *line){gps_count_sentence(s,line,(unsigned)strlen(line));}
int main(void){
    struct gps_stats s={0};
    feed(&s,"$GPRMC,123519,A,4807.038,N,01131.000,E,0,0,281026*00");
    feed(&s,"$GNRMC,123519,V,,,,,,,281026*00");
    assert(s.rmc_active==1&&s.rmc_void==1&&s.rmc_ticks==1);
    feed(&s,"$GPRMC,123520,A,4807.039,N,01131.001,E,0,0,281026*00");
    feed(&s,"$GPRMC,,V,,,,,,,281026*00");
    assert(s.rmc_active==2&&s.rmc_void==2&&s.rmc_ticks==2);
    feed(&s,"$GPGGA,123520,4807.039,N,01131.001,E,1,08,1.0*00");
    feed(&s,"$GPGGA,123520,,,,,0,00,99.0*00");
    feed(&s,"$GNGSA,A,3,,,,,,,,,,,,,1,1,1*00");
    assert(s.gga_fix==1&&s.gga_no_fix==1&&s.gsa==1&&s.sentences==7);
    puts("PASS: fix/no-fix statuses, UTC progress, empty time and mixed talkers; no position fields retained");
}
