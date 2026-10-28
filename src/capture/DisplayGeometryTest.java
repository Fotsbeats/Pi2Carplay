public final class DisplayGeometryTest {
    public static void main(String[] args) {
        for(int[] n:new int[][]{{1920,720,60,176,0,1744,720},{1280,720,30,0,0,1280,720},
                {800,480,30,0,0,800,480},{1282,722,60,0,0,1282,722},{1920,1080,60,0,0,1920,1080},
                {1280,720,60,100,60,1000,600}}) {
            DisplayGeometry g=new DisplayGeometry(n[0],n[1],n[2],n[3],n[4],n[5],n[6]);
            assert g.packedBytes>=g.bytes && g.packedBytes-g.bytes<g.packedWidth*4;
        }
        try {new DisplayGeometry(1280,720,60,176,0,1280,720);throw new AssertionError();}
        catch(IllegalArgumentException expected){}
        try {new DisplayGeometry(3840,2160,60,0,0,3840,2160);throw new AssertionError();}
        catch(IllegalArgumentException expected){}
        System.out.println("PASS: negotiated bounds, insets and packed I420 allocation");
    }
}
