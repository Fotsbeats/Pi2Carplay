import android.graphics.Rect;
import java.lang.reflect.Method;

/** Run with the target services.jar on app_process's CLASSPATH; requires root. */
public final class KeyboardRailInsetsTest {
    public static void main(String[] args) throws Exception {
        Class<?> properties=Class.forName("android.os.SystemProperties");
        Method set=properties.getMethod("set",String.class,String.class);
        String previous=(String)properties.getMethod("get",String.class).invoke(null,"sys.boxhome.rail.ready");
        Class<?> state=Class.forName("android.view.InsetsState");
        Method frame=state.getMethod("getDisplayFrame"),peek=state.getMethod("peekSource",int.class);
        Method helper=Class.forName("com.android.server.wm.WindowState").getMethod("boxKeyboardRailInsets",state,int.class);
        Object original=state.getConstructor().newInstance();
        ((Rect)frame.invoke(original)).set(0,0,1744,720);
        try {
            set.invoke(null,"sys.boxhome.rail.ready","1");
            Object metrics=state.getConstructor().newInstance();
            Object wm=Class.forName("android.view.WindowManagerGlobal").getMethod("getWindowManagerService").invoke(null);
            Class.forName("android.view.IWindowManager").getMethod("getWindowInsets",int.class,android.os.IBinder.class,state).invoke(wm,0,null,metrics);
            if(peek.invoke(metrics,993)!=null)throw new AssertionError("Keyboard inset leaked to a different caller");
            for(int type:new int[]{2011,2012}) {
                Object result=helper.invoke(null,original,type);
                if(result==original||peek.invoke(original,993)!=null)throw new AssertionError("Modified shared insets");
                Object source=peek.invoke(result,993);
                Rect bounds=(Rect)source.getClass().getMethod("getFrame").invoke(source);
                if(!bounds.equals(new Rect(0,0,104,720)))throw new AssertionError(bounds);
                if(!(Boolean)source.getClass().getMethod("isVisible").invoke(source))throw new AssertionError("Hidden rail inset");
            }
            for(int type:new int[]{1,2,2032})
                if(helper.invoke(null,original,type)!=original)throw new AssertionError("Affected non-keyboard window");
            ((Rect)frame.invoke(original)).set(0,0,319,720);
            if(helper.invoke(null,original,2011)!=original)throw new AssertionError("Accepted invalid display width");
            ((Rect)frame.invoke(original)).set(0,0,1744,720);
            set.invoke(null,"sys.boxhome.rail.ready","0");
            if(helper.invoke(null,original,2011)!=original)throw new AssertionError("Inset without rail");
            System.out.println("PASS: keyboard types, 104px bounds, shared-state and caller isolation, other windows and readiness gate");
        } finally { set.invoke(null,"sys.boxhome.rail.ready",previous); }
    }
}
