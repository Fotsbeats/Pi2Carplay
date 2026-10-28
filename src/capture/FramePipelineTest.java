import java.nio.ByteBuffer;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;

public final class FramePipelineTest {
    public static void main(String[] args)throws Exception{
        List<Integer> seen=Collections.synchronizedList(new ArrayList<>());
        try(FramePipeline p=new FramePipeline(16,(b,t,f)->{
            int value=b.getInt(0);try{Thread.sleep(5);}catch(InterruptedException e){throw new IOException(e);}
            if(b.getInt(0)!=value||value!=t||f!=(t==7?1:0))throw new IOException("Buffer overwritten or metadata reordered");
            seen.add(t);
        })){
            for(int i=0;i<100;i++){ByteBuffer b=p.acquire();b.putInt(0,i);p.submit(b,i,i==7?1:0);}
        }
        if(seen.size()!=100)throw new AssertionError("Missing frames");
        for(int i=0;i<100;i++)if(seen.get(i)!=i)throw new AssertionError("Reordered frames");
        FramePipeline failed=new FramePipeline(16,(b,t,f)->{throw new IOException("expected failure");});
        failed.submit(failed.acquire(),0,0);
        boolean propagated=false;
        try{failed.close();}catch(IOException expected){propagated=expected.getMessage().equals("expected failure");}
        if(!propagated)throw new AssertionError("Worker failure lost");
        CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1);
        FramePipeline stalled=new FramePipeline(16,(b,t,f)->{
            entered.countDown();boolean done=false;while(!done)try{release.await();done=true;}catch(InterruptedException ignored){}
        });
        stalled.submit(stalled.acquire(),0,0);entered.await();long start=System.nanoTime();boolean bounded=false;
        try{stalled.close();}catch(IOException expected){bounded=true;}finally{release.countDown();}
        if(!bounded||System.nanoTime()-start>4500000000L)throw new AssertionError("Unbounded shutdown");
        System.out.println("PASS: bounded two-buffer pipeline, FIFO metadata/forced IDR, worker failures and stalled shutdown");
    }
}
