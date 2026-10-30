package com.android.car.dialer.ui;

import android.app.*;
import android.content.res.ColorStateList;
import android.graphics.*;
import android.graphics.drawable.*;
import android.os.Bundle;
import android.view.*;
import android.widget.*;
import java.util.*;

/** Visual treatment for the upstream views; their IDs, listeners and call ownership stay intact. */
public final class PiPhoneUi implements Application.ActivityLifecycleCallbacks {
    private static final int SURFACE=0xff10131a,CONTROL=0xff252c37,GREEN=0xff28b66d,RED=0xffd83b49;
    private final WeakHashMap<View,Boolean> styled=new WeakHashMap<>(),selected=new WeakHashMap<>();
    private final WeakHashMap<Activity,ViewTreeObserver.OnGlobalLayoutListener> observers=new WeakHashMap<>();
    public static void init(Application app){app.registerActivityLifecycleCallbacks(new PiPhoneUi());}
    private static int dp(View view,float value){return Math.round(view.getResources().getDisplayMetrics().density*value);}
    private static GradientDrawable rounded(View view,int color,int radius){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(view,radius));return d;}
    private static Drawable ripple(View view,int color,int radius){return new RippleDrawable(ColorStateList.valueOf(0x28ffffff),new InsetDrawable(rounded(view,color,radius),dp(view,4)),null);}
    private static String name(View view){try{return view.getId()==View.NO_ID?"":view.getResources().getResourceEntryName(view.getId());}catch(RuntimeException e){return "";}}
    private void style(View view,boolean inCall){
        String id=name(view);
        if(id.equals("car_ui_toolbar_tab_item_text") && view instanceof TextView){
            View tab=(View)view.getParent();boolean active=Color.alpha(((TextView)view).getCurrentTextColor())>220;
            if(!Objects.equals(selected.get(tab),active)){selected.put(tab,active);tab.setBackground(new InsetDrawable(rounded(tab,active?0xff183251:0x00000000,18),dp(tab,6)));}
        }
        if(!styled.containsKey(view)){
            styled.put(view,true);
            if(id.equals("fragment_container") || id.equals("android:content"))view.setBackgroundColor(SURFACE);
            if(id.equals("car_ui_toolbar_background"))view.setBackgroundColor(0xff090c12);
            if(id.equals("background_image") && inCall)view.setVisibility(View.GONE);
            if(view instanceof TextView){
                TextView text=(TextView)view;
                if(id.equals("car_ui_toolbar_tab_item_text")){text.setTextSize(18);text.setTypeface(Typeface.create("sans-serif-medium",0));}
                else if(inCall && id.equals("user_profile_title")){text.setTextSize(36);text.setTypeface(Typeface.create("sans-serif-medium",0));}
                else if(inCall && id.equals("user_profile_call_state")){text.setTextSize(18);text.setTextColor(0xff8fe2b0);}
                else if(inCall && (id.equals("user_profile_phone_label") || id.equals("user_profile_phone_number")))text.setTextSize(18);
            }
            if(id.equals("one") || id.equals("two") || id.equals("three") || id.equals("four") || id.equals("five") || id.equals("six") || id.equals("seven") || id.equals("eight") || id.equals("nine") || id.equals("zero") || id.equals("star") || id.equals("pound"))view.setBackground(ripple(view,CONTROL,28));
            if(id.equals("call_button")){view.setBackground(ripple(view,GREEN,48));if(view instanceof ImageView)((ImageView)view).setImageDrawable(new Handset(dp(view,30),false));}
            if(inCall && id.equals("user_profile_avatar")){ViewGroup.LayoutParams lp=view.getLayoutParams();lp.width=lp.height=dp(view,144);view.setLayoutParams(lp);}
            boolean button=inCall && (id.equals("mute_button") || id.equals("toggle_dialpad_button") || id.equals("voice_channel_button") || id.equals("merge_button") || id.equals("pause_button") || id.equals("end_call_button") || id.equals("answer_call_button"));
            if(button){
                ViewGroup.LayoutParams lp=view.getLayoutParams();lp.width=lp.height=dp(view,64);view.setLayoutParams(lp);
                boolean voice=id.equals("voice_channel_button");
                view.setBackground(voice?null:ripple(view,id.equals("end_call_button")?RED:id.equals("answer_call_button")?GREEN:CONTROL,40));
                if(!voice)view.setTranslationY(-dp(view,6));
                if(view instanceof ImageView){
                    ImageView image=(ImageView)view;
                    image.setImageTintList(new ColorStateList(new int[][]{{android.R.attr.state_activated},{android.R.attr.state_selected},{}},new int[]{0xff8fe2b0,0xff8fe2b0,0xfff5f7fa}));
                    if(id.equals("end_call_button") || id.equals("answer_call_button"))image.setImageDrawable(new Handset(dp(view,30),id.equals("end_call_button")));
                }
            }
            if(inCall && id.equals("voice_channel_view")){view.setBackground(ripple(view,CONTROL,40));view.setTranslationY(-dp(view,6));}
            if(inCall && id.equals("mute_button")){
                ViewGroup bar=(ViewGroup)view.getParent();Labels labels=new Labels(bar);bar.addView(labels,new ViewGroup.LayoutParams(-1,-1));
            }
        }
        if(view instanceof ViewGroup){ViewGroup group=(ViewGroup)view;for(int i=0;i<group.getChildCount();i++)style(group.getChildAt(i),inCall);}
        if(view instanceof Labels)view.invalidate();
    }
    private void apply(Activity activity){View decor=activity.getWindow().getDecorView();if(!styled.containsKey(decor))decor.setBackgroundColor(SURFACE);style(decor,activity.getClass().getName().endsWith("InCallActivity"));}
    @Override public void onActivityResumed(Activity activity){
        PiCallBar.phoneVisible(true);apply(activity);
        if(!observers.containsKey(activity)){ViewTreeObserver.OnGlobalLayoutListener listener=()->apply(activity);observers.put(activity,listener);activity.getWindow().getDecorView().getViewTreeObserver().addOnGlobalLayoutListener(listener);}
    }
    @Override public void onActivityPaused(Activity activity){PiCallBar.phoneVisible(false);}
    @Override public void onActivityDestroyed(Activity activity){ViewTreeObserver.OnGlobalLayoutListener listener=observers.remove(activity);if(listener!=null && activity.getWindow().getDecorView().getViewTreeObserver().isAlive())activity.getWindow().getDecorView().getViewTreeObserver().removeOnGlobalLayoutListener(listener);}
    @Override public void onActivityCreated(Activity activity,Bundle state){}
    @Override public void onActivityStarted(Activity activity){}
    @Override public void onActivityStopped(Activity activity){}
    @Override public void onActivitySaveInstanceState(Activity activity,Bundle state){}
    private static final class Labels extends View {
        final ViewGroup bar;final Paint paint=new Paint(3);
        final String[] ids={"mute_button","toggle_dialpad_button","voice_channel_view","pause_button","merge_button","end_call_button"};
        final String[] captions={"Mute","Keypad","Audio","Hold","Merge","End"};
        Labels(ViewGroup bar){super(bar.getContext());this.bar=bar;setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);paint.setColor(0xffb7c0ce);paint.setTextSize(12*getResources().getDisplayMetrics().scaledDensity);paint.setTextAlign(Paint.Align.CENTER);paint.setTypeface(Typeface.create("sans-serif-medium",0));}
        protected void onDraw(Canvas canvas){
            int[] origin=new int[2];getLocationInWindow(origin);
            for(int i=0;i<ids.length;i++){int id=getResources().getIdentifier(ids[i],"id",getContext().getPackageName());View button=bar.findViewById(id);if(button==null || !button.isShown())continue;int[] at=new int[2];button.getLocationInWindow(at);canvas.drawText(captions[i],at[0]-origin[0]+button.getWidth()/2f,getHeight()-dp(this,4),paint);}
        }
    }
    private static final class Handset extends Drawable {
        final Paint paint=new Paint(3);final int size;final boolean end;
        Handset(int size,boolean end){this.size=size;this.end=end;paint.setColor(Color.WHITE);}
        public int getIntrinsicWidth(){return size;}public int getIntrinsicHeight(){return size;}
        public void draw(Canvas canvas){int saved=canvas.save();Rect b=getBounds();canvas.translate(b.left,b.top);canvas.scale(b.width()/24f,b.height()/24f);if(end)canvas.rotate(135,12,12);Path p=new Path();p.moveTo(6.6f,10.8f);p.cubicTo(8,13.5f,10.4f,16,13.2f,17.4f);p.lineTo(15.4f,15.2f);p.lineTo(21,16.7f);p.lineTo(21,21);p.cubicTo(11,22,2,13,3,3);p.lineTo(7.3f,3);p.lineTo(8.8f,8.6f);p.close();canvas.drawPath(p,paint);canvas.restoreToCount(saved);}
        public void setAlpha(int alpha){paint.setAlpha(alpha);}public void setColorFilter(ColorFilter filter){paint.setColorFilter(filter);}public int getOpacity(){return PixelFormat.TRANSLUCENT;}
    }
}
