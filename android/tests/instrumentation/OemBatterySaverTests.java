package com.tobyyu.arlecchinowallpaper;
import android.app.*;
import android.content.*;
import android.os.*;
import android.view.*;
import java.lang.reflect.*;
import java.util.concurrent.atomic.AtomicBoolean;
public final class OemBatterySaverTests extends Instrumentation {
  Activity activity;DuoControlCenter panel;
  Object field(Object o,String n){try{Field f=o.getClass().getDeclaredField(n);f.setAccessible(true);return f.get(o);}catch(Exception e){throw new RuntimeException(e);}}
  void main(Runnable work){runOnMainSync(work);}
  interface Condition{boolean test();}
  void check(boolean value,String message){if(!value)throw new AssertionError(message);}
  void await(Condition c,String m){long end=SystemClock.uptimeMillis()+12000;while(SystemClock.uptimeMillis()<end){AtomicBoolean ok=new AtomicBoolean();main(()->ok.set(c.test()));if(ok.get())return;SystemClock.sleep(40);}throw new AssertionError(m);}
  public void onCreate(Bundle b){super.onCreate(b);start();}
  public void onStart(){Bundle result=new Bundle();try{
    activity=startActivitySync(new Intent(getTargetContext(),HomeActivity.class).setAction(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
    PowerManager power=activity.getSystemService(PowerManager.class);
    check(power.isPowerSaveMode(),"Android fixture should be enabled");check(!BatterySaverState.enabled(activity),"OEM off must override Android on");
    main(()->((View)field(activity,"duoStatus")).performClick());panel=(DuoControlCenter)field(field(activity,"duoStatus"),"popup");
    await(()->((View)field(panel,"body")).getScaleX()==1,"Panel did not open");
    check(!(Boolean)field(field(panel,"batterySaver"),"active"),"Button falsely indicated OEM enabled");
    main(()->((View)field(panel,"batterySaver")).performClick());
    await(()->"OEM_PERMISSION".equals(field(field(panel,"batteryControl"),"lastError")),"Signature permission failure was not identified");
    check(!BatterySaverState.enabled(activity),"Failed OEM command changed native state");check(power.isPowerSaveMode(),"Failed OEM command changed Android flag");
    check(!(Boolean)field(field(panel,"batterySaver"),"active"),"Permission failure showed false success");
    check(((AlertDialog)field(field(panel,"batteryControl"),"errorDialog")).isShowing(),"OEM limitation was not shown");
    result.putString("stream","PASS: OEM-off overrides Android-on; actual Shizuku provider permission denial is reported without false success or altering Android state\n");
  }catch(Throwable failure){result.putString("stream","FAIL: "+failure+"\n");}finally{main(()->{if(panel!=null)panel.closeImmediately();});finish(Activity.RESULT_OK,result);}}
}
