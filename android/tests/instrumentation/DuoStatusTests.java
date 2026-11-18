package com.tobyyu.arlecchinowallpaper;
import android.app.*;
import android.content.*;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.lang.reflect.*;
import java.util.concurrent.atomic.AtomicBoolean;

public final class DuoStatusTests extends Instrumentation {
  Activity activity;
  Object field(String name){try{Field f=HomeActivity.class.getDeclaredField(name);f.setAccessible(true);return f.get(activity);}catch(Exception e){throw new RuntimeException(e);}}
  Object statusField(String name){try{Field f=DuoStatusView.class.getDeclaredField(name);f.setAccessible(true);return f.get(field("duoStatus"));}catch(Exception e){throw new RuntimeException(e);}}
  View root(){return (View)field("root");}
  DuoStatusView status(){return (DuoStatusView)field("duoStatus");}
  void main(Runnable task){runOnMainSync(task);}
  void check(boolean value,String message){if(!value)throw new AssertionError(message);}
  interface Condition{boolean test();}
  void await(Condition condition,String message){long end=SystemClock.uptimeMillis()+5000;while(SystemClock.uptimeMillis()<end){AtomicBoolean ok=new AtomicBoolean();main(()->ok.set(condition.test()));if(ok.get())return;SystemClock.sleep(20);}throw new AssertionError(message);}
  void apply(){main(()->{try{Method method=HomeActivity.class.getDeclaredMethod("applyStatusBarMode");method.setAccessible(true);method.invoke(activity);root().requestApplyInsets();}catch(Exception e){throw new RuntimeException(e);}});}
  boolean hidden(){return root().getRootWindowInsets()!=null&&!root().getRootWindowInsets().isVisible(WindowInsets.Type.statusBars());}
  boolean text(View root,String value){if(root instanceof TextView&&((TextView)root).getText().toString().contains(value))return true;if(root instanceof android.view.ViewGroup)for(int i=0;i<((android.view.ViewGroup)root).getChildCount();i++)if(text(((android.view.ViewGroup)root).getChildAt(i),value))return true;return false;}
  @Override public void onCreate(Bundle args){super.onCreate(args);start();}
  @Override public void onStart(){Bundle result=new Bundle();try{
    activity=startActivitySync(new Intent(getTargetContext(),HomeActivity.class).setAction(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
    await(()->status().getVisibility()==View.VISIBLE&&hidden(),"Native status bar was not hidden on launcher");
    await(()->status().getContentDescription().toString().contains("Battery 55 percent"),"Live battery status missing");
    main(()->{check(activity.checkSelfPermission(android.Manifest.permission.ACCESS_WIFI_STATE)==android.content.pm.PackageManager.PERMISSION_GRANTED,"Wi-Fi permission not normal/granted");status().performClick();});
    await(()->statusField("popup")!=null&&((DuoControlCenter)statusField("popup")).isShowing(),"Status panel failed to expand");
    await(this::hidden,"Expanded panel revealed the native status bar");
    main(()->{check(text(((DuoControlCenter)statusField("popup")).getWindow().getDecorView(),"55%"),"Expanded battery value missing");check(hidden(),"Expanded panel revealed the native status bar");((DuoControlCenter)statusField("popup")).closeImmediately();});
    main(()->{getTargetContext().getSharedPreferences("home",0).edit().putBoolean("duoStatus",false).apply();status().stop();});apply();
    await(()->status().getVisibility()==View.GONE&&!hidden(),"Disabling Duo did not restore normal status bar");
    main(()->{getTargetContext().getSharedPreferences("home",0).edit().putBoolean("duoStatus",true).apply();status().start();});apply();
    await(()->status().getVisibility()==View.VISIBLE&&hidden(),"Reenabling Duo failed");
    main(()->activity.startActivity(new Intent(android.provider.Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)));
    await(()->!(Boolean)statusField("started"),"Status listeners were not stopped in background");
    ActivityMonitor homes=addMonitor(HomeActivity.class.getName(),null,false);
    check(getUiAutomation().performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_HOME),"System Home failed");
    Activity returned=homes.waitForActivityWithTimeout(2000);if(returned!=null)activity=returned;removeMonitor(homes);
    await(()->activity.hasWindowFocus()&&hidden()&&(Boolean)statusField("started"),"Status did not resume/hide native bar after returning Home");
    result.putString("stream","PASS: launcher-only fullscreen, live battery, expanded status, normal-bar toggle, background cleanup and Home return; no root/overlay or dangerous phone permission\n");finish(Activity.RESULT_OK,result);
  }catch(Throwable failure){
    try {
      android.graphics.Bitmap screenshot=getUiAutomation().takeScreenshot();
      java.io.File file=new java.io.File(getTargetContext().getExternalFilesDir(null),"duo-test-failure.png");
      java.io.FileOutputStream output=new java.io.FileOutputStream(file);screenshot.compress(android.graphics.Bitmap.CompressFormat.PNG,100,output);output.close();
    }catch(Exception ignored){}
    result.putString("stream","FAIL: "+failure+" focus="+activity.hasWindowFocus()+" hidden="+hidden()+" statusVisibility="+status().getVisibility()+" started="+statusField("started")+" flags="+activity.getWindow().getAttributes().flags+" pref="+getTargetContext().getSharedPreferences("home",0).getBoolean("duoStatus",false));finish(Activity.RESULT_CANCELED,result);}}
}
