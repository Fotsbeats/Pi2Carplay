package org.bench.boxhome;

import android.animation.ValueAnimator;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.*;
import android.content.pm.*;
import android.graphics.*;
import android.graphics.drawable.*;
import android.net.*;
import android.net.wifi.*;
import android.os.*;
import android.provider.Settings;
import android.view.*;
import android.view.animation.*;
import android.widget.*;
import java.io.*;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.*;

public final class Home extends Activity {
    static final int AMBER = Color.rgb(246,164,71), TEXT = Color.rgb(245,243,236);
    static final String[] FAVORITES = {"com.plexapp.android","com.google.android.youtube","com.brave.browser","deezer.android.app","com.waze","com.spotify.music"};
    static final String[] LABELS = {"Plex","YouTube","Brave","Deezer","Waze","Spotify"};
    static final String[] ASSETS = {"plex.png","youtube.png","brave.png","deezer.jpg","waze.png","spotify.webp"};
    final Handler handler = new Handler(Looper.getMainLooper());
    final HashMap<String, Drawable> icons = new HashMap<>();
    final ArrayList<View> dots = new ArrayList<>();
    final ExecutorService appLoader = Executors.newSingleThreadExecutor();
    ArrayList<AppEntry> cachedExtras = new ArrayList<>();
    boolean appsLoading;
    boolean catalogReady;
    int catalogRevision;
    String[] homeApps;
    AlertDialog activeDialog;
    final class HomeDialog extends AlertDialog.Builder {
        HomeDialog(){super(Home.this);}
        @Override public AlertDialog show(){
            if(activeDialog!=null)activeDialog.dismiss();
            AlertDialog dialog=super.show();activeDialog=dialog;
            HomeSettings.style(Home.this,dialog);
            dialog.setOnDismissListener(d->{if(activeDialog==d)activeDialog=null;});
            return dialog;
        }
    }
    Scene scene;
    Pager pager;
    LinearLayout strip;
    TextView clock, date, network;
    NavigationRail localRail;
    boolean active;
    String appSignature = "";
    final Runnable tick = new Runnable() { public void run() { updateStatus(); if(active) handler.postDelayed(this,30000); } };
    final BroadcastReceiver changed = new BroadcastReceiver() { public void onReceive(Context c, Intent i) { catalogRevision++;appSignature="";if(active)refreshApps(); } };

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        homeApps=HomeApps.load(getPreferences(MODE_PRIVATE).getString("home_slots",null),FAVORITES);
        getWindow().setNavigationBarColor(Color.rgb(11,16,21));
        immersiveHome();
        scene = new Scene();
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.HORIZONTAL);
        android.util.DisplayMetrics metrics=new android.util.DisplayMetrics();getWindowManager().getDefaultDisplay().getRealMetrics(metrics);
        localRail=new NavigationRail(this,button->{if(button==NavigationRail.RECENTS)NavigationService.recentApps(this);else if(button==NavigationRail.SPLIT)NavigationService.splitScreen(this);else if(pager!=null)pager.go(button==2?1:0);});
        root.addView(localRail,new LinearLayout.LayoutParams(104,-1));
        root.addView(scene,new LinearLayout.LayoutParams(0,-1,1));setContentView(root);
        root.getViewTreeObserver().addOnGlobalLayoutListener(()->{
            int[] position=new int[2];root.getLocationOnScreen(position);
            android.util.DisplayMetrics current=new android.util.DisplayMetrics();getWindowManager().getDefaultDisplay().getRealMetrics(current);
            int width=LayoutRules.localRailWidth(current.widthPixels,position[0]);
            android.view.ViewGroup.LayoutParams lp=localRail.getLayoutParams();
            if(lp.width!=width){lp.width=width;localRail.setLayoutParams(lp);}
        });
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_PACKAGE_ADDED); filter.addAction(Intent.ACTION_PACKAGE_REMOVED); filter.addAction(Intent.ACTION_PACKAGE_CHANGED); filter.addDataScheme("package");
        registerReceiver(changed,filter);
    }
    void immersiveHome() {
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
    }
    @Override public void onWindowFocusChanged(boolean focus) { super.onWindowFocusChanged(focus);if(focus)immersiveHome(); }
    @Override protected void onResume() {
        super.onResume(); active=true;
        immersiveHome();
        scene.post(() -> { if(appSignature.isEmpty())refreshApps(); updateStatus(); if(pager!=null && getIntent().getBooleanExtra("open_apps",false))pager.go(1); });
        handler.removeCallbacks(tick); handler.post(tick);
    }
    @Override protected void onPause() { active=false; handler.removeCallbacks(tick); super.onPause(); }
    @Override protected void onDestroy() { if(activeDialog!=null)activeDialog.dismiss();appLoader.shutdownNow();handler.removeCallbacksAndMessages(null); unregisterReceiver(changed); super.onDestroy(); }
    @Override protected void onNewIntent(Intent intent) { super.onNewIntent(intent);if(activeDialog!=null)activeDialog.dismiss();setIntent(intent); if(pager!=null)pager.go(intent.getBooleanExtra("open_apps",false)?1:0); }
    @Override public void onBackPressed() { if(pager!=null)pager.go(0); }

    void updateStatus() {
        if(clock==null)return;
        Date now = new Date();
        String time = android.text.format.DateFormat.getTimeFormat(this).format(now);
        clock.setText(time);
        date.setText(new SimpleDateFormat("EEEE, MMMM d", Locale.getDefault()).format(now));
        String name="Offline";
        ConnectivityManager cm=(ConnectivityManager)getSystemService(CONNECTIVITY_SERVICE);
        NetworkCapabilities caps=cm.getNetworkCapabilities(cm.getActiveNetwork());
        if(caps!=null) {
            if(caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                WifiManager wm=(WifiManager)getApplicationContext().getSystemService(WIFI_SERVICE);
                String ssid=wm.getConnectionInfo().getSSID();
                name=ssid!=null && !ssid.equals("<unknown ssid>") ? ssid.replace("\"", "") : "Wi-Fi";
            } else name="Connected";
        }
        network.setText(name);
        network.setContentDescription("Network: "+name);
    }
    void launch(String pkg) {
        Intent intent=pkg.equals("com.suding.settings") ? new Intent(Settings.ACTION_SETTINGS)
                : getPackageManager().getLaunchIntentForPackage(pkg);
        if(intent==null && Arrays.asList(homeApps).contains(pkg) && !pkg.equals("com.android.vending")) {
            Intent store=getPackageManager().getLaunchIntentForPackage("com.android.vending");
            if(store!=null){
                new HomeDialog().setTitle(homeLabel(pkg)+" is not installed")
                    .setMessage("Install it from Play Store, or choose another app for this Home position.")
                    .setPositiveButton("Play Store",(dialog,which)->{
                        try{startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse("market://details?id="+pkg)).setPackage("com.android.vending"));}
                        catch(ActivityNotFoundException|SecurityException e){Toast.makeText(this,"Play Store is unavailable",Toast.LENGTH_SHORT).show();}
                    }).setNeutralButton("Choose app",(dialog,which)->chooseHomeApp(Arrays.asList(homeApps).indexOf(pkg))).setNegativeButton("Cancel",null).show();
                return;
            }
        }
        if(intent==null) { Toast.makeText(this,"Install this app first",Toast.LENGTH_SHORT).show(); return; }
        try { startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); }
        catch(ActivityNotFoundException|SecurityException e) { Toast.makeText(this,"Unable to open this app",Toast.LENGTH_SHORT).show(); }
    }
    String homeLabel(String pkg){
        int i=Arrays.asList(FAVORITES).indexOf(pkg);if(i>=0)return LABELS[i];
        for(AppEntry app:cachedExtras)if(app.pkg.equals(pkg))return app.label;
        return pkg;
    }
    boolean isCatalogApp(String pkg){for(AppEntry app:cachedExtras)if(app.pkg.equals(pkg))return true;return false;}
    Drawable homeIcon(String pkg){
        int i=Arrays.asList(FAVORITES).indexOf(pkg);if(i>=0)return favoriteIcon(i);
        for(AppEntry app:cachedExtras)if(app.pkg.equals(pkg)){
            Drawable.ConstantState state=app.icon.getConstantState();return state==null?app.icon:state.newDrawable(getResources()).mutate();
        }
        return getPackageManager().getDefaultActivityIcon();
    }
    void saveHomeApps(){
        getPreferences(MODE_PRIVATE).edit().putString("home_slots",HomeApps.save(homeApps)).apply();
        appSignature="";renderApps();
    }
    void showHomeSettings(){
        HomeSettings.show(this);
    }
    void showHomeHelp(){
        new HomeDialog().setTitle("Using Home")
                    .setMessage("Use Back, Home and Apps on the left rail. Double-tap or hold the Home circle for Android recent apps. Swipe a recent-app card up to close it. Use the split icon below Apps while an app is open, then choose another app from Recents. Drag the divider to resize; drag it to an edge to return to one app. Some apps do not support splitting. Swipe between Home and Apps, or tap the page indicators.\n\nHold an app to add, replace or remove it from Home. Choosing an app already on Home swaps its position. Removing a shortcut keeps the app installed.\n\nNew downloads appear in Apps and open in landscape. Picture-in-picture is allowed for YouTube.\n\nUse Wi-Fi to connect to the internet. Play Store may require your Google sign-in.")
                    .setPositiveButton("Got it",null).show();
    }
    void editHomeApps(){
        String[] rows=new String[HomeApps.SLOTS];
        for(int i=0;i<rows.length;i++)rows[i]="Position "+(i+1)+"   "+(homeApps[i].isEmpty()?"Empty":homeLabel(homeApps[i]));
        new HomeDialog().setTitle("Edit home apps")
            .setItems(rows,(dialog,slot)->chooseHomeApp(slot))
            .setNeutralButton("Reset defaults",(dialog,which)->{
                new HomeDialog().setTitle("Reset home apps?").setMessage("Restore Plex, YouTube, Brave, Deezer, Waze and Spotify. Installed apps and their data are kept.")
                    .setPositiveButton("Reset",(d,w)->{homeApps=FAVORITES.clone();saveHomeApps();}).setNegativeButton("Cancel",null).show();
            }).setNegativeButton("Done",null).show();
    }
    void chooseHomeApp(int slot){
        ArrayList<String> packages=new ArrayList<>(),labels=new ArrayList<>();
        packages.add("");labels.add("Empty - remove from Home");
        for(AppEntry app:cachedExtras){packages.add(app.pkg);int current=Arrays.asList(homeApps).indexOf(app.pkg);labels.add(app.label+(current>=0?"  (position "+(current+1)+")":""));}
        if(appsLoading){Toast.makeText(this,"Loading apps; try again shortly",Toast.LENGTH_SHORT).show();return;}
        new HomeDialog().setTitle("Home position "+(slot+1))
            .setItems(labels.toArray(new String[0]),(dialog,which)->{
                HomeApps.set(homeApps,slot,packages.get(which));saveHomeApps();dialog.dismiss();
            }).setNegativeButton("Cancel",null).show();
    }
    void showAppOptions(String pkg,String label){
        int slot=Arrays.asList(homeApps).indexOf(pkg);
        ArrayList<String> actions=new ArrayList<>();actions.add(slot>=0?"Replace or move home app":"Add to Home");
        if(slot>=0)actions.add("Remove from Home");actions.add("App info");
        new HomeDialog().setTitle(label).setItems(actions.toArray(new String[0]),(dialog,which)->{
            String action=actions.get(which);
            if(action.equals("App info"))startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:"+pkg)));
            else if(action.equals("Remove from Home")){HomeApps.set(homeApps,slot,"");saveHomeApps();}
            else if(slot>=0)chooseHomeApp(slot);
            else if(pkg.equals("com.android.vending"))Toast.makeText(this,"Play Store stays on the Apps page",Toast.LENGTH_SHORT).show();
            else {
                String[] positions=new String[HomeApps.SLOTS];for(int i=0;i<positions.length;i++)positions[i]="Position "+(i+1)+"   "+(homeApps[i].isEmpty()?"Empty":homeLabel(homeApps[i]));
                new HomeDialog().setTitle("Add "+label+" to Home").setItems(positions,(d,target)->{HomeApps.set(homeApps,target,pkg);saveHomeApps();}).setNegativeButton("Cancel",null).show();
            }
        }).setNegativeButton("Cancel",null).show();
    }
    Drawable storeIcon(){
        if(!icons.containsKey("store")){
            try(InputStream in=getAssets().open("icons/store.png")){
                Bitmap source=BitmapFactory.decodeStream(in);if(source==null)throw new IOException("Invalid Store icon");
                Bitmap square=Bitmap.createBitmap(223,223,Bitmap.Config.ARGB_8888);
                Canvas canvas=new Canvas(square);Paint paint=new Paint(3);paint.setColor(Color.WHITE);
                Path mask=new Path();mask.addRoundRect(new RectF(0,0,223,223),18,18,Path.Direction.CW);
                canvas.clipPath(mask);canvas.drawColor(Color.WHITE);
                canvas.drawBitmap(source,new Rect(40,40,183,183),new Rect(24,24,199,199),paint);
                icons.put("store",new BitmapDrawable(getResources(),square));
            }catch(IOException e){icons.put("store",getPackageManager().getDefaultActivityIcon());}
        }
        return icons.get("store").getConstantState().newDrawable(getResources()).mutate();
    }
    Drawable favoriteIcon(int index) {
        String key="favorite-"+index;
        if(!icons.containsKey(key)) {
            try(InputStream in=getAssets().open("icons/"+ASSETS[index])) {
                Bitmap b=BitmapFactory.decodeStream(in);
                if(b==null)throw new IOException("Invalid icon");
                if(FAVORITES[index].equals("com.spotify.music")) {
                    // Spotify's adaptive icon declares a black background behind its official artwork.
                    Bitmap square=Bitmap.createBitmap(223,223,Bitmap.Config.ARGB_8888);
                    Canvas c=new Canvas(square);c.drawColor(Color.BLACK);
                    c.drawBitmap(b,null,new Rect(8,8,215,215),new Paint(3));b=square;
                }
                if(FAVORITES[index].equals("com.waze") || FAVORITES[index].equals("deezer.android.app") || FAVORITES[index].equals("com.brave.browser") || FAVORITES[index].equals("com.spotify.music")) {
                    Bitmap normalized=Bitmap.createBitmap(223,223,Bitmap.Config.ARGB_8888);
                    Canvas c=new Canvas(normalized);Paint p=new Paint(3);
                    Matrix transform=new Matrix();transform.setRectToRect(new RectF(0,0,b.getWidth(),b.getHeight()),new RectF(20,20,203,203),Matrix.ScaleToFit.CENTER);
                    BitmapShader shader=new BitmapShader(b,Shader.TileMode.CLAMP,Shader.TileMode.CLAMP);shader.setLocalMatrix(transform);p.setShader(shader);
                    c.drawRoundRect(new RectF(20,20,203,203),33,33,p);b=normalized;

                }
                icons.put(key,new BitmapDrawable(getResources(),b));
            } catch(IOException e) { icons.put(key,getPackageManager().getDefaultActivityIcon()); }
        }
        return icons.get(key).getConstantState().newDrawable(getResources()).mutate();
    }
    static final class AppEntry {
        final String pkg,activity,label;final Drawable icon;
        AppEntry(ResolveInfo r,String label,Drawable icon){pkg=r.activityInfo.packageName;activity=r.activityInfo.name;this.label=label;this.icon=icon;}
    }
    ArrayList<AppEntry> extraApps() {
        if(Looper.myLooper()==Looper.getMainLooper())throw new IllegalStateException("App resources must load off the UI thread");
        Intent query=new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        TreeMap<String,ResolveInfo> unique=new TreeMap<>();
        HashSet<String> excluded=new HashSet<>();
        excluded.add(getPackageName()); excluded.add("com.android.launcher3"); excluded.add("com.android.vending");
        for(ResolveInfo r:getPackageManager().queryIntentActivities(query,0)) {
            String pkg=r.activityInfo.packageName;
            if(!excluded.contains(pkg))unique.put(pkg,r);
        }
        ArrayList<AppEntry> result=new ArrayList<>();
        for(ResolveInfo r:unique.values()) {
            try { result.add(new AppEntry(r,r.loadLabel(getPackageManager()).toString(),r.loadIcon(getPackageManager()))); }
            catch(RuntimeException e){android.util.Log.w("BoxHome","Unable to load app resources: "+r.activityInfo.packageName,e);}
        }
        Collections.sort(result,(a,b)->a.label.compareToIgnoreCase(b.label));
        return result;
    }
    void refreshApps() {
        if(isDestroyed() || scene.getWidth()==0)return;
        renderApps();
        if(appsLoading)return;
        appsLoading=true;
        final int revision=catalogRevision;
        appLoader.execute(()->{
            android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND);
            try {
                ArrayList<AppEntry> loaded=extraApps();
                handler.post(()->{appsLoading=false;if(isDestroyed())return;cachedExtras=loaded;catalogReady=true;appSignature="";
                    if(active){if(revision!=catalogRevision)refreshApps();else renderApps();}});
            } catch(RuntimeException e) {
                android.util.Log.w("BoxHome","Unable to refresh app catalog",e);
                handler.post(()->appsLoading=false);
            }
        });
    }
    void renderApps() {
        StringBuilder sig=new StringBuilder().append(scene.getWidth()).append(':').append(scene.getHeight());
        sig.append(Arrays.toString(homeApps));
        for(AppEntry r:cachedExtras)sig.append('|').append(r.pkg).append(':').append(r.activity).append(':').append(r.label);
        if(!sig.toString().equals(appSignature)) { int page=pager==null?0:pager.page;appSignature=sig.toString();ArrayList<AppEntry> extras=new ArrayList<>();
            for(AppEntry r:cachedExtras)if(!Arrays.asList(homeApps).contains(r.pkg))extras.add(r);
            scene.build(extras);Pager built=pager;built.page=LayoutRules.clamp(page,built.count);
            built.post(()->{if(pager==built)built.go(built.page);}); }
    }
    FrameLayout appButton(String pkg,String label,Drawable icon,int size) {
        FrameLayout button=new FrameLayout(this);button.setClipChildren(false);button.setClipToPadding(false);
        button.setContentDescription(label);button.setFocusable(true);button.setClickable(true);
        ImageView image=new ImageView(this);image.setImageDrawable(icon);image.setScaleType(ImageView.ScaleType.FIT_CENTER);

        image.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        FrameLayout.LayoutParams lp=new FrameLayout.LayoutParams(size,size,Gravity.CENTER);button.addView(image,lp);
        button.setOnClickListener(v->launch(pkg));
        button.setOnLongClickListener(v->{showAppOptions(pkg,label);return true;});
        button.setOnTouchListener((v,e)->{float rest=catalogReady&&Arrays.asList(homeApps).contains(pkg)&&!isCatalogApp(pkg)?.55f:1f;if(e.getActionMasked()==MotionEvent.ACTION_DOWN)v.setAlpha(rest*.7f);if(e.getActionMasked()==MotionEvent.ACTION_UP||e.getActionMasked()==MotionEvent.ACTION_CANCEL)v.setAlpha(rest);return false;});
        button.setOnFocusChangeListener((v,focus)->v.setForeground(focus?outline(AMBER,16):null));
        return button;
    }
    GradientDrawable rounded(int color,float radius) { GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(radius);return d; }
    GradientDrawable outline(int color,float radius) { GradientDrawable d=rounded(Color.TRANSPARENT,radius);d.setStroke(3,color);return d; }
    TextView text(float pixels,int color) { TextView t=new TextView(this);t.setTextColor(color);t.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,pixels);t.setGravity(Gravity.CENTER_VERTICAL);t.setSingleLine(true);return t; }
    void place(FrameLayout parent,View view,int width,int height,int x,int y) {
        FrameLayout.LayoutParams lp=new FrameLayout.LayoutParams(width,height);lp.leftMargin=x;lp.topMargin=y;parent.addView(view,lp);
    }
    void setPage(int page) {
        for(int i=0;i<dots.size();i++) {View v=dots.get(i);v.setBackground(rounded(i==page?AMBER:0x66ffffff,20));}
    }
    final class Scene extends FrameLayout {
        final Paint paint=new Paint(3);
        Scene(){super(Home.this);setWillNotDraw(false);setClipChildren(true);}
        @Override protected void onSizeChanged(int w,int h,int oldw,int oldh){super.onSizeChanged(w,h,oldw,oldh);post(()->refreshApps());}
        final Rect backgroundSource=new Rect(104,0,1744,720), backgroundTarget=new Rect();
        @Override protected void onDraw(Canvas c) {
            super.onDraw(c);backgroundTarget.set(0,0,getWidth(),getHeight());
            c.drawBitmap(Backdrop.image(),backgroundSource,backgroundTarget,paint);
        }

        void build(ArrayList<AppEntry> extras) {
            dots.clear();removeAllViews();
            int w=getWidth(),h=getHeight();float u=Math.min(w/1640f,h/720f);
            int pad=Math.round(48*u), header=Math.round(88*u), top=Math.round(36*u);
            int settingsWidth=Math.round(72*u),networkWidth=Math.round(300*u),gap=Math.round(24*u);
            LinearLayout status=new LinearLayout(Home.this);status.setOrientation(LinearLayout.HORIZONTAL);status.setGravity(Gravity.CENTER_VERTICAL);
            clock=text(36*u,TEXT);clock.setTypeface(Typeface.create("sans-serif-light",Typeface.NORMAL));
            status.addView(clock,new LinearLayout.LayoutParams(-2,-1));
            date=text(23*u,0xffbdc8d2);date.setEllipsize(android.text.TextUtils.TruncateAt.END);
            LinearLayout.LayoutParams dateParams=new LinearLayout.LayoutParams(0,-1,1);dateParams.leftMargin=gap;status.addView(date,dateParams);
            place(this,status,w-2*pad-settingsWidth-networkWidth-2*gap,header,pad,top);
            FrameLayout settings=new FrameLayout(Home.this);settings.setContentDescription("Settings");settings.setFocusable(true);settings.setClickable(true);
            settings.setBackground(new RippleDrawable(android.content.res.ColorStateList.valueOf(0x44f6a447),rounded(Color.TRANSPARENT,16*u),null));
            settings.setOnClickListener(v->showHomeSettings());settings.addView(new Glyph(true),new FrameLayout.LayoutParams(-1,-1));
            settings.setOnFocusChangeListener((v,focus)->v.setForeground(focus?outline(AMBER,20*u):null));
            place(this,settings,settingsWidth,header,w-pad-settingsWidth,top);
            network=text(23*u,0xffbdc8d2);network.setGravity(Gravity.RIGHT|Gravity.CENTER_VERTICAL);network.setEllipsize(android.text.TextUtils.TruncateAt.END);
            network.setClickable(true);network.setFocusable(true);network.setOnClickListener(v->startActivity(new Intent(Settings.ACTION_WIFI_SETTINGS)));
            network.setOnFocusChangeListener((v,focus)->v.setForeground(focus?outline(AMBER,16*u):null));
            place(this,network,networkWidth,header,w-pad-settingsWidth-gap-networkWidth,top);
            View line=new View(Home.this);line.setBackgroundColor(0xff303941);place(this,line,w-2*pad,1,pad,top+header+Math.round(16*u));
            int contentTop=top+header+Math.round(48*u),bottom=Math.round(76*u);
            int pageW=w-2*pad,pageH=h-contentTop-bottom;
            pager=new Pager();place(this,pager,pageW,pageH,pad,contentTop);
            strip=new LinearLayout(Home.this);strip.setOrientation(LinearLayout.HORIZONTAL);pager.addView(strip,new HorizontalScrollView.LayoutParams(-2,-1));
            FrameLayout favorites=new FrameLayout(Home.this);strip.addView(favorites,new LinearLayout.LayoutParams(pageW,pageH));
            float[] xs={.105f,.265f,.425f,.585f,.745f,.905f},ys={.43f,.53f,.59f,.59f,.53f,.43f};
            float[] angles={-9,5,-4,4,-5,9};
            int[] sizes={306,286,314,294,310,306};
            for(int i=0;i<HomeApps.SLOTS;i++) {
                final int slot=i;String pkg=homeApps[i];
                int size=Math.round(sizes[i]*u),bw=Math.round(322*u),bh=Math.round(352*u);
                FrameLayout icon;
                if(pkg.isEmpty()){
                    icon=new FrameLayout(Home.this);icon.setContentDescription("Choose app for home position "+(i+1));
                    icon.setFocusable(true);icon.setClickable(true);icon.setOnClickListener(v->chooseHomeApp(slot));
                    icon.setOnFocusChangeListener((v,focus)->v.setForeground(focus?outline(AMBER,16*u):null));
                    TextView add=text(64*u,0xffa7b3bf);add.setText("+");add.setGravity(Gravity.CENTER);
                    icon.addView(add,new FrameLayout.LayoutParams(-1,-1));
                }else{
                    icon=appButton(pkg,homeLabel(pkg),homeIcon(pkg),size);icon.setRotation(angles[i]);
                    if(catalogReady&&!isCatalogApp(pkg)){icon.setContentDescription(homeLabel(pkg)+", not installed; tap for options");}
                    icon.getChildAt(0).setTranslationY(-18*u);
                    // Installed icons fill their drawable; bundled favorites include a20px inset.
                    if(!Arrays.asList(FAVORITES).contains(pkg)){
                        View artwork=icon.getChildAt(0);artwork.setScaleX(183f/223f);artwork.setScaleY(183f/223f);
                        artwork.setOutlineProvider(new ViewOutlineProvider(){
                            @Override public void getOutline(View view,Outline outline){
                                outline.setRoundRect(0,0,view.getWidth(),view.getHeight(),Math.min(view.getWidth(),view.getHeight())*33f/183f);
                            }
                        });
                        artwork.setClipToOutline(true);
                    }
                    TextView label=text(26*u,TEXT);label.setText(homeLabel(pkg));label.setGravity(Gravity.CENTER);label.setEllipsize(android.text.TextUtils.TruncateAt.END);label.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
                    FrameLayout.LayoutParams caption=new FrameLayout.LayoutParams(-1,Math.round(42*u),Gravity.BOTTOM);icon.addView(label,caption);
                }
                place(favorites,icon,bw,bh,Math.round(xs[i]*pageW-bw/2f),Math.round(ys[i]*pageH-bh/2f));
            }
            int count=LayoutRules.pages(extras.size()+1);
            for(int pg=1;pg<count;pg++) {
                FrameLayout page=new FrameLayout(Home.this);strip.addView(page,new LinearLayout.LayoutParams(pageW,pageH));
                for(int i=0;i<LayoutRules.PAGE_SIZE;i++) {
                    int index=(pg-1)*LayoutRules.PAGE_SIZE+i;if(index>extras.size())break;
                    String pkg,label;Drawable icon;
                    if(index==0) {
                        pkg="com.android.vending";label="Play Store";
                        icon=storeIcon();
                    } else {
                        AppEntry r=extras.get(index-1);pkg=r.pkg;label=r.label;icon=r.icon;
                    }
                    int cellW=pageW/LayoutRules.COLUMNS,cellH=pageH/2,size=Math.round(Math.min(cellW*.55f,cellH*.65f));
                    FrameLayout button=appButton(pkg,label,icon,size);button.getChildAt(0).setTranslationY(-16*u);
                    TextView caption=text(23*u,TEXT);caption.setText(label);caption.setGravity(Gravity.CENTER);caption.setEllipsize(android.text.TextUtils.TruncateAt.END);caption.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
                    FrameLayout.LayoutParams cp=new FrameLayout.LayoutParams(-1,Math.round(40*u),Gravity.BOTTOM);cp.bottomMargin=Math.round(8*u);button.addView(caption,cp);
                    place(page,button,cellW,cellH,(i%LayoutRules.COLUMNS)*cellW,(i/LayoutRules.COLUMNS)*cellH);
                }
            }
            pager.count=count;
            int hit=Math.round(112*u),total=hit*count;
            for(int i=0;i<count;i++) {
                final int target=i;FrameLayout hitbox=new FrameLayout(Home.this);hitbox.setContentDescription(i==0?"Favorites page":"Apps page "+(i+1));hitbox.setClickable(true);hitbox.setFocusable(true);hitbox.setOnClickListener(v->pager.go(target));hitbox.setOnFocusChangeListener((v,focus)->v.setForeground(focus?outline(AMBER,16*u):null));
                View dot=new View(Home.this);dot.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);FrameLayout.LayoutParams dl=new FrameLayout.LayoutParams(Math.round(28*u),Math.round(5*u),Gravity.CENTER);hitbox.addView(dot,dl);dots.add(dot);
                place(this,hitbox,hit,Math.round(72*u),(w-total)/2+i*hit,h-Math.round(76*u));
            }
            updateStatus();setPage(0);
        }
    }
    final class Pager extends HorizontalScrollView {
        int count=1,page=0,startPage,startX;
        Pager(){super(Home.this);setFillViewport(true);setHorizontalScrollBarEnabled(false);setOverScrollMode(OVER_SCROLL_NEVER);setClipChildren(true);setContentDescription("App pages; swipe left or right");}
        void go(int index){page=LayoutRules.clamp(index,count);if(ValueAnimator.areAnimatorsEnabled())smoothScrollTo(page*getWidth(),0);else scrollTo(page*getWidth(),0);setPage(page);}
        @Override public boolean onInterceptTouchEvent(MotionEvent e){if(e.getActionMasked()==MotionEvent.ACTION_DOWN){startX=getScrollX();startPage=page;}return super.onInterceptTouchEvent(e);}
        @Override public boolean onTouchEvent(MotionEvent e){boolean handled=super.onTouchEvent(e);if(e.getActionMasked()==MotionEvent.ACTION_DOWN){startX=getScrollX();startPage=page;}if(e.getActionMasked()==MotionEvent.ACTION_UP||e.getActionMasked()==MotionEvent.ACTION_CANCEL)go(LayoutRules.snap(startPage,getScrollX()-startX,getWidth(),count));return handled;}
        @Override public void fling(int velocity) { /* Snap explicitly on release so fling cannot stop between pages. */ }
        @Override public boolean onKeyDown(int code,KeyEvent event){if(code==KeyEvent.KEYCODE_DPAD_RIGHT){go(page+1);return true;}if(code==KeyEvent.KEYCODE_DPAD_LEFT){go(page-1);return true;}return super.onKeyDown(code,event);}
    }
    final class Glyph extends View {
        final boolean gear; final Paint p=new Paint(3);
        Glyph(boolean gear){super(Home.this);this.gear=gear;setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);}
        @Override protected void onDraw(Canvas original){Canvas c=original;int save=c.save();float size=Math.min(getWidth(),getHeight())*(gear?.45f:.95f);c.translate((getWidth()-size)/2,(getHeight()-size)/2);c.scale(size/24,size/24);p.setColor(AMBER);p.setStrokeWidth(1.7f);p.setStyle(Paint.Style.STROKE);
            if(gear){Path path=new Path();for(int i=0;i<48;i++){double a=i*Math.PI*2/48;float r=(i%8>=2&&i%8<=5)?10:8;float x=12+(float)Math.cos(a)*r,y=12+(float)Math.sin(a)*r;if(i==0)path.moveTo(x,y);else path.lineTo(x,y);}path.close();c.drawPath(path,p);c.drawCircle(12,12,3,p);}
            else{for(int i=0;i<3;i++){float r=10-i*3.5f;c.drawArc(new RectF(12-r,17-r,12+r,17+r),225,90,false,p);}p.setStyle(Paint.Style.FILL);c.drawCircle(12,19,1,p);}c.restoreToCount(save);
        }
    }
}
