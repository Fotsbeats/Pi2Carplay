package org.bench.pi2call;

import android.bluetooth.*;
import android.content.Context;
import android.media.*;
import android.net.*;
import android.os.Looper;
import java.io.*;
import java.lang.reflect.*;
import java.util.*;

/** Root app_process helper: HFP signalling stays in Android; only PCM uses the private sockets. */
public final class CallAudio {
    private static Context context;
    private static AudioManager audioManager;
    private static volatile BluetoothProfile hf;
    private static volatile BluetoothProfile phonebook;
    private static volatile BluetoothDevice phone;
    private static volatile boolean call,carReady,duplexReady,muted,nativeReady;
    private static volatile String error="",calls="",phoneName="No phone connected";
    private static Boolean routeAllowed;
    private static long nextReconnect;
    private static volatile long nextPhonebook;
    private static int bluetoothRate=8000,captureSources;
    private static boolean appRecording,appPlayback;
    private static final CallDemand demand=new CallDemand();
    private static final Object audioLock=new Object();
    private static AudioTrack injectedMic;
    private static Object micPolicy;
    private static AudioRecord voicePlayback;
    private static final Transport hci=new Transport("hci"),car=new Transport("carplay");
    private static Object invoke(Object object,String name,Class<?>[] signature,Object... args) throws Exception {
        return object.getClass().getMethod(name,signature).invoke(object,args);
    }
    private static Object hf(String name,Class<?>[] signature,Object... args) throws Exception {
        BluetoothProfile profile=hf;if(profile==null)throw new IllegalStateException("Hands-free client unavailable");
        return invoke(profile,name,signature,args);
    }
    private static Object deviceMethod(String name,Object... rest) throws Exception {
        BluetoothDevice p=phone;if(p==null)throw new IllegalStateException("Connect a phone first");
        Class<?>[] signature=new Class<?>[rest.length+1];Object[] args=new Object[rest.length+1];
        signature[0]=BluetoothDevice.class;args[0]=p;
        for(int i=0;i<rest.length;i++) { args[i+1]=rest[i];signature[i+1]=rest[i] instanceof Boolean?boolean.class:rest[i] instanceof Integer?int.class:String.class; }
        return hf(name,signature,args);
    }
    private static void log(String message) { System.err.println("Pi2Call: "+message); }
    private static void fail(String message) {
        error=message;log(message);endCall();
        try { deviceMethod("disconnectAudio"); }catch(Exception ignored) {}
    }
    private static void syncCapture() {
        synchronized(audioLock) {
            demand.car=carReady;demand.apps=appRecording||appPlayback;
            int wanted=error.isEmpty()?demand.sources():0;
            if(wanted!=0 && captureSources==0) {
                if(!CallResampler.reset()){error="System speech resampler unavailable";return;}
                duplexReady=false;
                if(!car.send(1,null,0)){error="CarPlay microphone transport unavailable";return;}
            } else if(wanted==0 && captureSources!=0) {
                car.send(3,null,0);duplexReady=false;
                if(injectedMic!=null){injectedMic.pause();injectedMic.flush();}
            }
            if(captureSources!=wanted)log("CarPlay voice demand: "+captureSources+" -> "+wanted);
            captureSources=wanted;
        }
    }
    private static void startCall(int rate) {
        synchronized(audioLock) {
            if(!carReady) { fail("CarPlay is unavailable; returning call audio to phone");return; }
            if(!CallResampler.reset()) { fail("System speech resampler unavailable");return; }
            bluetoothRate=rate;call=true;muted=false;error="";demand.bluetooth(true);syncCapture();
            log("Bluetooth voice connected: "+(rate==16000?"mSBC wideband 16000 Hz":"CVSD narrowband 8000 Hz"));
        }
    }
    private static void endCall() {
        synchronized(audioLock) {
            call=false;muted=false;demand.bluetooth(false);syncCapture();
        }
    }
    private static final class Transport extends Thread {
        final String name;volatile LocalSocket socket;String lastFailure="";
        Transport(String name) { super("Call-"+name);this.name=name; }
        synchronized boolean send(int type,byte[] data,int n) {
            LocalSocket s=socket;if(s==null || n<0 || n>960)return false;
            byte[] frame=new byte[n+4];frame[0]=(byte)type;frame[2]=(byte)n;frame[3]=(byte)(n>>8);
            if(n>0)System.arraycopy(data,0,frame,4,n);
            try { s.getOutputStream().write(frame);return true; }catch(IOException e) { try{s.close();}catch(IOException ignored){}return false; }
        }
        public void run() {
            for(;;) {
                try(LocalSocket s=new LocalSocket(LocalSocket.SOCKET_SEQPACKET)) {
                    s.connect(new LocalSocketAddress("/data/vendor/pi2call/"+name+".sock",LocalSocketAddress.Namespace.FILESYSTEM));
                    s.setSendBufferSize(4096);s.setReceiveBufferSize(8192);
                    int uid=s.getPeerCredentials().getUid();if(uid!=(name.equals("hci")?1002:0))throw new IOException("Unexpected transport owner");
                    socket=s;
                    log(name+" transport connected");lastFailure="";
                    if(name.equals("hci")) { sendTarget();send(7,null,0); }
                    byte[] frame=new byte[1025],pcm=new byte[960],converted=new byte[2048];int n;
                    while((n=s.getInputStream().read(frame))>0) {
                        if(n<4 || n>964 || frame[1]!=0 || ((frame[2]&255)|((frame[3]&255)<<8))!=n-4)throw new IOException("Invalid call frame");
                        int type=frame[0]&255,bytes=n-4;
                        if(type==5) { fail(bytes==0?name+" call audio failed":new String(frame,4,bytes,"UTF-8"));continue; }
                        if(name.equals("hci")) {
                            if(type==8 && bytes==2)nativeReady=frame[4]==1 && frame[5]==1;
                            else if(type==1) {
                                if(bytes!=4)throw new IOException("Unsupported call format");
                                int rate=(frame[4]&255)|((frame[5]&255)<<8)|((frame[6]&255)<<16)|(frame[7]<<24);
                                if(rate!=8000 && rate!=16000)throw new IOException("Unsupported call rate");startCall(rate);
                            }
                            else if(type==3) {
                                if(bytes==20){StringBuilder stats=new StringBuilder("Bluetooth voice ended:");for(int i=0;i<5;i++){int at=4+i*4;long value=((frame[at]&255L)|((frame[at+1]&255L)<<8)|((frame[at+2]&255L)<<16)|((frame[at+3]&255L)<<24));stats.append(' ').append(new String[]{"rx=","tx=","underruns=","drops=","lost_frames="}[i]).append(value);}log(stats.toString());}
                                endCall();
                            }
                            else if(type==2 && bytes>0 && (bytes&1)==0) {
                                synchronized(audioLock) {
                                    if(!call || !duplexReady)continue;
                                    System.arraycopy(frame,4,pcm,0,bytes);int count=bluetoothRate==16000?bytes:CallResampler.convert(0,pcm,bytes,converted);
                                    if(count<0 || count>960 || !car.send(2,bluetoothRate==16000?pcm:converted,count))fail("Call playback transport failed");
                                }
                            }
                        } else {
                            if(type==4) { synchronized(audioLock){carReady=true;if(captureSources!=0)duplexReady=true;syncCapture();} }
                            else if(type==2 && bytes>0 && (bytes&1)==0) {
                                synchronized(audioLock) {
                                    if(captureSources==0 || !duplexReady)continue;
                                    System.arraycopy(frame,4,pcm,0,bytes);if(muted || audioManager.isMicrophoneMute())Arrays.fill(pcm,0,bytes,(byte)0);
                                    if(appRecording && injectedMic!=null) { injectedMic.play();injectedMic.write(pcm,0,bytes,AudioTrack.WRITE_NON_BLOCKING); }
                                    if(call) {
                                        int count=bluetoothRate==16000?bytes:CallResampler.convert(1,pcm,bytes,converted);
                                        if(count<0 || count>960 || !hci.send(4,bluetoothRate==16000?pcm:converted,count))fail("Call microphone transport failed");
                                    }
                                }
                            }
                        }
                    }
                } catch(Exception e) {
                    String failure=e.getClass().getSimpleName()+": "+String.valueOf(e.getMessage());
                    if(!failure.equals(lastFailure)) { log(name+" transport: "+failure);lastFailure=failure; }
                }
                finally {
                    socket=null;
                    if(name.equals("hci"))nativeReady=false;
                    if(name.equals("carplay")){carReady=false;syncCapture();}
                    if(call)fail(name+" transport disconnected");
                }
                try { Thread.sleep(1000); }catch(InterruptedException e) { return; }
            }
        }
    }
    private static void sendTarget() {
        BluetoothDevice p=phone;
        if(p==null) { hci.send(6,null,0);return; }
        String[] parts=p.getAddress().split(":");byte[] address=new byte[6];
        for(int i=0;i<6;i++)address[5-i]=(byte)Integer.parseInt(parts[i],16);
        hci.send(0,address,6);
    }
    @SuppressWarnings("unchecked") private static void pollPhone() throws Exception {
        List<BluetoothDevice> connected=(List<BluetoothDevice>)hf("getConnectedDevices",new Class<?>[0]);
        BluetoothDevice selected=connected.isEmpty()?null:connected.get(0);
        if(!Objects.equals(phone,selected)) { synchronized(audioLock){demand.phoneCall(false);endCall();}phone=selected;routeAllowed=null;nextPhonebook=0;phoneName=selected==null?"No phone connected":String.valueOf(selected.getName());sendTarget();log(selected==null?"Phone disconnected":"Hands-free phone connected"); }
        if(selected==null) {
            calls="";long now=System.nanoTime()/1000000L;
            if(now>=nextReconnect) {
                nextReconnect=now+30000;BluetoothDevice preferred=null;
                for(BluetoothDevice d:BluetoothAdapter.getDefaultAdapter().getBondedDevices()) {
                    if((Integer)hf("getConnectionPolicy",new Class<?>[]{BluetoothDevice.class},d)!=100)continue;
                    if(preferred!=null) { preferred=null;break; }
                    preferred=d;
                }
                if(preferred!=null)hf("connect",new Class<?>[]{BluetoothDevice.class},preferred);
            }
            return;
        }
        if(routeAllowed==null || routeAllowed!=carReady) { deviceMethod("setAudioRouteAllowed",carReady);routeAllowed=carReady; }
        long now=System.nanoTime()/1000000L;BluetoothProfile directory=phonebook;
        if(directory!=null && now>=nextPhonebook) {
            nextPhonebook=now+30000;
            try {
                if((Integer)invoke(directory,"getConnectionPolicy",new Class<?>[]{BluetoothDevice.class},selected)==100 && directory.getConnectionState(selected)==BluetoothProfile.STATE_DISCONNECTED) {
                    invoke(directory,"connect",new Class<?>[]{BluetoothDevice.class},selected);log("Phonebook reconnect requested for hands-free phone");
                }
            }catch(Exception e){log("Phonebook reconnect unavailable: "+e.getClass().getSimpleName());}
        }
        List<?> current=(List<?>)deviceMethod("getCurrentCalls");StringBuilder summary=new StringBuilder();boolean live=false;
        if(current!=null)for(Object entry:current) {
            int state=(Integer)invoke(entry,"getState",new Class<?>[0]);
            if(state!=7)live=true;
            String number=String.valueOf(invoke(entry,"getNumber",new Class<?>[0]));
            if(summary.length()>0)summary.append("; ");
            String[] states={"Active","Held","Dialing","Ringing","Incoming","Waiting","Held","Ended"};
            summary.append(state>=0&&state<states.length?states[state]:"Call").append(" ").append(number);
        }
        calls=summary.toString();
        synchronized(audioLock){demand.phoneCall(live);syncCapture();}
    }
    private static void installMic() throws Exception {
        Class<?> ruleBuilder=Class.forName("android.media.audiopolicy.AudioMixingRule$Builder");
        Object rules=ruleBuilder.getConstructor().newInstance();
        for(int source:new int[]{0,1,5,6,7,9}) {
            AudioAttributes.Builder attributes=new AudioAttributes.Builder();
            invoke(attributes,"setInternalCapturePreset",new Class<?>[]{int.class},source);
            invoke(rules,"addRule",new Class<?>[]{AudioAttributes.class,int.class},attributes.build(),2);
        }
        Object rule=invoke(rules,"build",new Class<?>[0]);Class<?> ruleType=Class.forName("android.media.audiopolicy.AudioMixingRule");
        Object mixBuilder=Class.forName("android.media.audiopolicy.AudioMix$Builder").getConstructor(ruleType).newInstance(rule);
        AudioFormat format=new AudioFormat.Builder().setSampleRate(16000).setEncoding(AudioFormat.ENCODING_PCM_16BIT).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build();
        invoke(mixBuilder,"setFormat",new Class<?>[]{AudioFormat.class},format);invoke(mixBuilder,"setRouteFlags",new Class<?>[]{int.class},2);
        Object mix=invoke(mixBuilder,"build",new Class<?>[0]);
        Object policyBuilder=Class.forName("android.media.audiopolicy.AudioPolicy$Builder").getConstructor(Context.class).newInstance(context);
        invoke(policyBuilder,"addMix",new Class<?>[]{Class.forName("android.media.audiopolicy.AudioMix")},mix);
        Object voiceRules=ruleBuilder.getConstructor().newInstance();
        for(int usage:new int[]{AudioAttributes.USAGE_VOICE_COMMUNICATION,AudioAttributes.USAGE_VOICE_COMMUNICATION_SIGNALLING})invoke(voiceRules,"addRule",new Class<?>[]{AudioAttributes.class,int.class},new AudioAttributes.Builder().setUsage(usage).build(),1);
        invoke(voiceRules,"voiceCommunicationCaptureAllowed",new Class<?>[]{boolean.class},true);
        Object voiceBuilder=Class.forName("android.media.audiopolicy.AudioMix$Builder").getConstructor(ruleType).newInstance(invoke(voiceRules,"build",new Class<?>[0]));
        // CarPlay owns voice playback; duplicating to the primary output mixes independent clocks.
        invoke(voiceBuilder,"setFormat",new Class<?>[]{AudioFormat.class},format);invoke(voiceBuilder,"setRouteFlags",new Class<?>[]{int.class},2);
        Object voiceMix=invoke(voiceBuilder,"build",new Class<?>[0]);
        invoke(policyBuilder,"addMix",new Class<?>[]{Class.forName("android.media.audiopolicy.AudioMix")},voiceMix);
        micPolicy=invoke(policyBuilder,"build",new Class<?>[0]);
        Object manager=context.getSystemService(Context.AUDIO_SERVICE);
        if((Integer)invoke(manager,"registerAudioPolicy",new Class<?>[]{Class.forName("android.media.audiopolicy.AudioPolicy")},micPolicy)!=0)throw new IOException("Microphone injection registration failed");
        injectedMic=(AudioTrack)invoke(micPolicy,"createAudioTrackSource",new Class<?>[]{Class.forName("android.media.audiopolicy.AudioMix")},mix);
        if(injectedMic==null || injectedMic.getState()!=AudioTrack.STATE_INITIALIZED)throw new IOException("Microphone injection unavailable");
        voicePlayback=(AudioRecord)invoke(micPolicy,"createAudioRecordSink",new Class<?>[]{Class.forName("android.media.audiopolicy.AudioMix")},voiceMix);
        if(voicePlayback==null || voicePlayback.getState()!=AudioRecord.STATE_INITIALIZED)throw new IOException("App voice playback capture unavailable");
        log("Android microphone injection registered");
    }
    private static void watchApps() {
        AudioManager manager=(AudioManager)context.getSystemService(Context.AUDIO_SERVICE);
        manager.registerAudioRecordingCallback(new AudioManager.AudioRecordingCallback(){public void onRecordingConfigChanged(List<AudioRecordingConfiguration> configs){
            boolean active=false;for(AudioRecordingConfiguration c:configs){int source=c.getClientAudioSource();if((source==0 || source==1 || source==5 || source==6 || source==7 || source==9) && !c.isClientSilenced())active=true;}
            synchronized(audioLock){if(appRecording!=active){appRecording=active;error="";log("Android microphone demand: "+active);syncCapture();}}
        }},new android.os.Handler(Looper.getMainLooper()));
        manager.registerAudioPlaybackCallback(new AudioManager.AudioPlaybackCallback(){public void onPlaybackConfigChanged(List<AudioPlaybackConfiguration> configs){
            boolean active=false;for(AudioPlaybackConfiguration c:configs){int usage=c.getAudioAttributes().getUsage();try{if((usage==2 || usage==3) && (Integer)invoke(c,"getPlayerState",new Class<?>[0])==2)active=true;}catch(Exception ignored){}}
            synchronized(audioLock){if(appPlayback!=active){appPlayback=active;error="";log("Android voice playback demand: "+active);syncCapture();}}
        }},new android.os.Handler(Looper.getMainLooper()));
        voicePlayback.startRecording();
        new Thread(()->{byte[] pcm=new byte[320];for(;;){try{int n=voicePlayback.read(pcm,0,pcm.length,AudioRecord.READ_NON_BLOCKING);synchronized(audioLock){if(n>0 && appPlayback && !call && duplexReady && !car.send(2,pcm,n))fail("App voice playback transport failed");}Thread.sleep(5);}catch(Exception e){log("App voice playback reader failed");return;}}},"App-voice-playback").start();
    }
    private static String status() {
        return (carReady?"CarPlay connected":"CarPlay not connected")+"\n"+phoneName+"\n"+(calls.isEmpty()?"No call":calls)+"\n"+(muted?"Microphone muted":duplexReady?(call?(bluetoothRate==16000?"Wideband call audio active":"Narrowband call audio active"):(appRecording||appPlayback?"App voice audio active":"Phone audio on handset")):nativeReady?"Bluetooth audio ready":"Bluetooth audio starting")+"\n"+error;
    }
    private static String command(String command) throws Exception {
        if(command.equals("status"))return status();
        if(command.equals("phones")) {
            StringBuilder result=new StringBuilder();
            for(BluetoothDevice d:BluetoothAdapter.getDefaultAdapter().getBondedDevices())result.append(d.getAddress()).append(' ').append(String.valueOf(d.getName()).replace('\n',' ')).append('\n');
            return result.toString();
        }
        if(command.startsWith("connect ")) {
            String address=command.substring(8);if(!BluetoothAdapter.checkBluetoothAddress(address))throw new IOException("Invalid phone selection");
            BluetoothDevice d=BluetoothAdapter.getDefaultAdapter().getRemoteDevice(address);
            if(d.getBondState()!=BluetoothDevice.BOND_BONDED)throw new IOException("Pair the phone in Android Bluetooth settings first");
            if(phone!=null && !phone.equals(d))throw new IOException("Disconnect the current phone in Bluetooth settings before switching");
            hf("setConnectionPolicy",new Class<?>[]{BluetoothDevice.class,int.class},d,100);
            if(!(Boolean)hf("connect",new Class<?>[]{BluetoothDevice.class},d))throw new IOException("Phone connection request rejected");
        } else if(command.startsWith("dial ")) {
            String number=command.substring(5);if(!number.matches("[+*#0-9]{1,40}"))throw new IOException("Invalid number");
            if(!carReady)throw new IOException("Connect CarPlay before calling");deviceMethod("dial",number);
        } else if(command.equals("answer")) { if(!carReady)throw new IOException("Connect CarPlay before answering");deviceMethod("acceptCall",0); }
        else if(command.equals("hangup")) {
            Class<?> callType=Class.forName("android.bluetooth.BluetoothHeadsetClientCall");
            hf("terminateCall",new Class<?>[]{BluetoothDevice.class,callType},phone,null);
        } else if(command.equals("mute")) { if(!call)throw new IOException("No call audio is active");muted=!muted; }
        else throw new IOException("Unknown call command");
        return status();
    }
    private static void controls() throws Exception {
        try(LocalServerSocket server=new LocalServerSocket("pi2call-controls")) {
            for(;;)try(LocalSocket peer=server.accept()) {
                int uid=peer.getPeerCredentials().getUid();
                if(uid!=0 && uid!=context.getPackageManager().getApplicationInfo("org.bench.pi2call",0).uid)continue;
                peer.setSoTimeout(1500);InputStream input=peer.getInputStream();ByteArrayOutputStream text=new ByteArrayOutputStream();int c;
                while(text.size()<160 && (c=input.read())!=-1 && c!='\n')text.write(c);
                String response;
                try { response=text.size()==160?"Command too long":command(text.toString("UTF-8")); }
                catch(Exception e) { Throwable cause=e instanceof InvocationTargetException?e.getCause():e;response="Error: "+cause.getClass().getSimpleName()+": "+String.valueOf(cause.getMessage()); }
                peer.getOutputStream().write(response.getBytes("UTF-8"));
            }catch(Exception e) { log("Control connection closed"); }
        }
    }
    public static void main(String[] args) throws Exception {
        if(args.length==1 && args[0].equals("--status")) {
            try(LocalSocket socket=new LocalSocket()) {
                socket.connect(new LocalSocketAddress("pi2call-controls"));socket.setSoTimeout(2000);
                if(socket.getPeerCredentials().getUid()!=0)throw new IOException("Unexpected call service owner");
                socket.getOutputStream().write("status\n".getBytes("UTF-8"));
                byte[] buffer=new byte[1024];int n;while((n=socket.getInputStream().read(buffer))>0)System.out.write(buffer,0,n);
            } catch(IOException e) { System.err.println("Calls service unavailable");System.exit(1); }
            System.exit(0);
        }
        Looper.prepareMainLooper();
        Class<?> thread=Class.forName("android.app.ActivityThread");Object activity=thread.getMethod("systemMain").invoke(null);
        context=(Context)thread.getMethod("getSystemContext").invoke(activity);
        audioManager=(AudioManager)context.getSystemService(Context.AUDIO_SERVICE);
        if(args.length==1 && args[0].equals("--resampler-check")) {
            if(!CallResampler.reset())throw new IOException("Resampler unavailable");byte[] in=new byte[320],out=new byte[2048];
            for(int i=0;i<in.length;i+=2){short v=(short)(Math.sin(i*Math.PI*440/8000)*4000);in[i]=(byte)v;in[i+1]=(byte)(v>>8);}
            int total=0,down=0,peak=0;for(int i=0;i<20;i++){int n=CallResampler.convert(0,in,in.length,out);if(n<0 || n>640 || (n&1)!=0)throw new IOException("Upsample length "+n);total+=n;byte[] reduced=new byte[2048];int k=CallResampler.convert(1,out,n,reduced);if(k<0 || k>320)throw new IOException("Downsample length "+k);down+=k;for(int j=0;j<k;j+=2)peak=Math.max(peak,Math.abs((short)((reduced[j]&255)|(reduced[j+1]<<8))));}
            if(total<12000 || down<5800 || peak<100)throw new IOException("Resampler output invalid");
            if(CallResampler.convert(0,in,3,out)!=-1)throw new IOException("Odd PCM accepted");
            log("PASS system speech resampler: bytes="+total+" down="+down+" peak="+peak);System.exit(0);
        }
        installMic();
        if(args.length==1 && args[0].equals("--mic-check")) {
          for(int source:new int[]{1,7,8}) {
            AudioRecord record=source==8?voicePlayback:new AudioRecord(source,16000,AudioFormat.CHANNEL_IN_MONO,AudioFormat.ENCODING_PCM_16BIT,8192);
            AudioTrack writer=source==8?new AudioTrack(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION).build(),new AudioFormat.Builder().setSampleRate(16000).setEncoding(AudioFormat.ENCODING_PCM_16BIT).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build(),8192,AudioTrack.MODE_STREAM,0):injectedMic;
            byte[] pattern=new byte[320],captured=new byte[16384];int seed=1234567,used=0;
            for(int i=0;i<pattern.length;i++){seed=seed*1664525+1013904223;pattern[i]=(byte)(seed>>24);}
            try {
                record.startRecording();writer.play();
                for(int i=0;i<100;i++) {
                    writer.write(pattern,0,pattern.length,AudioTrack.WRITE_NON_BLOCKING);
                    int got=record.read(captured,used,captured.length-used,AudioRecord.READ_NON_BLOCKING);
                    if(got<0)throw new IOException("Synthetic microphone read failed");used+=got;
                    if(used>=captured.length)break;Thread.sleep(10);
                }
            } finally { record.stop();record.release();writer.pause();writer.flush();if(source==8)writer.release(); }
            boolean matched=false;
            for(int offset=0;offset+64<=used && !matched;offset++) {
                matched=true;for(int i=0;i<64;i++)if(captured[offset+i]!=pattern[i]){matched=false;break;}
            }
            if(!matched)throw new IOException("Synthetic audio path failed: source="+source+" bytes="+used);
            log("PASS synthetic audio source "+source);
          }
            injectedMic.release();
            invoke(context.getSystemService(Context.AUDIO_SERVICE),"unregisterAudioPolicy",new Class<?>[]{Class.forName("android.media.audiopolicy.AudioPolicy")},micPolicy);
            log("PASS synthetic PCM reached Android microphone; no vehicle microphone recording performed");System.exit(0);
        }
        // app_process does not run the zygote's mainline Bluetooth initialization.
        Class<?> initializer=Class.forName("android.bluetooth.BluetoothFrameworkInitializer");
        if(initializer.getMethod("getBluetoothServiceManager").invoke(null)==null) {
            Class<?> manager=Class.forName("android.os.BluetoothServiceManager");
            initializer.getMethod("setBluetoothServiceManager",manager).invoke(null,manager.getConstructor().newInstance());
        }
        BluetoothAdapter adapter=BluetoothAdapter.getDefaultAdapter();
        if(adapter==null || !adapter.getProfileProxy(context,new BluetoothProfile.ServiceListener() {
            public void onServiceConnected(int profile,BluetoothProfile proxy) { hf=proxy;log("Hands-free client service ready"); }
            public void onServiceDisconnected(int profile) { hf=null;phone=null;synchronized(audioLock){demand.phoneCall(false);endCall();}log("Hands-free client service lost"); }
        },16))throw new IOException("Hands-free client profile unavailable");
        if(new File("/system/priv-app/Pi2CarDialer/CarDialerApp.apk").isFile()) {
            if(!adapter.getProfileProxy(context,new BluetoothProfile.ServiceListener(){
                public void onServiceConnected(int profile,BluetoothProfile proxy){phonebook=proxy;nextPhonebook=0;log("Phonebook client service ready");}
                public void onServiceDisconnected(int profile){phonebook=null;}
            },17))log("Phonebook client profile unavailable");
        }
        hci.start();car.start();watchApps();
        new Thread(()->{try{controls();}catch(Exception e){log("Call controls unavailable");}},"Call-controls").start();
        new Thread(()->{String last="";for(;;){try{if(hf!=null)pollPhone();Class.forName("android.os.SystemProperties").getMethod("set",String.class,String.class).invoke(null,"sys.pi2call.ready",hf!=null&&nativeReady?"1":"0");last="";Thread.sleep(500);}catch(Exception e){error="Hands-free state unavailable";String kind=e.getClass().getSimpleName();if(!kind.equals(last)){log("Hands-free poll failed: "+kind);last=kind;}try{Thread.sleep(1000);}catch(InterruptedException ignored){return;}}}},"Call-state").start();
        log("Call audio helper ready; waiting for phone and CarPlay");Looper.loop();
    }
}
