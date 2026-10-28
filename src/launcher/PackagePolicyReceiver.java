package org.bench.boxhome;

import android.content.*;
import android.content.pm.ApplicationInfo;
import java.io.File;
import java.io.IOException;

/** Protected install broadcasts queue fixed presentation-policy requests for init. */
public final class PackagePolicyReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context,Intent intent){
        if(!Intent.ACTION_PACKAGE_ADDED.equals(intent.getAction())&& !Intent.ACTION_PACKAGE_REPLACED.equals(intent.getAction()))return;
        String pkg=intent.getData()==null?null:intent.getData().getSchemeSpecificPart();
        if(pkg==null||!pkg.matches("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z0-9_]+)+"))return;
        try{
            ApplicationInfo info=context.getPackageManager().getApplicationInfo(pkg,0);
            if((info.flags&ApplicationInfo.FLAG_SYSTEM)!=0)return;
        }catch(Exception e){return;}
        PendingResult pending=goAsync();
        new Thread(()->{
            try{
                File queue=new File(context.getFilesDir(),"pending-policy");
                if(!queue.isDirectory()&&!queue.mkdirs())throw new IOException("Cannot create policy queue");
                File request=new File(queue,pkg);
                if(!request.exists()&&!request.createNewFile())throw new IOException("Cannot queue app policy");
                Class.forName("android.os.SystemProperties").getMethod("set",String.class,String.class).invoke(null,"sys.boxhome.apps.pending","1");
            }catch(Exception e){android.util.Log.w("BoxHome","Could not queue landscape policy for "+pkg,e);}
            finally{pending.finish();}
        },"home-package-policy").start();
    }
}
