/** User settings are caps; the receiver's negotiated frame rate remains authoritative. */
final class VideoMode {
    final int fps,bitrate;
    VideoMode(int requestedFps,int requestedBitrate,int ceiling) {
        if(ceiling<1||ceiling>60)throw new IllegalArgumentException("Receiver frame rate");
        fps=Math.min(requestedFps==60?60:30,ceiling);
        bitrate=requestedBitrate==8000?8000:4000;
    }
    boolean same(VideoMode other){return other!=null&&fps==other.fps&&bitrate==other.bitrate;}
}
