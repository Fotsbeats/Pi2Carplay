import java.io.*;
import java.nio.*;
public final class EncoderSelfCheck {
    private static int nal(byte[] b) throws IOException {
        for(int i=0;i+4<b.length;i++) {
            if(b[i]==0 && b[i+1]==0 && b[i+2]==1) return b[i+3]&31;
            if(b[i]==0 && b[i+1]==0 && b[i+2]==0 && b[i+3]==1) return b[i+4]&31;
        }
        throw new IOException("Missing Annex B NAL");
    }
    public static void main(String[] args) throws Exception {
        if(args.length!=1 || !args[0].equals("/data/local/tmp/pi5-encoder-selfcheck.bin"))
            throw new IllegalArgumentException("self-check scratch path");
        File output=new File(args[0]);
        if(output.exists()) throw new IOException("Self-check scratch file already exists");
        try {
            try(FileOutputStream create=new FileOutputStream(output)) { }
            ByteBuffer frame=ByteBuffer.allocateDirect(1920*720*4).order(ByteOrder.nativeOrder());
            for(int i=0;i<1920*720;i++) frame.putInt(0xff402010);
            frame.clear();
            ByteBuffer yuv=ByteBuffer.allocateDirect((1920*720*3/2));
            DisplayCapture.referenceI420(frame,yuv,1920,720);
            long encoder=DisplayCapture.openEncoder(args[0],1920,720,60);
            try {
                for(int i=0;i<3;i++) DisplayCapture.encodeI420(encoder,yuv,1000+i*33,i==2?1:0);
            } finally { DisplayCapture.closeEncoder(encoder); }
            try(DataInputStream in=new DataInputStream(new FileInputStream(output))) {
                for(int i=0;i<4;i++) {
                    int length=in.readInt(),config=in.readInt(),stamp=in.readInt();
                    if(length<=0 || length>2097152) throw new IOException("Invalid packet length");
                    byte[] payload=new byte[length]; in.readFully(payload);
                    int expected=i==0?7:(i==2?1:5);
                    if(config!=(i==0?1:0) || stamp!=(i==0?0:1000+(i-1)*33) || nal(payload)!=expected)
                        throw new IOException("Config/timestamp/forced-IDR contract mismatch");
                }
                if(in.read()!=-1) throw new IOException("Unexpected extra packet");
            }
            System.out.println("PASS: Android JNI encoder SPS/PPS, IDR/P/forced-IDR and timestamps");
        } finally { if(output.exists() && !output.delete()) throw new IOException("Scratch cleanup failed"); }
    }
}
