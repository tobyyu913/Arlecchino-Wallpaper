package com.tobyyu.arlecchinowallpaper;

import android.graphics.drawable.Animatable2;
import android.graphics.drawable.AnimatedImageDrawable;
import android.graphics.drawable.Drawable;
import java.lang.ref.WeakReference;

/** Own one opening. Retired drawables keep their callback list safe for queued framework work. */
final class WallpaperPlayback {
    private final AnimatedImageDrawable image;
    private Runnable finished;
    private boolean released, completed;

    WallpaperPlayback(AnimatedImageDrawable image, Drawable.Callback drawing, Runnable finished) {
        this.image = image;
        this.finished = finished;
        image.setRepeatCount(0);
        image.setCallback(drawing);
        image.registerAnimationCallback(new Completion(this));
    }
    void start() { if (!released) image.start(); }
    void release() {
        if (released) return;
        released = true;
        finished = null;
        image.setCallback(null);
        // clearAnimationCallbacks (including removing the final callback) can null the
        // list after Android has posted a runnable which still iterates that list.
        // Leave the weak, inert callback installed until the drawable is collected.
        image.stop();
    }
    private void complete() {
        if (released || completed) return;
        completed = true;
        Runnable action = finished;
        finished = null;
        if (action != null) action.run();
    }
    private static final class Completion extends Animatable2.AnimationCallback {
        private final WeakReference<WallpaperPlayback> owner;
        Completion(WallpaperPlayback playback) { owner = new WeakReference<>(playback); }
        @Override public void onAnimationEnd(Drawable drawable) {
            WallpaperPlayback playback = owner.get();
            if (playback != null) playback.complete();
        }
    }
}
