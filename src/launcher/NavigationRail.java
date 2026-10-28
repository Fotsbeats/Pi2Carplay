package org.bench.boxhome;

import android.content.Context;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.view.*;
import android.widget.LinearLayout;

final class NavigationRail extends LinearLayout {
    static final int RECENTS=3;
    static final int SPLIT=4;
    long lastHomeTap=-1;
    interface Action { void run(int button); }
    NavigationRail(Context context,Action action) {
        super(context);setOrientation(VERTICAL);setGravity(Gravity.CENTER);setBackgroundColor(0xff0b1015);
        float density=getResources().getDisplayMetrics().density;
        String[] labels={"Back","Home","All apps","Split screen"};
        for(int i=0;i<labels.length;i++) {
            final int index=i;
            View button=new View(context) {
                final Paint p=new Paint(3);
                @Override protected void onDraw(Canvas c) {
                    super.onDraw(c);int save=c.save();float size=Math.min(getWidth(),getHeight())*.4f;
                    c.translate((getWidth()-size)/2,(getHeight()-size)/2);c.scale(size/24,size/24);
                    p.setColor(isPressed()?0xfff6a447:0xffc5ced6);p.setStrokeWidth(1.7f);p.setStyle(Paint.Style.STROKE);p.setStrokeCap(Paint.Cap.ROUND);p.setStrokeJoin(Paint.Join.ROUND);
                    if(index==0){Path path=new Path();path.moveTo(15,5);path.lineTo(8,12);path.lineTo(15,19);c.drawPath(path,p);}
                    else if(index==1)c.drawCircle(12,12,7,p);
                    else if(index==2){p.setStyle(Paint.Style.FILL);for(int row=0;row<3;row++)for(int col=0;col<3;col++)c.drawRoundRect(new RectF(3+col*7,3+row*7,7+col*7,7+row*7),1,1,p);}
                    else {c.drawRoundRect(new RectF(2,4,22,20),2,2,p);c.drawLine(12,4,12,20,p);}
                    c.restoreToCount(save);
                }
                @Override public void setPressed(boolean pressed){super.setPressed(pressed);invalidate();}
            };
            button.setContentDescription(labels[i]);button.setFocusable(true);button.setClickable(true);
            button.setOnClickListener(v->{
                if(index==1){
                    long now=android.os.SystemClock.uptimeMillis();
                    boolean twice=LayoutRules.isDoubleTap(lastHomeTap,now,ViewConfiguration.getDoubleTapTimeout());
                    lastHomeTap=twice?-1:now;
                    action.run(twice?RECENTS:1);
                }else{lastHomeTap=-1;action.run(index==3?SPLIT:index);}
            });
            if(index==1)button.setOnLongClickListener(v->{lastHomeTap=-1;action.run(RECENTS);return true;});
            button.setOnFocusChangeListener((v,focus)->{GradientDrawable bg=new GradientDrawable();bg.setColor(0x33f6a447);bg.setCornerRadius(12);v.setBackground(focus?bg:null);});
            LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,Math.round(56*density));lp.topMargin=Math.round(8*density);lp.bottomMargin=Math.round(8*density);addView(button,lp);
        }
    }
}
