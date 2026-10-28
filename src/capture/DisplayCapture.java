import android.graphics.SurfaceTexture;
import android.hardware.display.VirtualDisplay;
import android.opengl.*;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.view.Surface;
import java.io.*;
import java.nio.*;

/** Capture starts only after the bridge publishes the selected head-unit geometry. */
public final class DisplayCapture {
    static { System.load(System.getProperty("pi5.encoder.library","/data/local/tmp/pi5-experimental/libcapture_encoder.so")); }
    static native long openEncoder(String output,int width,int height,int fps) throws IOException;
    static native void encodeFrame(long encoder, ByteBuffer rgba, int timestamp, int force) throws IOException;
    static native void closeEncoder(long encoder);
    static native void encodeI420(long encoder, ByteBuffer yuv, int timestamp, int force) throws IOException;
    static native void referenceI420(ByteBuffer rgba,ByteBuffer yuv,int width,int height) throws IOException;

    public static void main(String[] args) throws Exception {
        if (args.length != 9) throw new IllegalArgumentException("output pipe, duration seconds, negotiated geometry");
        DisplayGeometry geometry=DisplayGeometry.parse(args,2);
        int seconds = Integer.parseInt(args[1]);
        if (seconds < 0 || seconds > 150) throw new IllegalArgumentException("duration");
        Class<?> properties=Class.forName("android.os.SystemProperties");
        java.lang.reflect.Method getInt=properties.getMethod("getInt",String.class,int.class);
        java.lang.reflect.Method set=properties.getMethod("set",String.class,String.class);
        Looper.prepareMainLooper();
        VirtualDisplay mirror = null;
        HandlerThread callbacks = new HandlerThread("capture-frames"); callbacks.start();
        SurfaceTexture texture = null; Surface mirrorSurface = null;
        GpuI420 gpu=null;
        long encoder = openEncoder(args[0],geometry.width,geometry.height,geometry.fps);
        FramePipeline pipeline=null;
        try {
            gpu=new GpuI420(true,geometry);
            final long handle=encoder;
            pipeline=new FramePipeline(geometry.packedBytes,(buffer,stamp,force)->encodeI420(handle,buffer,stamp,force));
            int[] name = new int[1]; GLES20.glGenTextures(1,name,0);
            GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,name[0]);
            GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,GLES20.GL_TEXTURE_MIN_FILTER,GLES20.GL_LINEAR);
            GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,GLES20.GL_TEXTURE_MAG_FILTER,GLES20.GL_LINEAR);
            texture = new SurfaceTexture(name[0]); texture.setDefaultBufferSize(geometry.contentWidth,geometry.contentHeight);
            Object lock = new Object(); boolean[] available = {false};
            texture.setOnFrameAvailableListener(t -> { synchronized(lock) { available[0]=true; lock.notifyAll(); } },new Handler(callbacks.getLooper()));
            mirrorSurface = new Surface(texture);
            mirror = (VirtualDisplay) android.hardware.display.DisplayManager.class.getMethod("createVirtualDisplay",
                String.class,int.class,int.class,int.class,Surface.class).invoke(null,"Pi CarPlay capture",geometry.contentWidth,geometry.contentHeight,0,mirrorSurface);
            if (mirror == null) throw new IllegalStateException("mirror unavailable");
            float[] matrix = new float[16];
            long end = seconds == 0 ? Long.MAX_VALUE : System.nanoTime() + seconds*1000000000L, next = 0;
            long[] sourceStamps=new long[12000],captureStamps=new long[12000];
            int frames = 0, unique=0; long previousTexture=-1,firstTexture=0,lastTexture=0;
            while (System.nanoTime() < end) {
                synchronized(lock) {
                    while ((!available[0] || System.nanoTime() < next) && System.nanoTime() < end) {
                        long wake = available[0] ? Math.min(end,next) : end;
                        lock.wait(Math.max(1,Math.min(100,(wake-System.nanoTime())/1000000)));
                        pipeline.check();
                        if ((Integer)getInt.invoke(null,"sys.suding.force.key.frame",0)==1 && System.nanoTime()>=next) available[0]=true;
                    }
                    if (!available[0] || System.nanoTime() >= end) break;
                    available[0]=false;
                }
                ByteBuffer i420=pipeline.acquire();
                texture.updateTexImage(); texture.getTransformMatrix(matrix);
                long textureStamp=texture.getTimestamp();
                if(textureStamp!=previousTexture){unique++;if(firstTexture==0)firstTexture=textureStamp;lastTexture=textureStamp;previousTexture=textureStamp;}
                long captured = System.nanoTime();
                if (next == 0) next = captured;
                do { next += 1000000000L/geometry.fps; } while (next <= captured);
                if(frames<sourceStamps.length){sourceStamps[frames]=textureStamp;captureStamps[frames]=captured;}
                gpu.render(name[0],matrix,i420);
                int force=(Integer)getInt.invoke(null,"sys.suding.force.key.frame",0)==1?1:0;
                if(force==1) {
                    set.invoke(null,"sys.suding.force.key.frame","0");
                    System.err.println("HU keyframe request forwarded to encoder");
                }
                pipeline.submit(i420,(int)(captured/1000000),force); frames++;
            }
            if(frames==0) throw new IOException("no captured frames");
            String metrics=System.getProperty("pi5.capture.metrics");
            if(metrics!=null)try(DataOutputStream stats=new DataOutputStream(new FileOutputStream(metrics))){
                for(int i=0;i<Math.min(frames,sourceStamps.length);i++){stats.writeLong(sourceStamps[i]);stats.writeLong(captureStamps[i]);}
            }
            System.err.println("Fresh mirrored frames="+unique+" source_span_ns="+(lastTexture-firstTexture));
            System.err.println("GPU capture: frames="+frames+" duration_s="+seconds);

        } finally {
            try { if(pipeline!=null)pipeline.close(); }
            finally {
                if(mirror!=null)mirror.release();
                if(mirrorSurface!=null)mirrorSurface.release();if(texture!=null)texture.release();
                if(gpu!=null)gpu.close();callbacks.quitSafely();
                // A timed-out native writer still owns its codec until process exit.
                if(pipeline==null||pipeline.isStopped())closeEncoder(encoder);
            }
        }
    }
}
