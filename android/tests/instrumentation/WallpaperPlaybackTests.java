package com.tobyyu.arlecchinowallpaper;

import android.app.*;
import android.content.*;
import android.graphics.*;
import android.graphics.drawable.*;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.util.concurrent.atomic.AtomicInteger;

/** Native AnimatedImageDrawable lifecycle checks on disposable emulators only. */
public final class WallpaperPlaybackTests extends Instrumentation {
    WallpaperPlayback natural;
    Activity activity;
    private AnimatedImageDrawable decode() {
        try { return (AnimatedImageDrawable) ImageDecoder.decodeDrawable(
            ImageDecoder.createSource(getTargetContext().getResources(),R.raw.opening),
            (decoder,info,source)->decoder.setTargetSize(160,348)); }
        catch(Exception failure){throw new RuntimeException(failure);}
    }
    private void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    @Override public void onCreate(Bundle args){super.onCreate(args);start();}
    @Override public void onStart(){Bundle result=new Bundle();try{
        android.content.pm.ServiceInfo service=getTargetContext().getPackageManager().getServiceInfo(new ComponentName(getTargetContext(),CurtainWallpaperService.class),0);
        check(service.processName.endsWith(":wallpaper"),"Wallpaper is not isolated from launcher process");
        AtomicInteger canceled=new AtomicInteger();
        runOnMainSync(()->{
            Bitmap bitmap=Bitmap.createBitmap(160,348,Bitmap.Config.ARGB_8888);Canvas canvas=new Canvas(bitmap);
            for(int i=0;i<24;i++){
                AnimatedImageDrawable image=decode();
                WallpaperPlayback playback=new WallpaperPlayback(image,null,canceled::incrementAndGet);
                playback.start();image.setBounds(0,0,160,348);image.draw(canvas);
                // Android has queued an end runnable before the owner retires it.
                image.stop();playback.release();playback.release();
                if(image.getCallback()!=null)throw new AssertionError("Retired drawable retained its drawing owner");
            }
            bitmap.recycle();
        });
        waitForIdleSync();SystemClock.sleep(400);check(canceled.get()==0,"Retired opening fired its completion");
        AtomicInteger completed=new AtomicInteger();
        activity=startActivitySync(new Intent(getTargetContext(),HomeActivity.class).setAction(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        runOnMainSync(()->{
            ImageView view=new ImageView(activity);view.setScaleType(ImageView.ScaleType.FIT_CENTER);
            AnimatedImageDrawable image=decode();view.setImageDrawable(image);
            activity.addContentView(view,new android.view.ViewGroup.LayoutParams(-1,-1));
            natural=new WallpaperPlayback(image,view,()->{completed.incrementAndGet();natural.release();view.setImageDrawable(null);});
            natural.start();
        });
        long end=SystemClock.uptimeMillis()+7000;
        while(completed.get()==0&&SystemClock.uptimeMillis()<end)SystemClock.sleep(25);
        check(completed.get()==1,"Opening did not naturally finish once");
        waitForIdleSync();SystemClock.sleep(300);check(completed.get()==1,"Completion fired twice");
        result.putString("stream","PASS: 24 queued-end cancellation/release races, natural one-shot completion, detached drawing callbacks and wallpaper process isolation\n");
    }catch(Throwable failure){result.putString("stream","FAIL: "+failure+"\n");}
    finally{runOnMainSync(()->{if(natural!=null)natural.release();});finish(Activity.RESULT_OK,result);}}
}
