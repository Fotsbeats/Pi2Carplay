package org.bench.boxhome;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.content.Intent;
import android.content.IntentFilter;
import android.provider.Settings;
import android.graphics.PixelFormat;
import android.util.DisplayMetrics;
import android.view.*;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.os.Handler;
import android.os.Looper;
import android.graphics.Rect;
import android.widget.Toast;

public final class NavigationService extends AccessibilityService {
    static NavigationService connected;
    WindowManager windows;
    View rail;
    PackagePolicyReceiver packagePolicy;
    final Handler splitHandler=new Handler(Looper.getMainLooper());
    int splitPhase;
    long splitReadyAt;
    final Runnable splitPoll=new Runnable(){public void run(){advanceSplit();if(splitPhase!=0)splitHandler.postDelayed(this,150);}};
    final Runnable splitTimeout=()->finishSplit("Split screen unavailable; open a supported app and try again");
    @Override protected void onServiceConnected() {
        super.onServiceConnected();
        connected=this;
        AccessibilityServiceInfo info=getServiceInfo();
        info.eventTypes=0;info.flags=0;setServiceInfo(info);
        if(packagePolicy==null){
            packagePolicy=new PackagePolicyReceiver();IntentFilter filter=new IntentFilter();
            filter.addAction(Intent.ACTION_PACKAGE_ADDED);filter.addAction(Intent.ACTION_PACKAGE_REPLACED);filter.addDataScheme("package");
            registerReceiver(packagePolicy,filter);
        }
        windows=(WindowManager)getSystemService(WINDOW_SERVICE);
        if(rail!=null)return;
        showRail();
    }
    void navigate(int button) {
        if(button!=NavigationRail.SPLIT&&splitPhase!=0)finishSplit(null);
        if(button==0)performGlobalAction(GLOBAL_ACTION_BACK);
        else if(button==1)performGlobalAction(GLOBAL_ACTION_HOME);
        else if(button==NavigationRail.RECENTS)recentApps(this);
        else if(button==NavigationRail.SPLIT)splitScreen(this);
        else {
            Intent intent=new Intent(this,Home.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP).putExtra("open_apps",true);
            try{startActivity(intent);}catch(RuntimeException e){Toast.makeText(this,"Unable to open apps",Toast.LENGTH_SHORT).show();}
        }
    }
    static void recentApps(android.content.Context context){
        if(connected==null||!connected.performGlobalAction(GLOBAL_ACTION_RECENTS))
            Toast.makeText(context,"Recent apps is unavailable; check navigation accessibility in Android settings",Toast.LENGTH_LONG).show();
    }
    static void splitScreen(android.content.Context context){
        if(connected!=null)connected.beginSplit();
        else Toast.makeText(context,"Navigation accessibility is unavailable",Toast.LENGTH_SHORT).show();
    }
    void beginSplit(){
        if(splitPhase!=0)return;
        splitPhase=1;splitReadyAt=android.os.SystemClock.uptimeMillis()+300;
        AccessibilityServiceInfo info=getServiceInfo();
        info.eventTypes=AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED|AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED;
        info.packageNames=new String[]{"com.android.launcher3"};info.flags=AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS|AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS;setServiceInfo(info);
        splitHandler.postDelayed(splitTimeout,3000);
        if(!performGlobalAction(GLOBAL_ACTION_RECENTS)){finishSplit("Recent apps is unavailable");return;}
        splitHandler.postDelayed(splitPoll,300);
    }
    void finishSplit(String message){
        splitPhase=0;splitHandler.removeCallbacksAndMessages(null);
        AccessibilityServiceInfo info=getServiceInfo();
        if(info!=null){info.eventTypes=0;info.flags=0;info.packageNames=null;setServiceInfo(info);}
        if(message!=null)Toast.makeText(this,message,Toast.LENGTH_SHORT).show();
    }
    boolean clickNode(AccessibilityNodeInfo node){
        for(int i=0;node!=null&&i<4;i++,node=node.getParent())
            if(node.isEnabled()&&node.isClickable()){
                if(node.performAction(AccessibilityNodeInfo.ACTION_CLICK))return true;
                Rect bounds=new Rect();node.getBoundsInScreen(bounds);
                if(bounds.isEmpty())return false;
                android.graphics.Path path=new android.graphics.Path();path.moveTo(bounds.exactCenterX(),bounds.exactCenterY());
                return dispatchGesture(new android.accessibilityservice.GestureDescription.Builder().addStroke(
                    new android.accessibilityservice.GestureDescription.StrokeDescription(path,0,60)).build(),null,null);
            }
        return false;
    }
    void advanceSplit(){
        if(splitPhase==0||android.os.SystemClock.uptimeMillis()<splitReadyAt)return;
        AccessibilityNodeInfo root=null;
        for(android.view.accessibility.AccessibilityWindowInfo window:getWindows()){
            AccessibilityNodeInfo candidate=window.getRoot();
            if(candidate!=null&&android.text.TextUtils.equals("com.android.launcher3",candidate.getPackageName())){root=candidate;break;}
        }
        if(root==null||!android.text.TextUtils.equals("com.android.launcher3",root.getPackageName()))return;
        // shortcut: stock English Quickstep labels; resolve localized resources before adding other UI languages.
        if(splitPhase==2){
            for(AccessibilityNodeInfo node:root.findAccessibilityNodeInfosByText("Split screen")){
                if("com.android.launcher3:id/text".equals(node.getViewIdResourceName())&&android.text.TextUtils.equals("Split screen",node.getText())&&clickNode(node)){finishSplit(null);return;}
            }
            return;
        }
        AccessibilityNodeInfo card=null;int largest=0;Rect viewport=new Rect();root.getBoundsInScreen(viewport);
        for(AccessibilityNodeInfo node:root.findAccessibilityNodeInfosByViewId("com.android.launcher3:id/task_view_single")){
            Rect bounds=new Rect();node.getBoundsInScreen(bounds);
            if(node.isVisibleToUser()&&bounds.intersect(viewport)&&bounds.width()*bounds.height()>largest){card=node;largest=bounds.width()*bounds.height();}
        }
        if(card==null)return;
        for(AccessibilityNodeInfo icon:card.findAccessibilityNodeInfosByViewId("com.android.launcher3:id/icon")){
            splitPhase=2;
            if(!clickNode(icon))splitPhase=1;
            return;
        }
    }
    void showRail() {
        DisplayMetrics metrics=new DisplayMetrics();windows.getDefaultDisplay().getRealMetrics(metrics);
        rail=new NavigationRail(this,this::navigate);
        WindowManager.LayoutParams lp=new WindowManager.LayoutParams(104,WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE|WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL|WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN|WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.OPAQUE);
        lp.setFitInsetsTypes(0);lp.gravity=Gravity.LEFT|Gravity.TOP;lp.setTitle("Box Home navigation");
        try{windows.addView(rail,lp);ready(true);windows.updateViewLayout(rail,lp);}catch(RuntimeException e){rail=null;ready(false);disableSelf();}
    }
    void ready(boolean value) {
        try { Class.forName("android.os.SystemProperties").getMethod("set",String.class,String.class).invoke(null,"sys.boxhome.rail.ready",value?"1":"0"); }
        catch(ReflectiveOperationException e){android.util.Log.e("BoxHome","Cannot announce navigation readiness",e);}
    }
    @Override public void onAccessibilityEvent(AccessibilityEvent event) {
        if(splitPhase!=0&&android.text.TextUtils.equals("com.android.launcher3",event.getPackageName()))advanceSplit();
    }
    @Override public void onInterrupt() { }
    @Override public void onDestroy() {
        finishSplit(null);
        if(connected==this)connected=null;
        ready(false);
        if(packagePolicy!=null){unregisterReceiver(packagePolicy);packagePolicy=null;}
        if(rail!=null){try{windows.removeView(rail);}catch(IllegalArgumentException ignored){}rail=null;}
        super.onDestroy();
    }
}
