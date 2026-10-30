package org.bench.pi2call;
import android.app.*;
import android.content.*;
import android.net.*;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.util.concurrent.*;
public final class Calls extends Activity {
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private final Handler handler=new Handler(Looper.getMainLooper());
    private TextView status;private EditText number;private volatile boolean visible;private boolean statusPending;
    private String request(String command) throws IOException {
        try(LocalSocket socket=new LocalSocket()) {
            socket.connect(new LocalSocketAddress("pi2call-controls"));socket.setSoTimeout(2000);
            if(socket.getPeerCredentials().getUid()!=0)throw new IOException("Unexpected call service owner");
            socket.getOutputStream().write((command+"\n").getBytes("UTF-8"));
            ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] b=new byte[1024];int n;
            while(out.size()<8192 && (n=socket.getInputStream().read(b))>0)out.write(b,0,n);
            return out.toString("UTF-8");
        }
    }
    private void action(String command) {
        boolean poll=command.equals("status");if(poll && statusPending)return;if(poll)statusPending=true;
        worker.execute(()->{String text;try{text=request(command);}catch(IOException e){text="Call service unavailable";}final String result=text;handler.post(()->{status.setText(result);if(poll){statusPending=false;if(visible)handler.postDelayed(refresh,1500);}});});
    }
    private void phones() {
        worker.execute(()->{
            try {
                String result=request("phones");String[] choices=result.trim().isEmpty()?new String[0]:result.trim().split("\n");
                handler.post(()->{
                    if(choices.length==0)new AlertDialog.Builder(this).setMessage("Pair your phone in Android Bluetooth settings first.").setPositiveButton("Settings",(d,w)->startActivity(new Intent("android.settings.BLUETOOTH_SETTINGS"))).setNegativeButton("Close",null).show();
                    else new AlertDialog.Builder(this).setTitle("Connect phone").setItems(choices,(d,w)->action("connect "+choices[w].substring(0,17))).show();
                });
            }catch(IOException e){handler.post(()->status.setText("Call service unavailable"));}
        });
    }
    private Button button(String text,Runnable run) { Button b=new Button(this);b.setText(text);b.setMinHeight(64);b.setOnClickListener(v->run.run());return b; }
    public void onCreate(Bundle state) {
        super.onCreate(state);getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(24,16,24,16);
        status=new TextView(this);status.setTextSize(20);status.setText("Connecting to call service…");root.addView(status,new LinearLayout.LayoutParams(-1,0,1));
        root.addView(button("Connect phone",this::phones));
        number=new EditText(this);number.setSingleLine(true);number.setHint("Phone number");number.setInputType(3);root.addView(number);
        LinearLayout buttons=new LinearLayout(this);
        for(Button b:new Button[]{button("Call",()->action("dial "+number.getText().toString().replace(" ",""))),button("Answer",()->action("answer")),button("End / Reject",()->action("hangup")),button("Mute",()->action("mute"))})buttons.addView(b,new LinearLayout.LayoutParams(0,-2,1));
        root.addView(buttons);setContentView(root);
    }
    private final Runnable refresh=new Runnable(){public void run(){if(visible)action("status");}};
    protected void onResume(){super.onResume();visible=true;handler.post(refresh);}
    protected void onPause(){visible=false;handler.removeCallbacks(refresh);super.onPause();}
    protected void onDestroy(){worker.shutdownNow();super.onDestroy();}
}
