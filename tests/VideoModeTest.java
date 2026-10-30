public final class VideoModeTest {
    public static void main(String[] args) {
        VideoMode defaults=new VideoMode(0,0,60);assert defaults.fps==30&&defaults.bitrate==4000;
        VideoMode high=new VideoMode(60,8000,60);assert high.fps==60&&high.bitrate==8000;
        assert new VideoMode(60,8000,30).fps==30;
        assert new VideoMode(30,4000,24).fps==24;
        assert new VideoMode(-1,6000,60).same(defaults);
        assert !high.same(defaults)&&!high.same(null);
        for(int ceiling:new int[]{0,61}){boolean rejected=false;try{new VideoMode(30,4000,ceiling);}catch(IllegalArgumentException e){rejected=true;}assert rejected;}
        System.out.println("PASS: 4 Mbps/30 FPS defaults, live choices, receiver ceiling and invalid-value fallbacks");
    }
}
