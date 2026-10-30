import android.content.Context;
import android.graphics.*;
import android.graphics.drawable.Drawable;
import java.io.FileOutputStream;

/** Renders the installed icon and checks square shoulders plus transparent rounded corners. */
public final class CarDialerIconTest {
 public static void main(String[] args)throws Exception {
  android.os.Looper.prepareMainLooper();Class<?> thread=Class.forName("android.app.ActivityThread");Object activity=thread.getMethod("systemMain").invoke(null);
  Context context=(Context)thread.getMethod("getSystemContext").invoke(activity);
  Drawable icon=context.getPackageManager().getApplicationIcon("com.android.car.dialer");Bitmap image=Bitmap.createBitmap(192,192,Bitmap.Config.ARGB_8888);
  icon.setBounds(0,0,192,192);icon.draw(new Canvas(image));
  if(Color.alpha(image.getPixel(16,40))<240 || Color.alpha(image.getPixel(0,0))!=0)throw new AssertionError("Icon is not a rounded square");
  if(Color.alpha(image.getPixel(0,96))<240 || Color.alpha(image.getPixel(96,0))<240 || Color.alpha(image.getPixel(191,96))<240 || Color.alpha(image.getPixel(96,191))<240)throw new AssertionError("Extra icon padding changes its launcher size");
  if(args.length==1)try(FileOutputStream out=new FileOutputStream(args[0])){image.compress(Bitmap.CompressFormat.PNG,100,out);}
  System.out.println("PASS: installed Phone icon fills the launcher drawable with rounded transparent corners");System.exit(0);
 }
}
