/* Bounded NMEA stream framing; only checksum-valid standard GNSS sentences pass. */
struct nmea_stream { char line[252]; unsigned used; };
static int nmea_hex(unsigned char c) {
    if(c>='0'&&c<='9')return c-'0';
    if(c>='A'&&c<='F')return c-'A'+10;
    if(c>='a'&&c<='f')return c-'a'+10;
    return -1;
}
static int nmea_valid(const char *p,unsigned n) {
    if(n<10||p[0]!='$'||p[1]<'A'||p[1]>'Z'||p[2]<'A'||p[2]>'Z'||p[6]!=','||p[n-3]!='*')return 0;
    int known=(p[3]=='R'&&p[4]=='M'&&p[5]=='C')||(p[3]=='G'&&p[4]=='G'&&p[5]=='A')||
        (p[3]=='G'&&p[4]=='S'&&(p[5]=='A'||p[5]=='V'||p[5]=='T'))||
        (p[3]=='V'&&p[4]=='T'&&p[5]=='G')||(p[3]=='Z'&&p[4]=='D'&&p[5]=='A')||
        (p[3]=='H'&&p[4]=='D'&&p[5]=='T');
    int hi=nmea_hex(p[n-2]),lo=nmea_hex(p[n-1]);
    if(!known||hi<0||lo<0)return 0;
    unsigned sum=0;
    for(unsigned i=1;i<n-3;i++){if(p[i]=='*')return 0;sum^=(unsigned char)p[i];}
    return sum==(unsigned)((hi<<4)|lo);
}
static void nmea_feed(struct nmea_stream *s,const char *data,unsigned length,void (*emit)(const char *,unsigned)) {
    for(unsigned i=0;i<length;i++) {
        unsigned char c=data[i];
        if(c=='$'){s->used=0;s->line[s->used++]=(char)c;continue;}
        if(c=='\r')continue;
        if(c=='\n'||c==0){if(nmea_valid(s->line,s->used))emit(s->line,s->used);s->used=0;continue;}
        if(!s->used)continue;
        if(c<32||c>126||s->used>=sizeof(s->line)){s->used=0;continue;}
        s->line[s->used++]=(char)c;
    }
    /* iAP2 may delimit a sentence by packet length instead of CR/LF. */
    if(nmea_valid(s->line,s->used)){emit(s->line,s->used);s->used=0;}
}
