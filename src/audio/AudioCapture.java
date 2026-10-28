import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.media.AudioManager;
import java.io.FileOutputStream;
public final class AudioCapture {
 public static void main(String[] args) throws Exception {
  if(args.length<2 || args.length>3) throw new IllegalArgumentException("PCM pipe, duration seconds, optional self-check");
  int seconds=Integer.parseInt(args[1]); if(seconds<0 || seconds>150)throw new IllegalArgumentException("duration");
  int size=AudioRecord.getMinBufferSize(48000,AudioFormat.CHANNEL_IN_STEREO,AudioFormat.ENCODING_PCM_16BIT);
  if(size<=0)throw new IllegalStateException("audio format unsupported");
  AudioRecord record=new AudioRecord(8,48000,AudioFormat.CHANNEL_IN_STEREO,AudioFormat.ENCODING_PCM_16BIT,Math.max(size,19200));
  try(FileOutputStream out=new FileOutputStream(args[0])) {
   if(record.getState()!=AudioRecord.STATE_INITIALIZED)throw new IllegalStateException("remote submix not initialized");
   record.startRecording();
   if(record.getRecordingState()!=AudioRecord.RECORDSTATE_RECORDING)throw new IllegalStateException("remote submix not recording");
   AudioTrack tone=null;
   try {
    if(args.length==3) {
     byte[] samples=new byte[48000*4*2];
     for(int i=0;i<samples.length/4;i++) {short v=(short)(Math.sin(i*2*Math.PI*440/48000)*3000);for(int c=0;c<2;c++){samples[i*4+c*2]=(byte)v;samples[i*4+c*2+1]=(byte)(v>>8);}}
     tone=new AudioTrack(AudioManager.STREAM_MUSIC,48000,AudioFormat.CHANNEL_OUT_STEREO,AudioFormat.ENCODING_PCM_16BIT,samples.length,AudioTrack.MODE_STATIC);
     tone.write(samples,0,samples.length);tone.play();
    }
    byte[] buffer=new byte[960];long end=seconds==0?Long.MAX_VALUE:System.nanoTime()+seconds*1000000000L;long bytes=0;int peak=0;
    while(System.nanoTime()<end) {
     int n=record.read(buffer,0,buffer.length);
     if(n<=0 || (n&3)!=0)throw new IllegalStateException("audio read "+n);
     for(int i=0;i<n;i+=2){int v=(short)((buffer[i]&255)|(buffer[i+1]<<8));peak=Math.max(peak,Math.abs(v));}
     out.write(buffer,0,n);bytes+=n;
    }
    System.err.println("Remote submix PCM bytes="+bytes+" peak="+peak);
    if(args.length==3 && peak==0)throw new IllegalStateException("self-check captured silence");
   } finally {if(tone!=null)tone.release();record.stop();}
  } finally {record.release();}
  System.exit(0);
 }
}
