package com.tobyyu.arlecchinowallpaper;
import android.app.*;
import android.content.*;
import android.media.*;
import android.media.session.*;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.util.concurrent.atomic.AtomicInteger;

public class MusicTests extends Instrumentation {
  Activity activity;
  MusicPlayerView view;
  MediaSession session, second;
  AtomicInteger plays = new AtomicInteger(), pauses = new AtomicInteger(), previous = new AtomicInteger(), next = new AtomicInteger();
  void check(boolean ok, String reason) { if (!ok) throw new AssertionError(reason); }
  void main(Runnable task) { runOnMainSync(task); }
  void settle() { waitForIdleSync(); SystemClock.sleep(350); }
  View find(View root, String text, String desc) {
    if (text != null && root instanceof TextView && text.contentEquals(((TextView)root).getText())) return root;
    if (desc != null && desc.equals(String.valueOf(root.getContentDescription()))) return root;
    if (root instanceof ViewGroup) for (int i=0;i<((ViewGroup)root).getChildCount();i++) { View found=find(((ViewGroup)root).getChildAt(i),text,desc);if(found!=null)return found; }
    return null;
  }
  MusicPlayerView findPlayer(View root) {
    if(root instanceof MusicPlayerView)return (MusicPlayerView)root;
    if(root instanceof ViewGroup)for(int i=0;i<((ViewGroup)root).getChildCount();i++){MusicPlayerView found=findPlayer(((ViewGroup)root).getChildAt(i));if(found!=null)return found;}
    return null;
  }
  void state(MediaSession s, int state) {
    s.setPlaybackState(new PlaybackState.Builder().setActions(PlaybackState.ACTION_PLAY|PlaybackState.ACTION_PAUSE|PlaybackState.ACTION_PLAY_PAUSE|PlaybackState.ACTION_SKIP_TO_NEXT|PlaybackState.ACTION_SKIP_TO_PREVIOUS).setState(state,30000,state==PlaybackState.STATE_PLAYING?1:0,SystemClock.elapsedRealtime()).build());
  }
  void song(MediaSession s, String name) { s.setMetadata(new MediaMetadata.Builder().putString(MediaMetadata.METADATA_KEY_TITLE,name).putString(MediaMetadata.METADATA_KEY_ARTIST,"Test artist").putBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART,android.graphics.Bitmap.createBitmap(48,48,android.graphics.Bitmap.Config.ARGB_8888)).putLong(MediaMetadata.METADATA_KEY_DURATION,180000).build()); }
  @Override public void onCreate(Bundle args) { super.onCreate(args); start(); }
  @Override public void onStart() {
    Bundle result=new Bundle();
    try {
      activity=startActivitySync(new Intent(getTargetContext(), HomeActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));settle();
      main(()->{view=findPlayer(activity.getWindow().getDecorView());check(view!=null,"Favorites player missing");check(view.getVisibility()==View.GONE,"Idle player must not occupy favorites");});
      main(()->{
        session=new MediaSession(getTargetContext(),"Arlecchino music fixture");
        session.setFlags(MediaSession.FLAG_HANDLES_MEDIA_BUTTONS|MediaSession.FLAG_HANDLES_TRANSPORT_CONTROLS);
        session.setCallback(new MediaSession.Callback(){
          @Override public void onPlay(){plays.incrementAndGet();state(session,PlaybackState.STATE_PLAYING);}
          @Override public void onPause(){pauses.incrementAndGet();state(session,PlaybackState.STATE_PAUSED);}
          @Override public void onSkipToNext(){next.incrementAndGet();song(session,"Next test song");}
          @Override public void onSkipToPrevious(){previous.incrementAndGet();song(session,"Previous test song");}
        },new Handler(Looper.getMainLooper()));
        song(session,"Test song");state(session,PlaybackState.STATE_PLAYING);session.setActive(true);view.refresh();
      });settle();
      main(()->{check(view.getVisibility()==View.VISIBLE,"Active player must appear");check(((ImageView)find(view,null,"Album artwork · open player")).getDrawable() instanceof android.graphics.drawable.BitmapDrawable,"Artwork missing");check(find(view,"Test song",null)!=null,"Track metadata missing");check(find(view,"Test artist",null)!=null,"Artist missing");check(find(view,null,"Pause").isEnabled(),"Pause disabled");find(view,null,"Pause").performClick();});settle();
      main(()->{check(pauses.get()==1,"Pause not delivered");check(find(view,null,"Play")!=null,"Paused UI stale");find(view,null,"Play").performClick();});settle();
      main(()->{check(plays.get()==1,"Play not delivered");find(view,null,"Next track").performClick();});settle();
      main(()->{check(next.get()==1&&find(view,"Next test song",null)!=null,"Next track stale");find(view,null,"Previous track").performClick();});settle();
      main(()->{check(previous.get()==1&&find(view,"Previous test song",null)!=null,"Previous track stale");view.stop();view.start();});settle();
      main(()->{check(find(view,"Previous test song",null)!=null,"Resume lost media session");
        state(session,PlaybackState.STATE_PAUSED);
        second=new MediaSession(getTargetContext(),"Second music fixture");song(second,"Second player");state(second,PlaybackState.STATE_PLAYING);second.setActive(true);view.refresh();
      });settle();
      main(()->{check(find(view,"Second player",null)!=null,"Active player switch failed");state(second,PlaybackState.STATE_STOPPED);state(session,PlaybackState.STATE_STOPPED);});settle();
      main(()->{check(view.getVisibility()==View.GONE,"Stopped sessions must collapse player");state(session,PlaybackState.STATE_PAUSED);});settle();
      main(()->{check(view.getVisibility()==View.VISIBLE,"Paused session should remain available");second.release();session.release();});settle();
      main(()->{view.refresh();});settle();
      main(()->{check(view.getVisibility()==View.GONE,"Released sessions must collapse favorites player");check(!find(view,null,"Play").isEnabled(),"Dead session remains controllable");});
      result.putString("stream","PASS: metadata, play/pause, next/previous, lifecycle resume, active-session switch, artwork, adaptive visibility and release\n");
      finish(Activity.RESULT_OK,result);
    } catch(Throwable failure) { result.putString("stream","FAIL: "+failure);finish(Activity.RESULT_CANCELED,result); }
    finally { main(()->{if(session!=null)session.release();if(second!=null)second.release();}); }
  }
}
