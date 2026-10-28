/** One negotiated session geometry shared by capture, GPU composition and encoder. */
final class DisplayGeometry {
    final int width,height,fps,x,y,contentWidth,contentHeight,bytes,packedWidth,packedHeight,packedBytes;
    DisplayGeometry(int w,int h,int rate,int left,int top,int cw,int ch) {
        if(w<320||h<240||w>2560||h>1440||(w&1)!=0||(h&1)!=0||rate<1||rate>60||
           left<0||top<0||cw<320||ch<240||left>w-cw||top>h-ch)
            throw new IllegalArgumentException("Unsupported negotiated display geometry");
        width=w;height=h;fps=rate;x=left;y=top;contentWidth=cw;contentHeight=ch;
        bytes=w*h*3/2;packedWidth=(w+3)/4;packedHeight=(bytes+packedWidth*4-1)/(packedWidth*4);
        packedBytes=packedWidth*packedHeight*4;
    }
    static DisplayGeometry parse(String[] values,int start) {
        if(values.length-start!=7)throw new IllegalArgumentException("Expected width height fps x y content-width content-height");
        int[] n=new int[7];for(int i=0;i<7;i++)n[i]=Integer.parseInt(values[start+i]);
        return new DisplayGeometry(n[0],n[1],n[2],n[3],n[4],n[5],n[6]);
    }
}
