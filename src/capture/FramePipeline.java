import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;

/** Two buffers total: capture cannot overwrite a frame still being encoded. */
final class FramePipeline implements AutoCloseable {
    interface Sink { void encode(ByteBuffer frame,int timestamp,int force) throws IOException; }
    private static final class Frame {
        final ByteBuffer data;final int timestamp,force;
        Frame(ByteBuffer data,int timestamp,int force){this.data=data;this.timestamp=timestamp;this.force=force;}
    }
    private final ArrayBlockingQueue<ByteBuffer> free=new ArrayBlockingQueue<>(2);
    private final ArrayBlockingQueue<Frame> ready=new ArrayBlockingQueue<>(2);
    private final Thread worker;
    private volatile IOException failure;
    private boolean closed;
    FramePipeline(int size,Sink sink){
        if(size<=0)throw new IllegalArgumentException("frame size");
        free.add(ByteBuffer.allocateDirect(size));free.add(ByteBuffer.allocateDirect(size));
        worker=new Thread(()->{
            try {
                while(true){
                    Frame f=ready.take();if(f.data==null)break;
                    try{sink.encode(f.data,f.timestamp,f.force);}finally{free.add(f.data);}
                }
            }catch(InterruptedException e){Thread.currentThread().interrupt();failure=new IOException("Encoder interrupted",e);}
             catch(Exception e){failure=e instanceof IOException?(IOException)e:new IOException("Encoder worker failed",e);}
        },"i420-encoder");
        worker.setDaemon(true);worker.start();
    }
    void check() throws IOException {if(failure!=null)throw failure;if(closed)throw new IOException("Encoder pipeline closed");}
    ByteBuffer acquire() throws IOException,InterruptedException {
        while(true){check();ByteBuffer b=free.poll(50,TimeUnit.MILLISECONDS);if(b!=null){b.clear();return b;}}
    }
    void submit(ByteBuffer b,int timestamp,int force) throws IOException,InterruptedException {
        check();if(b==null||!b.isDirect()||force<0||force>1)throw new IllegalArgumentException("frame");
        ready.put(new Frame(b,timestamp,force));check();
    }
    public void close() throws IOException,InterruptedException {
        if(closed)return;closed=true;
        long end=System.nanoTime()+3000000000L;
        while(worker.isAlive()&&!ready.offer(new Frame(null,0,0),50,TimeUnit.MILLISECONDS)) {
            if(System.nanoTime()>=end){worker.interrupt();throw new IOException("Encoder output stalled on close");}
        }
        long remaining=end-System.nanoTime();
        if(remaining>0)worker.join(Math.max(1,TimeUnit.NANOSECONDS.toMillis(remaining)));
        if(worker.isAlive()){worker.interrupt();throw new IOException("Encoder output stalled on close");}
        if(failure!=null)throw failure;
    }
    boolean isStopped(){return !worker.isAlive();}
}
