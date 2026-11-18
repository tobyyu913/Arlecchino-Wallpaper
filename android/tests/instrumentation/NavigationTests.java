package com.tobyyu.arlecchinowallpaper;
import android.app.*;
import android.content.*;
import android.os.*;
import android.view.*;
import android.accessibilityservice.AccessibilityService;
import java.lang.reflect.*;
import java.util.concurrent.atomic.AtomicBoolean;

/** Run only on a disposable emulator configured for system gesture navigation. */
public final class NavigationTests extends Instrumentation {
  Activity activity;
  Object field(String name){try{Field f=HomeActivity.class.getDeclaredField(name);f.setAccessible(true);return f.get(activity);}catch(Exception e){throw new RuntimeException(e);}}
  View view(String name){return (View)field(name);}
  void main(Runnable action){runOnMainSync(action);}
  void call(String name){main(()->{try{Method m=HomeActivity.class.getDeclaredMethod(name);m.setAccessible(true);m.invoke(activity);}catch(Exception e){throw new RuntimeException(e);}});}
  void check(boolean value,String message){if(!value)throw new AssertionError(message);}
  interface Condition{boolean test();}
  void await(Condition condition,String message){long end=SystemClock.uptimeMillis()+5000;while(SystemClock.uptimeMillis()<end){AtomicBoolean ok=new AtomicBoolean();main(()->ok.set(condition.test()));if(ok.get())return;SystemClock.sleep(20);}throw new AssertionError(message);}
  void swipe(float x0,float y0,float x1,float y1,int hold){
    long down=SystemClock.uptimeMillis();
    MotionEvent event=MotionEvent.obtain(down,down,MotionEvent.ACTION_DOWN,x0,y0,0);event.setSource(InputDevice.SOURCE_TOUCHSCREEN);getUiAutomation().injectInputEvent(event,true);event.recycle();
    for(int i=1;i<=20;i++){SystemClock.sleep(15);event=MotionEvent.obtain(down,SystemClock.uptimeMillis(),MotionEvent.ACTION_MOVE,x0+(x1-x0)*i/20,y0+(y1-y0)*i/20,0);event.setSource(InputDevice.SOURCE_TOUCHSCREEN);getUiAutomation().injectInputEvent(event,true);event.recycle();}
    SystemClock.sleep(hold);event=MotionEvent.obtain(down,SystemClock.uptimeMillis(),MotionEvent.ACTION_UP,x1,y1,0);event.setSource(InputDevice.SOURCE_TOUCHSCREEN);getUiAutomation().injectInputEvent(event,true);event.recycle();
  }
  void railTouch(int action,int index){main(()->{
    View root=view("root");float density=activity.getResources().getDisplayMetrics().density;
    float start=root.getHeight()*.29f,step=(root.getHeight()-start-Math.max((Integer)field("bottomInset"),(Integer)field("gestureBottom"))-36*density)/28;
    long now=SystemClock.uptimeMillis();MotionEvent e=MotionEvent.obtain(now,now,action,root.getWidth()-(Integer)field("gestureRight")-8*density,start+step*(index+.5f),0);activity.dispatchTouchEvent(e);e.recycle();
  });}
  @Override public void onCreate(Bundle args){super.onCreate(args);start();}
  @Override public void onStart(){Bundle result=new Bundle();try{
    activity=startActivitySync(new Intent(getTargetContext(),HomeActivity.class).setAction(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
    await(()->view("root").getHeight()>0&&(Integer)field("gestureLeft")>0&&(Integer)field("gestureRight")>0,"System gesture insets missing");
    main(()->check(field("backCallback")!=null,"Modern Back callback not registered"));
    railTouch(MotionEvent.ACTION_DOWN,4);railTouch(MotionEvent.ACTION_UP,4);
    await(()->(Boolean)field("all"),"Inward alphabet band stopped working");SystemClock.sleep(500);
    int width=view("root").getWidth(),height=view("root").getHeight();
    swipe(2,height*.5f,width*.45f,height*.5f,0);
    await(()->!(Boolean)field("all")&&!(Boolean)field("searching"),"System Back swipe did not return favorites");
    call("openSearch");SystemClock.sleep(200);call("hideKeyboard");SystemClock.sleep(250);
    swipe(width-2,height*.5f,width*.55f,height*.5f,0);
    await(()->!(Boolean)field("searching"),"Right-edge Back did not close search");
    railTouch(MotionEvent.ACTION_DOWN,0);SystemClock.sleep(30);railTouch(MotionEvent.ACTION_MOVE,4);
    await(()->(Boolean)field("departingFavorites"),"Departure missing for cancellation test");
    railTouch(MotionEvent.ACTION_CANCEL,4);SystemClock.sleep(400);
    main(()->check(view("list").getAlpha()==1&&view("gestureLayer").getVisibility()==View.GONE,"System cancellation left a hidden list or stale preview"));
    main(()->activity.startActivity(new Intent(android.provider.Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)));
    await(()->!activity.hasWindowFocus(),"Test settings app did not open");SystemClock.sleep(350);
    ActivityMonitor homes = addMonitor(HomeActivity.class.getName(), null, false);
    swipe(width*.5f,height-2,width*.5f,height*.6f,0);
    Activity returnedHome = homes.waitForActivityWithTimeout(2000);
    if (returnedHome != null) activity = returnedHome;
    removeMonitor(homes);
    await(()->activity.hasWindowFocus()&&!(Boolean)field("all")&&!(Boolean)field("searching"),"Bottom Home swipe did not return favorites");
    check(getUiAutomation().performGlobalAction(AccessibilityService.GLOBAL_ACTION_RECENTS),"System Recents unavailable");
    await(()->!activity.hasWindowFocus(),"Recents did not take focus");
    main(()->check(view("list").getAlpha()==1&&view("gestureLayer").getVisibility()==View.GONE,"Recents interrupted UI incorrectly"));
    check(getUiAutomation().performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME),"System Home unavailable");
    await(()->activity.hasWindowFocus(),"Home did not resume launcher");
    result.putString("stream","PASS: gesture insets, inward alphabet band, left/right native Back, search Back, system cancellation, bottom Home swipe and Recents resume\n");finish(Activity.RESULT_OK,result);
  }catch(Throwable error){result.putString("stream","FAIL: "+error);finish(Activity.RESULT_CANCELED,result);}}
}
