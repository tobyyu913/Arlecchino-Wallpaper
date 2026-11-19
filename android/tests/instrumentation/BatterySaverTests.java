package com.tobyyu.arlecchinowallpaper;
import android.app.*;
import android.content.*;
import android.os.*;
import android.view.*;
import android.view.accessibility.AccessibilityNodeInfo;
import java.lang.reflect.*;
import java.util.concurrent.atomic.AtomicBoolean;

/** Requires official Shizuku running on a disposable emulator with an unplugged battery fixture. */
public final class BatterySaverTests extends Instrumentation {
  Activity activity;DuoControlCenter panel;
  Object field(Object o,String name){try{Field f=o.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(o);}catch(Exception e){throw new RuntimeException(e);}}
  void main(Runnable task){runOnMainSync(task);}
  interface Condition{boolean test();}
  void check(boolean value,String message){if(!value)throw new AssertionError(message);}
  boolean allow(AccessibilityNodeInfo n){if(n==null)return false;CharSequence pkg=n.getPackageName(),label=n.getText();if(pkg!=null&&pkg.toString().equals("moe.shizuku.privileged.api")&&label!=null&&label.toString().startsWith("Allow"))return n.performAction(AccessibilityNodeInfo.ACTION_CLICK);for(int i=0;i<n.getChildCount();i++)if(allow(n.getChild(i)))return true;return false;}
  void await(Condition c,String message){long end=SystemClock.uptimeMillis()+12000;while(SystemClock.uptimeMillis()<end){AtomicBoolean ok=new AtomicBoolean();main(()->ok.set(c.test()));if(ok.get())return;allow(getUiAutomation().getRootInActiveWindow());SystemClock.sleep(50);}throw new AssertionError(message);}
  @Override public void onCreate(Bundle args){super.onCreate(args);start();}
  @Override public void onStart(){Bundle result=new Bundle();try{
    activity=startActivitySync(new Intent(getTargetContext(),HomeActivity.class).setAction(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
    main(()->((View)field(activity,"duoStatus")).performClick());panel=(DuoControlCenter)field(field(activity,"duoStatus"),"popup");
    await(()->((View)field(panel,"body")).getScaleX()==1,"Panel did not open");
    PowerManager power=activity.getSystemService(PowerManager.class);check(!power.isPowerSaveMode(),"Fixture started in battery saver");
    main(()->((View)field(panel,"batterySaver")).performClick());
    await(power::isPowerSaveMode,"Shizuku did not enable real Battery Saver");
    await(()->!((BatterySaverControl)field(panel,"batteryControl")).busy(),"Toggle never finished");
    check(panel.isShowing(),"Permission/toggle dismissed Control Center");
    main(()->((View)field(panel,"batterySaver")).performClick());
    await(()->!power.isPowerSaveMode(),"Second tap did not disable real Battery Saver");
    await(()->!((BatterySaverControl)field(panel,"batteryControl")).busy(),"Disable never finished");
    result.putString("stream","PASS: official Shizuku permission flow and shell UserService turn actual Battery Saver on and off, confirm system state and keep Control Center open\n");
  }catch(Throwable failure){result.putString("stream","FAIL: "+failure+" showing="+(panel!=null&&panel.isShowing())+" closed="+(panel==null?"?":field(field(panel,"batteryControl"),"closed"))+" busy="+(panel==null?"?":field(field(panel,"batteryControl"),"busy"))+"\n");}finally{main(()->{if(panel!=null)panel.closeImmediately();});finish(Activity.RESULT_OK,result);}}
}
