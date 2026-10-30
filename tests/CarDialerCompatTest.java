import android.content.Context;
import android.os.Parcel;
import android.os.Parcelable;
import java.lang.reflect.*;

/** Exercises the real upstream Car UI Parcelable and optional-service fallback; places no calls. */
public final class CarDialerCompatTest {
    public static void main(String[] args)throws Exception {
        android.os.Looper.prepareMainLooper();
        Class<?> thread=Class.forName("android.app.ActivityThread");
        Object activity=thread.getMethod("systemMain").invoke(null);
        Context context=(Context)thread.getMethod("getSystemContext").invoke(activity);
        Class<?> builder=Class.forName("android.car.drivingstate.CarUxRestrictions$Builder");
        Class<?> restriction=Class.forName("android.car.drivingstate.CarUxRestrictions");
        Object built=builder.getMethod("build").invoke(builder.getConstructor(boolean.class,int.class,long.class).newInstance(true,9,123L));
        if((Integer)restriction.getMethod("getActiveRestrictions").invoke(built)!=9)throw new AssertionError("Restriction flags changed");
        Parcel parcel=Parcel.obtain();((Parcelable)built).writeToParcel(parcel,0);parcel.setDataPosition(0);
        Object copy=((Parcelable.Creator<?>)restriction.getField("CREATOR").get(null)).createFromParcel(parcel);parcel.recycle();
        if(!(Boolean)restriction.getMethod("isSameRestrictions",restriction).invoke(built,copy))throw new AssertionError("Parcelable roundtrip failed");
        Class<?> util=Class.forName("com.android.car.ui.utils.CarUxRestrictionsUtil");
        Object instance=util.getMethod("getInstance",Context.class).invoke(null,context);
        Object fallback=util.getMethod("getCurrentRestrictions").invoke(instance);
        if((Integer)restriction.getMethod("getActiveRestrictions").invoke(fallback)!=0)throw new AssertionError("Non-Automotive fallback failed");
        Object car=Class.forName("com.android.car.dialer.inject.DialerModules_AppModule_ProvideCarFactory").getMethod("provideCar",Context.class).invoke(null,context);
        if(car!=null)throw new AssertionError("Unexpected vehicle service");
        if(args.length==1 && args[0].equals("--installed")) {
            Context app=context.createPackageContext("com.android.car.dialer",Context.CONTEXT_IGNORE_SECURITY);
            Class<?> model=Class.forName("com.android.car.dialer.ui.dialpad.DialpadRestrictionViewModel");
            model.getConstructor(Context.class,Class.forName("android.car.Car")).newInstance(app,null);
            android.content.pm.ServiceInfo info=context.getPackageManager().getServiceInfo(new android.content.ComponentName("com.android.car.dialer","com.android.car.dialer.telecom.InCallServiceImpl"),android.content.pm.PackageManager.GET_META_DATA);
            if(!info.exported || !"android.permission.BIND_INCALL_SERVICE".equals(info.permission) || !info.metaData.getBoolean("android.telecom.IN_CALL_SERVICE_UI"))throw new AssertionError("In-call service registration invalid");
        }
        System.out.println("PASS: AOSP restriction flags/Parcelable, non-Automotive Car UI and optional CarService injection; no calls placed");
        System.exit(0);
    }
}
