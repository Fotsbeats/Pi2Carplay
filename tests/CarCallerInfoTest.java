import android.content.Context;
import android.content.ContextWrapper;
import android.content.ContentResolver;
import android.content.ContentProviderClient;
import android.database.Cursor;
import android.os.Looper;
import android.provider.ContactsContract;
import android.telecom.TelecomManager;
import java.lang.reflect.*;

/** Checks a real imported contact without printing names or numbers, and public/private fallbacks. */
public final class CarCallerInfoTest {
 public static void main(String[]args)throws Exception {
  Looper.prepareMainLooper();Class<?> thread=Class.forName("android.app.ActivityThread");Object activity=thread.getMethod("systemMain").invoke(null);Context base=(Context)thread.getMethod("getSystemContext").invoke(activity);
  // app_process has no registered application thread; use the same external-provider path as adb content.
  Object manager=Class.forName("android.app.ActivityManager").getMethod("getService").invoke(null);Class<?> api=Class.forName("android.app.IActivityManager");
  Object holder=api.getMethod("getContentProviderExternal",String.class,int.class,android.os.IBinder.class,String.class).invoke(manager,"com.android.contacts",0,new android.os.Binder(),"PiCallerInfoTest");
  Object provider=holder.getClass().getField("provider").get(holder);Constructor<ContentProviderClient> constructor=ContentProviderClient.class.getDeclaredConstructor(ContentResolver.class,Class.forName("android.content.IContentProvider"),boolean.class);constructor.setAccessible(true);
  ContentResolver resolver=ContentResolver.wrap(constructor.newInstance(base.getContentResolver(),provider,true));
  Context context=new ContextWrapper(base){public Context getApplicationContext(){return this;}public ContentResolver getContentResolver(){return resolver;}};
  Class<?> book=Class.forName("com.android.car.telephony.common.InMemoryPhoneBook");Object cache=book.getMethod("init",Context.class).invoke(null,context);
  new Thread(()->{try {
   for(int i=0;i<100 && !(Boolean)book.getMethod("isLoaded").invoke(cache);i++)Thread.sleep(100);
   if(!(Boolean)book.getMethod("isLoaded").invoke(cache))throw new AssertionError("Phonebook did not load");
   Class<?> info=Class.forName("com.android.car.dialer.ui.PiCallerInfo");Method label=info.getMethod("label",String.class,String.class,int.class);
   int checked=0;
   try(Cursor row=context.getContentResolver().query(ContactsContract.CommonDataKinds.Phone.CONTENT_URI,new String[]{"data1","display_name","account_name"},"account_type=?",new String[]{"com.android.bluetooth.pbapclient"},null)){
    while(row!=null && row.moveToNext() && checked<5){String number=row.getString(0),name=row.getString(1),account=row.getString(2);String result=(String)label.invoke(null,number,account,TelecomManager.PRESENTATION_ALLOWED);if(!name.equals(result))throw new AssertionError("Saved contact did not resolve");checked++;}
   }
   if(checked==0)throw new AssertionError("No synced contact available for the check");
   String unknown="1555010987654321";String result=(String)label.invoke(null,unknown,"unknown-test-account",TelecomManager.PRESENTATION_ALLOWED);
   if(!result.replaceAll("[^0-9]","").equals(unknown))throw new AssertionError("Unknown contact did not show its number");
   if(!"Private caller".equals(label.invoke(null,unknown,"",TelecomManager.PRESENTATION_RESTRICTED)))throw new AssertionError("Private number exposed");
   if(!"Unknown caller".equals(label.invoke(null,null,"",TelecomManager.PRESENTATION_UNKNOWN)))throw new AssertionError("Missing number fallback failed");
   System.out.println("PASS: "+checked+" synced contacts resolve by name; unsaved numbers and private/unknown calls have correct fallbacks");System.exit(0);
  }catch(Throwable e){e.printStackTrace();System.exit(1);}},"Caller-name-check").start();Looper.loop();
 }
}
