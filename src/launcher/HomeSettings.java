package org.bench.boxhome;

import android.app.AlertDialog;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.os.SystemClock;
import android.provider.Settings;
import android.view.*;
import android.widget.*;
import java.lang.reflect.Method;
import java.util.ArrayList;

/** Small live controls over the existing launcher-scoped property API. */
final class HomeSettings {
    private final Home home;
    private final ArrayList<Button> choices=new ArrayList<>();
    private final TextView status;
    private int fps,bitrate;
    private long deadline;
    private AlertDialog dialog;
    private static int property(String name,int fallback) {
        try{return (Integer)Class.forName("android.os.SystemProperties").getMethod("getInt",String.class,int.class).invoke(null,name,fallback);}
        catch(Exception e){return fallback;}
    }
    private int dp(float value){return Math.round(value*home.getResources().getDisplayMetrics().density);}
    private TextView text(String value,int size,int color) {
        TextView v=new TextView(home);v.setText(value);v.setTextSize(size);v.setTextColor(color);return v;
    }
    private LinearLayout column(){LinearLayout v=new LinearLayout(home);v.setOrientation(LinearLayout.VERTICAL);return v;}
    private static GradientDrawable panel(int color,float radius,int stroke) {
        GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(radius);d.setStroke(1,stroke);return d;
    }
    private Button button(String label) {
        Button v=new Button(home);v.setText(label);v.setTextSize(16);v.setAllCaps(false);v.setMinWidth(0);v.setMinimumWidth(0);
        v.setPadding(dp(12),0,dp(12),0);v.setTextColor(Home.TEXT);v.setGravity(Gravity.CENTER);
        v.setBackground(new RippleDrawable(ColorStateList.valueOf(0x40f6a447),panel(0x402e383f,dp(12),0x30ffffff),null));
        return v;
    }
    private HomeSettings(Home home) {
        this.home=home;fps=property("persist.vendor.pi2video.fps",30)==60?60:30;
        bitrate=property("persist.vendor.pi2video.bitrate",4000)==8000?8000:4000;
        LinearLayout body=new LinearLayout(home);body.setPadding(dp(20),dp(8),dp(20),dp(8));
        LinearLayout navigation=column(),video=column();
        body.addView(navigation,new LinearLayout.LayoutParams(0,-2,1));
        LinearLayout.LayoutParams videoLayout=new LinearLayout.LayoutParams(0,-2,1.25f);videoLayout.leftMargin=dp(24);body.addView(video,videoLayout);
        TextView heading=text("HOME",13,0xffbfc8ce);heading.setPadding(0,0,0,dp(10));navigation.addView(heading);
        String[] labels={"Edit home apps","Wi-Fi","Bluetooth","Android settings","Home help"};
        for(int i=0;i<labels.length;i++) {
            final int item=i;Button row=button(labels[i]);row.setGravity(Gravity.CENTER_VERTICAL|Gravity.LEFT);
            LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,dp(48));lp.bottomMargin=dp(6);navigation.addView(row,lp);
            row.setOnClickListener(v->{dialog.dismiss();if(item==0)home.editHomeApps();else if(item==4)home.showHomeHelp();
                else home.startActivity(new Intent(item==1?Settings.ACTION_WIFI_SETTINGS:item==2?Settings.ACTION_BLUETOOTH_SETTINGS:Settings.ACTION_SETTINGS));});
        }
        TextView title=text("VIDEO",13,0xffbfc8ce);title.setPadding(0,0,0,dp(12));video.addView(title);
        addChoices(video,"Bitrate",new int[]{4000,8000},new String[]{"4 Mbps","8 Mbps"},false);
        addChoices(video,"Frame rate",new int[]{30,60},new String[]{"30 FPS","60 FPS"},true);
        TextView note=text("Changes apply live.\nFrame rate follows the display’s limit.",14,0xffbac5cc);note.setPadding(0,dp(14),0,dp(10));video.addView(note);
        status=text("",14,Home.AMBER);status.setMinHeight(dp(42));video.addView(status);
        ScrollView scroll=new ScrollView(home);scroll.setFillViewport(false);scroll.addView(body);
        int available=home.getResources().getDisplayMetrics().heightPixels-dp(150);
        scroll.setLayoutParams(new android.view.ViewGroup.LayoutParams(-1,Math.min(dp(320),Math.max(dp(140),available))));
        dialog=home.new HomeDialog().setTitle("Home settings").setView(scroll).setNegativeButton("Close",null).show();
        updateChoices();deadline=SystemClock.uptimeMillis()+5000;refreshStatus();
    }
    private void addChoices(LinearLayout parent,String label,int[] values,String[] labels,boolean frameRate) {
        TextView title=text(label,18,Home.TEXT);title.setPadding(0,dp(8),0,dp(8));parent.addView(title);
        LinearLayout row=new LinearLayout(home);parent.addView(row);
        for(int i=0;i<values.length;i++) {
            final int value=values[i];Button choice=button(labels[i]);choice.setTag((frameRate?"fps:":"bitrate:")+value);
            LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,dp(48),1);if(i>0)lp.leftMargin=dp(8);row.addView(choice,lp);choices.add(choice);
            choice.setOnClickListener(v->select(frameRate,value));
        }
    }
    private void select(boolean frameRate,int value) {
        int old=frameRate?fps:bitrate;if(old==value)return;
        for(Button b:choices)b.setEnabled(false);status.setText("Applying…");
        new Thread(()->{
            try {
                Method set=Class.forName("android.os.SystemProperties").getMethod("set",String.class,String.class);
                set.invoke(null,frameRate?"persist.vendor.pi2video.fps":"persist.vendor.pi2video.bitrate",String.valueOf(value));
                home.handler.post(()->{if(frameRate)fps=value;else bitrate=value;deadline=SystemClock.uptimeMillis()+5000;updateChoices();refreshStatus();});
            }catch(Exception e){home.handler.post(()->{status.setText("Couldn’t apply video setting. Try again.");for(Button b:choices)b.setEnabled(true);});}
        },"home-video-setting").start();
    }
    private void updateChoices() {
        for(Button b:choices) {
            boolean selected=b.getTag().equals("fps:"+fps)||b.getTag().equals("bitrate:"+bitrate);
            b.setSelected(selected);b.setTextColor(selected?0xff171b1f:Home.TEXT);
            b.setBackground(new RippleDrawable(ColorStateList.valueOf(0x50ffffff),panel(selected?Home.AMBER:0x402e383f,dp(12),selected?Home.AMBER:0x40ffffff),null));
        }
    }
    private void refreshStatus() {
        if(dialog==null||!dialog.isShowing())return;
        boolean applied=property("sys.boxhome.video.applied_fps",0)==fps&&property("sys.boxhome.video.applied_bitrate",0)==bitrate;
        int ceiling=property("sys.pi2video.ceiling",0),actual=ceiling>0?Math.min(fps,ceiling):fps;
        if(ceiling>0)applied&=property("sys.pi2video.active_fps",0)==actual&&property("sys.pi2video.active_bitrate",0)==bitrate;
        if(applied){status.setText("Applied · "+actual+" FPS / "+(bitrate/1000)+" Mbps");for(Button b:choices)b.setEnabled(true);}
        else if(deadline!=0&&SystemClock.uptimeMillis()>deadline){status.setText("Saved. Video hasn’t confirmed the change yet.");for(Button b:choices)b.setEnabled(true);}
        else {status.setText("Applying…");home.handler.postDelayed(this::refreshStatus,200);}
    }
    static void show(Home home){new HomeSettings(home);}
    static void style(Home home,AlertDialog dialog) {
        float density=home.getResources().getDisplayMetrics().density;Window window=dialog.getWindow();
        GradientDrawable glass=new GradientDrawable(GradientDrawable.Orientation.TL_BR,new int[]{0xe82a343b,0xf0182027});
        glass.setCornerRadius(24*density);glass.setStroke(Math.max(1,(int)density),0x38ffffff);
        window.setBackgroundDrawable(glass);window.setDimAmount(.28f);
        window.getDecorView().setClipToOutline(true);
        int width=Math.min(home.getResources().getDisplayMetrics().widthPixels-104-(int)(40*density),(int)(760*density));
        window.setLayout(width,-2);
        WindowManager.LayoutParams attributes=window.getAttributes();attributes.x=52;window.setAttributes(attributes);
        window.getDecorView().setSystemUiVisibility(home.getWindow().getDecorView().getSystemUiVisibility());
        try{Window.class.getMethod("setBackgroundBlurRadius",int.class).invoke(window,24);}catch(Exception ignored){}
        for(int which:new int[]{AlertDialog.BUTTON_POSITIVE,AlertDialog.BUTTON_NEGATIVE,AlertDialog.BUTTON_NEUTRAL}) {
            Button b=dialog.getButton(which);if(b!=null){b.setTextColor(Home.AMBER);b.setAllCaps(false);b.setMinHeight((int)(48*density));}
        }
        TextView message=dialog.findViewById(android.R.id.message);if(message!=null)message.setTextColor(Home.TEXT);
    }
}
