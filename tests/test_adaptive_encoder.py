"""Check emitted SPS dimensions, framing, timestamps and requested IDR across geometries."""
from pathlib import Path
import struct,sys

class Bits:
    def __init__(self,data):
        self.bits=''.join(f'{n:08b}' for n in data);self.pos=0
    def read(self,n):
        assert self.pos+n<=len(self.bits)
        value=int(self.bits[self.pos:self.pos+n],2);self.pos+=n;return value
    def ue(self):
        zeros=0
        while self.read(1)==0:
            zeros+=1;assert zeros<32
        return (1<<zeros)-1+(self.read(zeros) if zeros else 0)

for w,h,fps in [(800,480,30),(1280,720,30),(1282,722,60),(1920,720,60),(1920,1080,60),(2560,1440,30)]:
    data=(Path(sys.argv[1])/f'{w}x{h}-{fps}.bin').read_bytes();packets=[]
    while data:
        size,config,stamp=struct.unpack('>III',data[:12]);assert 0<size<=2097152
        payload=data[12:12+size];assert len(payload)==size
        assert payload[:4]==b'\0\0\0\1' or payload[:3]==b'\0\0\1'
        offset=4 if payload[:4]==b'\0\0\0\1' else 3
        packets.append((config,stamp,payload[offset:]));data=data[12+size:]
    assert len(packets)==4 and [p[0] for p in packets]==[1,0,0,0]
    assert [p[1] for p in packets]==[0,1000,1000+1000//fps,1000+2000//fps]
    assert [p[2][0]&31 for p in packets]==[7,5,1,5]
    sps=packets[0][2][1:].replace(b'\0\0\3',b'\0\0');b=Bits(sps)
    assert b.read(8)==66 # Baseline
    b.read(8);b.read(8);b.ue();b.ue();poc=b.ue()
    assert poc in (0,2)
    if poc==0:b.ue()
    b.ue();b.read(1);mw=b.ue()+1;mh=b.ue()+1
    frame_only=b.read(1);assert frame_only==1
    b.read(1);crop=[b.ue() for _ in range(4)] if b.read(1) else [0]*4
    assert mw*16-2*(crop[0]+crop[1])==w
    assert mh*16-2*(crop[2]+crop[3])==h
print('PASS: six real x264 streams, cropped SPS dimensions, timestamps and forced IDR')
