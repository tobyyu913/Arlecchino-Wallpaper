package com.tobyyu.arlecchinowallpaper;
import android.app.*;
import android.content.*;
import android.os.*;
import android.provider.Settings;
import android.media.*;
import android.media.session.MediaSession;
import android.media.session.PlaybackState;
import android.view.*;
import java.lang.reflect.*;
import java.util.concurrent.atomic.*;

/** Public-API controls exercised on a disposable emulator, never a user's phone. */
public final class ControlCenterTests extends Instrumentation {
  Activity activity;DuoControlCenter panel;MediaSession session;AudioManager audio;int oldVolume,oldBrightness,oldMode,oldRotation;
  AtomicInteger plays=new AtomicInteger(),pauses=new AtomicInteger(),next=new AtomicInteger();
  Object field(Object owner,String name){try{Field f=owner.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(owner);}catch(Exception e){throw new RuntimeException(e);}}
  void main(Runnable work){runOnMainSync(work);}
  interface Condition {boolean test();}
  void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
  void await(Condition condition,String message){long end=SystemClock.uptimeMillis()+5000;while(SystemClock.uptimeMillis()<end){AtomicBoolean ok=new AtomicBoolean();main(()->ok.set(condition.test()));if(ok.get())return;SystemClock.sleep(25);}throw new AssertionError(message);}
  void call(String name,int value){main(()->{try{Method m=DuoControlCenter.class.getDeclaredMethod(name,int.class);m.setAccessible(true);m.invoke(panel,value);}catch(Exception e){throw new RuntimeException(e);}});}
  void progress(String field,float value){main(()->{Bundle b=new Bundle();b.putFloat(android.view.accessibility.AccessibilityNodeInfo.ACTION_ARGUMENT_PROGRESS_VALUE,value);((View)field(panel,field)).performAccessibilityAction(android.view.accessibility.AccessibilityNodeInfo.AccessibilityAction.ACTION_SET_PROGRESS.getId(),b);});}
  void open(){main(()->((View)field(activity,"duoStatus")).performClick());panel=(DuoControlCenter)field(field(activity,"duoStatus"),"popup");await(()->((View)field(panel,"body")).getScaleX()==1,"Expansion failed to settle");}
  @Override public void onCreate(Bundle args){super.onCreate(args);start();}
  @Override public void onStart(){Bundle result=new Bundle();try{
    activity=startActivitySync(new Intent(getTargetContext(),HomeActivity.class).setAction(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
    audio=activity.getSystemService(AudioManager.class);oldVolume=audio.getStreamVolume(AudioManager.STREAM_MUSIC);oldBrightness=Settings.System.getInt(activity.getContentResolver(),Settings.System.SCREEN_BRIGHTNESS,128);oldMode=Settings.System.getInt(activity.getContentResolver(),Settings.System.SCREEN_BRIGHTNESS_MODE,0);oldRotation=Settings.System.getInt(activity.getContentResolver(),Settings.System.ACCELEROMETER_ROTATION,1);
    main(()->{session=new MediaSession(activity,"Control Center synthetic player");session.setCallback(new MediaSession.Callback(){@Override public void onPlay(){plays.incrementAndGet();session.setPlaybackState(state(PlaybackState.STATE_PLAYING));}@Override public void onPause(){pauses.incrementAndGet();session.setPlaybackState(state(PlaybackState.STATE_PAUSED));}@Override public void onSkipToNext(){next.incrementAndGet();}},new Handler(Looper.getMainLooper()));session.setMetadata(new MediaMetadata.Builder().putString(MediaMetadata.METADATA_KEY_TITLE,"Control Center Test").putString(MediaMetadata.METADATA_KEY_ARTIST,"Synthetic session").build());session.setPlaybackState(state(PlaybackState.STATE_PAUSED));session.setActive(true);});
    open();
    await(()->field(panel,"current")!=null,"Media session not connected");
    progress("volume",60);await(()->audio.getStreamVolume(AudioManager.STREAM_MUSIC)==Math.round(audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)*.6f),"Volume slider did not control system media volume");
    progress("brightness",65);await(()->Settings.System.getInt(activity.getContentResolver(),Settings.System.SCREEN_BRIGHTNESS,0)==166,"Brightness did not change the system setting");
    call("transport",1);await(()->plays.get()==1,"Play control failed");call("transport",1);await(()->pauses.get()==1,"Pause control failed");call("transport",2);await(()->next.get()==1,"Next control failed");
    main(()->((View)field(panel,"rotation")).performClick());await(()->Settings.System.getInt(activity.getContentResolver(),Settings.System.ACCELEROMETER_ROTATION,1)!=oldRotation,"Rotation lock failed");
    check(getUiAutomation().performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK),"System Back unavailable");await(()->!panel.isShowing(),"Native Back dismissal animation failed");
    main(()->check((Boolean)field(panel,"cleaned")&&((java.util.List<?>)field(panel,"sessions")).isEmpty(),"Dismissed panel retained listeners"));
    open();main(()->panel.updateStatus(76,false,false,true,"5G",false));
    main(()->check(((android.widget.TextView)field(panel,"status")).getText().toString().contains("5G"),"Expanded status lost mobile label"));
    IntentFilter wifi=new IntentFilter(Settings.Panel.ACTION_WIFI);ActivityMonitor monitor=addMonitor(wifi,new ActivityResult(Activity.RESULT_OK,null),true);
    main(()->((View)field(panel,"wifi")).performClick());await(()->monitor.getHits()>0,"Wi-Fi control did not open Android system controls");removeMonitor(monitor);
    await(()->!panel.isShowing(),"Settings handoff left an overlay");
    result.putString("stream","PASS: expansion/dismissal, system volume/brightness/rotation, media play/pause/next, mobile status, Wi-Fi system-control handoff and listener cleanup\n");
  }catch(Throwable failure){result.putString("stream","FAIL: "+failure+"\n");}finally{main(()->{if(panel!=null)panel.closeImmediately();if(session!=null)session.release();if(audio!=null)audio.setStreamVolume(AudioManager.STREAM_MUSIC,oldVolume,0);if(activity!=null){Settings.System.putInt(activity.getContentResolver(),Settings.System.SCREEN_BRIGHTNESS,oldBrightness);Settings.System.putInt(activity.getContentResolver(),Settings.System.SCREEN_BRIGHTNESS_MODE,oldMode);Settings.System.putInt(activity.getContentResolver(),Settings.System.ACCELEROMETER_ROTATION,oldRotation);}});finish(Activity.RESULT_OK,result);}}
  PlaybackState state(int value){return new PlaybackState.Builder().setState(value,0,1).setActions(PlaybackState.ACTION_PLAY|PlaybackState.ACTION_PAUSE|PlaybackState.ACTION_SKIP_TO_NEXT).build();}
}
