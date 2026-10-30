package org.bench.pi2call;
import android.content.Context;
import android.media.*;
import android.os.Looper;
import java.lang.reflect.*;

/** Synthetic app playback only: verify downmix/resampling and the PCM clock on the Pi. */
public final class CallPlaybackTest {
    private static void field(String name,Object value)throws Exception {
        Field f=CallAudio.class.getDeclaredField(name);f.setAccessible(true);f.set(null,value);
    }
    public static void main(String[] args)throws Exception {
        Looper.prepareMainLooper();Class<?> thread=Class.forName("android.app.ActivityThread");
        Object activity=thread.getMethod("systemMain").invoke(null);
        Context context=(Context)thread.getMethod("getSystemContext").invoke(activity);
        AudioManager manager=(AudioManager)context.getSystemService(Context.AUDIO_SERVICE);
        field("context",context);field("audioManager",manager);
        Method install=CallAudio.class.getDeclaredMethod("installMic");install.setAccessible(true);install.invoke(null);
        Field sink=CallAudio.class.getDeclaredField("voicePlayback");sink.setAccessible(true);
        AudioRecord record=(AudioRecord)sink.get(null);
        if(record.getSampleRate()!=16000 || record.getChannelCount()!=1 || record.getAudioFormat()!=2)
            throw new AssertionError("Unexpected captured voice format");
        int oldMode=manager.getMode();
        AudioRecord media=new AudioRecord(8,48000,AudioFormat.CHANNEL_IN_STEREO,2,19200);
        byte[] mediaPcm=new byte[8192];media.startRecording();
        try {
            for(int rate:new int[]{16000,44100,48000}) {
                manager.setMode(AudioManager.MODE_IN_COMMUNICATION);
                byte[] tone=new byte[rate*4];
                for(int i=0;i<rate;i++) {
                    short sample=(short)(Math.sin(i*2*Math.PI*440/rate)*6000);
                    for(int c=0;c<2;c++){tone[i*4+c*2]=(byte)sample;tone[i*4+c*2+1]=(byte)(sample>>8);}
                }
                AudioTrack track=new AudioTrack(new AudioAttributes.Builder().setUsage(2).build(),
                    new AudioFormat.Builder().setSampleRate(rate).setChannelMask(AudioFormat.CHANNEL_OUT_STEREO).setEncoding(2).build(),tone.length,AudioTrack.MODE_STATIC,0);
                byte[] pcm=new byte[32000];int used=0;
                record.startRecording();track.write(tone,0,tone.length);track.setLoopPoints(0,rate,-1);track.play();
                long warmup=System.nanoTime()+1500000000L;
                while(System.nanoTime()<warmup){record.read(pcm,0,pcm.length,AudioRecord.READ_NON_BLOCKING);media.read(mediaPcm,0,mediaPcm.length,AudioRecord.READ_NON_BLOCKING);Thread.sleep(2);}
                long began=System.nanoTime(),end=began+1000000000L;
                int mediaBytes=0;
                while(System.nanoTime()<end) {
                    int n=record.read(pcm,used,pcm.length-used,AudioRecord.READ_NON_BLOCKING);
                    if(n<0)throw new AssertionError("Voice read failed: "+n);
                    int m=media.read(mediaPcm,0,mediaPcm.length,AudioRecord.READ_NON_BLOCKING);
                    if(m<0)throw new AssertionError("Media clock read failed: "+m);
                    mediaBytes+=m;used+=n;if(used==pcm.length)break;Thread.sleep(2);
                }
                long elapsed=(System.nanoTime()-began)/1000000;
                track.stop();track.release();record.stop();
                int crossings=0,active=0,jumps=0;short previous=0;
                for(int i=0;i+1<used;i+=2) {
                    short sample=(short)((pcm[i]&255)|(pcm[i+1]<<8));
                    if(Math.abs(sample)>100)active++;
                    if(i>0 && Math.abs(sample-previous)>2000)jumps++;
                    if(previous<0 && sample>=0)crossings++;previous=sample;
                }
                double hz=crossings*16000.0/(used/2);
                if(used<30000 || elapsed<950 || hz<435 || hz>445 || active<used/5 || jumps>2)
                    throw new AssertionError("Voice clock/tone: rate="+rate+" bytes="+used+" elapsed="+elapsed+" hz="+hz+" discontinuities="+jumps);
                if(mediaBytes<180000 || mediaBytes>205000)throw new AssertionError("Media clock changed: "+mediaBytes);
                System.out.println("PASS: stereo "+rate+" Hz -> mono 16000 Hz: bytes="+used+" elapsed_ms="+elapsed+" tone_hz="+hz+" discontinuities="+jumps+" media_clock_bytes="+mediaBytes);
            }
        } finally {manager.setMode(oldMode);record.release();media.stop();media.release();}
        System.exit(0);
    }
}
