package com.android.car.dialer.ui;

import android.app.KeyguardManager;
import android.content.Context;
import android.graphics.*;
import android.graphics.drawable.*;
import android.os.*;
import android.provider.Settings;
import android.telecom.*;
import android.text.TextUtils;
import android.text.format.DateUtils;
import android.view.*;
import android.widget.*;
import java.util.*;

/** A call-owned window: leaving Phone keeps controls visible; ending the call removes it. */
public final class PiCallBar {
    private static PiCallBar instance;
    private static boolean phoneVisible;
    private final InCallService service;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final WindowManager windows;
    private final Set<Call> watched=new HashSet<>();
    private View panel;
    private TextView title,timer;
    private Button mute;
    private Call current;
    private long connectedAt;
    private final Runnable tick=()->refresh();
    private final Call.Callback callback=new Call.Callback(){
        public void onStateChanged(Call call,int state){refresh();}
        public void onDetailsChanged(Call call,Call.Details details){refresh();}
        public void onCallDestroyed(Call call){refresh();}
    };
    private PiCallBar(InCallService service){this.service=service;windows=(WindowManager)service.getSystemService(Context.WINDOW_SERVICE);}
    public static void attach(InCallService service){if(instance!=null)instance.destroy();instance=new PiCallBar(service);instance.refresh();}
    public static void changed(InCallService service){if(instance!=null && instance.service==service)instance.refresh();}
    public static void detach(InCallService service){if(instance!=null && instance.service==service){instance.destroy();instance=null;}}
    public static void phoneVisible(boolean visible){phoneVisible=visible;if(instance!=null)instance.refresh();}
    private int dp(float value){return Math.round(value*service.getResources().getDisplayMetrics().density);}
    private GradientDrawable background(int color,float radius){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(radius));return d;}
    private Button button(String text,String description,int color,View.OnClickListener click){
        Button b=new Button(service);b.setText(text);b.setTextSize(14);b.setAllCaps(false);b.setTextColor(Color.WHITE);b.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));
        b.setMinWidth(0);b.setMinimumWidth(0);b.setMinHeight(0);b.setMinimumHeight(0);b.setPadding(dp(8),0,dp(8),0);b.setContentDescription(description);
        b.setBackground(new RippleDrawable(android.content.res.ColorStateList.valueOf(0x33ffffff),background(color,16),null));b.setOnClickListener(click);return b;
    }
    private void openPhone(){((TelecomManager)service.getSystemService(Context.TELECOM_SERVICE)).showInCallScreen(false);}
    private void show(){
        if(panel!=null || !Settings.canDrawOverlays(service))return;
        android.util.DisplayMetrics metrics=new android.util.DisplayMetrics();windows.getDefaultDisplay().getRealMetrics(metrics);
        int available=metrics.widthPixels-104;boolean compact=available<dp(300);
        // shortcut: exceptionally small canvases retain the native call notification.
        if(available<dp(124) || metrics.heightPixels<dp(compact?120:88))return;
        LinearLayout row=new LinearLayout(service);row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(dp(10),dp(8),dp(8),dp(8));row.setElevation(dp(12));row.setBackground(background(0xff171c25,22));
        LinearLayout info=new LinearLayout(service);info.setOrientation(LinearLayout.VERTICAL);info.setGravity(Gravity.CENTER_VERTICAL);info.setPadding(dp(10),0,dp(8),0);info.setContentDescription("Return to phone call");info.setClickable(true);info.setFocusable(true);info.setOnClickListener(v->openPhone());
        title=new TextView(service);title.setTextSize(16);title.setTextColor(Color.WHITE);title.setTypeface(Typeface.create("sans-serif-medium",0));title.setSingleLine(true);title.setEllipsize(TextUtils.TruncateAt.END);
        timer=new TextView(service);timer.setTextSize(14);timer.setTextColor(0xff8fe2b0);timer.setSingleLine(true);
        info.addView(title);info.addView(timer);
        if(compact){row.setOrientation(LinearLayout.VERTICAL);row.setGravity(Gravity.CENTER);row.setPadding(dp(4),dp(4),dp(4),dp(4));title.setTextSize(12);timer.setTextSize(12);row.addView(info,new LinearLayout.LayoutParams(-1,dp(36)));}
        else row.addView(info,new LinearLayout.LayoutParams(0,-1,1));
        LinearLayout actions=new LinearLayout(service);actions.setGravity(Gravity.CENTER);row.addView(actions,new LinearLayout.LayoutParams(compact?-1:-2,dp(48)));
        mute=button("Mute","Mute call microphone",0xff303844,v->{CallAudioState state=service.getCallAudioState();service.setMuted(state==null || !state.isMuted());});
        actions.addView(mute,new LinearLayout.LayoutParams(dp(compact?56:64),dp(48)));
        Button end=button("End","End phone call",0xffd83b49,v->{Call call=current;if(call!=null){if(call.getState()==Call.STATE_RINGING)call.reject(false,null);else call.disconnect();}});
        LinearLayout.LayoutParams endParams=new LinearLayout.LayoutParams(dp(compact?56:64),dp(48));endParams.leftMargin=dp(compact?4:8);actions.addView(end,endParams);
        int width=Math.min(dp(440),available-(compact?0:dp(24)));
        WindowManager.LayoutParams params=new WindowManager.LayoutParams(width,dp(compact?96:64),WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE|WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,PixelFormat.TRANSLUCENT);
        params.gravity=Gravity.TOP|Gravity.CENTER_HORIZONTAL;params.x=52;params.y=dp(12);params.setTitle("Phone call controls");params.setFitInsetsTypes(0);
        try{windows.addView(row,params);panel=row;}catch(RuntimeException e){android.util.Log.w("PiCallBar","Call overlay unavailable",e);}
    }
    private void hide(){if(panel!=null){try{windows.removeView(panel);}catch(IllegalArgumentException ignored){}panel=null;}}
    private void refresh(){
        handler.removeCallbacks(tick);List<Call> calls=new ArrayList<>(service.getCalls());
        for(Call call:new HashSet<>(watched))if(!calls.contains(call)){call.unregisterCallback(callback);watched.remove(call);}
        for(Call call:calls)if(watched.add(call))call.registerCallback(callback,handler);
        Call selected=null;for(Call call:calls)if(call.getState()!=Call.STATE_DISCONNECTED && call.getState()!=Call.STATE_DISCONNECTING){if(selected==null || call.getState()==Call.STATE_ACTIVE)selected=call;}
        if(selected!=current){current=selected;connectedAt=0;}
        if(current==null){hide();return;}
        KeyguardManager keyguard=(KeyguardManager)service.getSystemService(Context.KEYGUARD_SERVICE);
        if(phoneVisible || keyguard.isKeyguardLocked())hide();else show();
        if(panel!=null){
            Call.Details details=current.getDetails();String number=details==null || details.getHandle()==null?null:details.getHandle().getSchemeSpecificPart();String account=details==null || details.getAccountHandle()==null?null:details.getAccountHandle().getId();
            title.setText(PiCallerInfo.label(number,account,details==null?TelecomManager.PRESENTATION_UNKNOWN:details.getHandlePresentation()));
            int state=current.getState();long started=details==null?0:details.getConnectTimeMillis();
            if(started>0 && connectedAt==0)connectedAt=SystemClock.elapsedRealtime()-Math.max(0,System.currentTimeMillis()-started);
            timer.setText(state==Call.STATE_RINGING?"Incoming call":state==Call.STATE_HOLDING?"On hold":connectedAt>0?DateUtils.formatElapsedTime(Math.max(0,(SystemClock.elapsedRealtime()-connectedAt)/1000)):"Calling…");
            CallAudioState audio=service.getCallAudioState();boolean muted=audio!=null && audio.isMuted();mute.setText(muted?"Unmute":"Mute");mute.setContentDescription(muted?"Unmute call microphone":"Mute call microphone");
        }
        handler.postDelayed(tick,1000);
    }
    private void destroy(){handler.removeCallbacksAndMessages(null);hide();for(Call call:watched)call.unregisterCallback(callback);watched.clear();}
}
