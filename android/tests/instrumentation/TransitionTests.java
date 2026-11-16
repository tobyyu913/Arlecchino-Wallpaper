package com.tobyyu.arlecchinowallpaper;
import android.app.*;
import android.content.*;
import android.os.*;
import android.view.*;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

/** Synthetic input on a disposable emulator; never edits physical-phone favorites. */
public final class TransitionTests extends Instrumentation {
  Activity activity;
  Object field(String name) {
    try { Field f=HomeActivity.class.getDeclaredField(name);f.setAccessible(true);return f.get(activity); }
    catch(Exception e){throw new RuntimeException(e);}
  }
  View view(String name){return (View)field(name);}
  void check(boolean value,String message){if(!value)throw new AssertionError(message);}
  void main(Runnable action){runOnMainSync(action);}
  interface Condition {boolean test();}
  void await(Condition condition,String message){
    long end=SystemClock.uptimeMillis()+3000;
    while(SystemClock.uptimeMillis()<end){AtomicBoolean ok=new AtomicBoolean();main(()->ok.set(condition.test()));if(ok.get())return;SystemClock.sleep(8);}
    throw new AssertionError(message);
  }
  void touch(int action,int index){
    main(()->{
      View root=view("root");float density=activity.getResources().getDisplayMetrics().density;
      float start=root.getHeight()*.29f;
      float step=(root.getHeight()-start-(Integer)field("bottomInset")-36*density)/28;
      float y=start+step*(index+.5f),x=root.getWidth()-8*density;
      long time=SystemClock.uptimeMillis();
      MotionEvent event=MotionEvent.obtain(time,time,action,x,y,0);
      activity.dispatchTouchEvent(event);event.recycle();
    });
  }
  @Override public void onCreate(Bundle args){super.onCreate(args);start();}
  @Override public void onStart(){
    Bundle result=new Bundle();
    try{
      activity=startActivitySync(new Intent(getTargetContext(),HomeActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
      await(()->((List<?>)field("apps")).size()>1,"App catalog did not load");
      main(()->{
        try{
          List<?> apps=(List<?>)field("apps");StringBuilder ids=new StringBuilder();
          for(int i=0;i<2;i++){Field id=apps.get(i).getClass().getDeclaredField("id");id.setAccessible(true);ids.append(id.get(apps.get(i))).append('\n');}
          getTargetContext().getSharedPreferences("home",0).edit().putString("favorites",ids.toString()).apply();
          for(String method:new String[]{"loadSets","home"}){Method m=HomeActivity.class.getDeclaredMethod(method);m.setAccessible(true);m.invoke(activity);}
        }catch(Exception e){throw new RuntimeException(e);}
      });waitForIdleSync();
      touch(MotionEvent.ACTION_DOWN,0);SystemClock.sleep(40);touch(MotionEvent.ACTION_MOVE,4);
      await(()->(Boolean)field("departingFavorites")&&view("gestureLayer").getAlpha()>0.02f&&view("gestureLayer").getAlpha()<.95f,"No intermediate departure frames");
      main(()->{
        View incoming=view("gestureLayer"),outgoing=view("favoritesDeparture");
        check(view("list").getAlpha()==0,"Rebuilt list flashed through departure");
        check(incoming.getTranslationY()>0&&outgoing.getTranslationY()<0,"Transition must move both surfaces continuously");
        check(Math.abs(incoming.getAlpha()+outgoing.getAlpha()-1)<.02,"Incoming/outgoing fades drift apart");
      });
      final Object[] snapshot = new Object[1];
      main(()->snapshot[0]=field("departureBitmap"));
      touch(MotionEvent.ACTION_UP,4);
      main(()->check((Boolean)field("pendingGestureSettle")&&view("list").getAlpha()==0,"Quick release must wait for departure"));
      await(()->view("gestureLayer").getVisibility()==View.GONE&&view("list").getAlpha()==1,"Released section did not settle");
      touch(MotionEvent.ACTION_DOWN,0);SystemClock.sleep(40);touch(MotionEvent.ACTION_MOVE,4);
      await(()->(Boolean)field("departingFavorites")&&view("gestureLayer").getAlpha()>.02f,"Second departure did not start");
      main(()->check(field("departureBitmap")==snapshot[0],"Departure snapshot should be reused"));
      final Object[] group = new Object[1];
      main(()->group[0]=((android.widget.ScrollView)view("gestureScroll")).getChildAt(0));
      touch(MotionEvent.ACTION_MOVE,5);
      await(()->(Integer)field("previewIndex")==5,"Neighbor section missing");
      touch(MotionEvent.ACTION_MOVE,4);
      await(()->(Integer)field("previewIndex")==4,"Returning section missing");
      main(()->check(((android.widget.ScrollView)view("gestureScroll")).getChildAt(0)==group[0],"Returning to a section should reuse its views"));
      touch(MotionEvent.ACTION_MOVE,0);
      await(()->!(Boolean)field("all")&&view("list").getAlpha()==1&&view("gestureLayer").getVisibility()==View.GONE,"Held star must restore favorites");
      touch(MotionEvent.ACTION_UP,0);SystemClock.sleep(400);
      main(()->check(!(Boolean)field("all")&&view("favoritesDeparture").getVisibility()==View.GONE&&view("gestureLayer").getTranslationY()==0&&view("list").getAlpha()==1,"Canceled departure changed Home later"));
      result.putString("stream","PASS: intermediate slide/fade frames, hidden rebuilt list, quick-release handoff, reused snapshots/section views and interrupted return to favorites\n");
      finish(Activity.RESULT_OK,result);
    }catch(Throwable error){result.putString("stream","FAIL: "+error);finish(Activity.RESULT_CANCELED,result);}
  }
}
