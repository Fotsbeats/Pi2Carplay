import android.opengl.*;
import java.nio.*;
import java.io.IOException;

/** Run on the Pi before installing the candidate capture JAR. */
public final class GpuConversionSelfCheck {
    private static int channel(int x,int y,int c){
        if(c==0)return (x/16+y/32)%256;
        if(c==1)return (x/8+y/16)%256;
        return ((x<872?37:211)+(y<360?0:29))%256;
    }
    public static void main(String[] args) throws Exception {
        for(int[] n:new int[][]{{1920,720,60,176,0,1744,720},{800,480,30,0,0,800,480},
                {1282,722,60,0,0,1282,722},{1280,720,60,100,60,1000,600},
                {1280,720,60,101,61,1001,597}})check(new DisplayGeometry(n[0],n[1],n[2],n[3],n[4],n[5],n[6]));
        EncoderSelfCheck.main(new String[]{"/data/local/tmp/pi5-encoder-selfcheck.bin"});
    }
    private static void check(DisplayGeometry g) throws Exception {
        ByteBuffer image=ByteBuffer.allocateDirect(g.contentWidth*g.contentHeight*4);
        ByteBuffer canvas=ByteBuffer.allocateDirect(g.width*g.height*4);
        for(int y=0;y<g.contentHeight;y++)for(int x=0;x<g.contentWidth;x++) {
            for(int c=0;c<3;c++)image.put((byte)channel(x,y,c));image.put((byte)255);
        }
        for(int y=0;y<g.height;y++)for(int x=0;x<g.width;x++) {
            int topY=g.height-1-y;
            boolean inside=x>=g.x&&x<g.x+g.contentWidth&&topY>=g.y&&topY<g.y+g.contentHeight;
            for(int c=0;c<3;c++)canvas.put((byte)(inside?channel(x-g.x,g.contentHeight-1-(topY-g.y),c):0));
            canvas.put((byte)255);
        }
        image.clear();canvas.clear();
        ByteBuffer expected=ByteBuffer.allocateDirect(g.packedBytes),actual=ByteBuffer.allocateDirect(g.packedBytes);
        DisplayCapture.referenceI420(canvas,expected,g.width,g.height);
        try(GpuI420 gpu=new GpuI420(false,g)) {
            int[] texture=new int[1];GLES20.glGenTextures(1,texture,0);
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D,texture[0]);
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,GLES20.GL_TEXTURE_MIN_FILTER,GLES20.GL_LINEAR);
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,GLES20.GL_TEXTURE_MAG_FILTER,GLES20.GL_LINEAR);
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,GLES20.GL_TEXTURE_WRAP_S,GLES20.GL_CLAMP_TO_EDGE);
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,GLES20.GL_TEXTURE_WRAP_T,GLES20.GL_CLAMP_TO_EDGE);
            GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D,0,GLES20.GL_RGBA,g.contentWidth,g.contentHeight,0,GLES20.GL_RGBA,GLES20.GL_UNSIGNED_BYTE,image);
            float[] matrix=new float[16];Matrix.setIdentityM(matrix,0);
            gpu.render(texture[0],matrix,actual);
            int max=0,bad=0;
            for(int i=0;i<g.bytes;i++) {
                int delta=Math.abs((expected.get(i)&255)-(actual.get(i)&255));max=Math.max(max,delta);if(delta>2)bad++;
            }
            for(int y=0;y<g.height;y++)for(int x=0;x<g.width;x++)
                if((x<g.x||x>=g.x+g.contentWidth||y<g.y||y>=g.y+g.contentHeight)&&
                        (actual.get(y*g.width+x)&255)!=16)throw new IOException("Reserved-area luma mismatch");
            if(bad!=0)throw new IOException("GPU/libyuv mismatch: bytes="+bad+" max_delta="+max);
            GLES20.glDeleteTextures(1,texture,0);
            System.out.println("PASS: GPU I420 geometry="+g.width+"x"+g.height+" content="+g.contentWidth+"x"+g.contentHeight+" origin="+g.x+","+g.y+" max delta="+max);
        }
    }
}
