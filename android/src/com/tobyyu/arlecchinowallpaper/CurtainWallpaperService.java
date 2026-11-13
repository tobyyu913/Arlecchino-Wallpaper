package com.tobyyu.arlecchinowallpaper;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.RectF;
import android.graphics.ImageDecoder;
import android.graphics.drawable.Animatable2;
import android.graphics.drawable.AnimatedImageDrawable;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.service.wallpaper.WallpaperService;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.WindowManager;
import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class CurtainWallpaperService extends WallpaperService {
    @Override public Engine onCreateEngine() { return new CurtainEngine(); }

    final class CurtainEngine extends Engine implements Drawable.Callback {
        private final Handler handler = new Handler(Looper.getMainLooper());
        private final ExecutorService decoder = Executors.newSingleThreadExecutor();
        private final PowerManager power = (PowerManager) getSystemService(POWER_SERVICE);
        private final WakeCycle cycle = new WakeCycle(power.isInteractive());
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        private Bitmap restingFrame;
        private AnimatedImageDrawable animation;
        private boolean surfaceReady;
        private boolean destroyed;
        private int generation;
        private float horizontalOffset = 0.5f;
        private float verticalOffset = 0.5f;
        private final Runnable draw = this::drawFrame;
        private final BroadcastReceiver screenReceiver = new BroadcastReceiver() {
            @Override public void onReceive(Context context, Intent intent) {
                if (Intent.ACTION_SCREEN_OFF.equals(intent.getAction())) {
                    cycle.setInteractive(false);
                    stopAnimation();
                } else if (Intent.ACTION_SCREEN_ON.equals(intent.getAction())) {
                    cycle.setInteractive(true);
                    startIfNeeded();
                }
            }
        };

        @Override public void onCreate(SurfaceHolder holder) {
            super.onCreate(holder);
            setTouchEventsEnabled(false);
            setOffsetNotificationsEnabled(true);
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inScaled = false;
            restingFrame = BitmapFactory.decodeResource(getResources(), R.raw.open, options);
            IntentFilter filter = new IntentFilter(Intent.ACTION_SCREEN_ON);
            filter.addAction(Intent.ACTION_SCREEN_OFF);
            if (Build.VERSION.SDK_INT >= 33) registerReceiver(screenReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
            else registerReceiver(screenReceiver, filter);
        }
        @Override public void onSurfaceCreated(SurfaceHolder holder) {
            super.onSurfaceCreated(holder);
            surfaceReady = true;
            startIfNeeded();
            requestDraw();
        }
        @Override public void onSurfaceChanged(SurfaceHolder holder, int format, int width, int height) {
            super.onSurfaceChanged(holder, format, width, height);
            requestDraw();
        }
        @Override public void onOffsetsChanged(float xOffset, float yOffset, float xStep, float yStep, int xPixels, int yPixels) {
            horizontalOffset = Math.max(0f, Math.min(1f, xOffset));
            verticalOffset = Math.max(0f, Math.min(1f, yOffset));
            requestDraw();
        }
        @Override public void onSurfaceRedrawNeeded(SurfaceHolder holder) { requestDraw(); }
        @Override public void onVisibilityChanged(boolean visible) {
            cycle.setInteractive(power.isInteractive());
            cycle.setVisible(visible);
            if (visible) { startIfNeeded(); requestDraw(); }
            else { stopAnimation(); handler.removeCallbacks(draw); }
        }
        @Override public void onSurfaceDestroyed(SurfaceHolder holder) {
            surfaceReady = false;
            cycle.finish();
            stopAnimation();
            handler.removeCallbacks(draw);
            super.onSurfaceDestroyed(holder);
        }
        private void startIfNeeded() {
            if (!surfaceReady || destroyed || !cycle.takeOpening()) return;
            stopAnimation();
            final int token = generation;
            decoder.execute(() -> {
                AnimatedImageDrawable result = null;
                try {
                    Drawable decoded = ImageDecoder.decodeDrawable(ImageDecoder.createSource(getResources(), R.raw.opening));
                    if (decoded instanceof AnimatedImageDrawable) result = (AnimatedImageDrawable) decoded;
                } catch (IOException | RuntimeException error) { Log.e("ArlecchinoWallpaper", "Cannot decode opening", error); }
                final AnimatedImageDrawable loaded = result;
                handler.post(() -> {
                    if (destroyed || token != generation || !cycle.isOpening() || !cycle.canDraw()) {
                        if (loaded != null) loaded.stop();
                        return;
                    }
                    if (loaded == null) { cycle.finish(); requestDraw(); return; }
                    animation = loaded;
                    animation.setRepeatCount(0);
                    animation.setCallback(CurtainEngine.this);
                    animation.registerAnimationCallback(new Animatable2.AnimationCallback() {
                        @Override public void onAnimationEnd(Drawable drawable) {
                            if (animation != drawable) return;
                            cycle.finish();
                            stopAnimation();
                            requestDraw();
                        }
                    });
                    animation.start();
                    requestDraw();
                });
            });
            requestDraw();
        }
        private void stopAnimation() {
            generation++;
            if (animation != null) {
                AnimatedImageDrawable previous = animation;
                animation = null;
                previous.clearAnimationCallbacks();
                previous.stop();
                previous.setCallback(null);
                handler.removeCallbacksAndMessages(previous);
            }
        }
        private void requestDraw() {
            if (destroyed || !surfaceReady || !cycle.canDraw()) return;
            handler.removeCallbacks(draw);
            handler.post(draw);
        }
        private void drawFrame() {
            if (destroyed || !surfaceReady || !cycle.canDraw()) return;
            Canvas canvas = null;
            SurfaceHolder holder = getSurfaceHolder();
            try {
                canvas = holder.lockCanvas();
                if (canvas == null) return;
                canvas.drawColor(Color.BLACK);
                if (cycle.isOpening()) {
                    if (animation != null) {
                        RectF target = frame(canvas.getWidth(), canvas.getHeight(), animation.getIntrinsicWidth(), animation.getIntrinsicHeight());
                        animation.setBounds(Math.round(target.left), Math.round(target.top), Math.round(target.right), Math.round(target.bottom));
                        animation.draw(canvas);
                    }
                } else if (restingFrame != null) {
                    canvas.drawBitmap(restingFrame, null, frame(canvas.getWidth(), canvas.getHeight(), restingFrame.getWidth(), restingFrame.getHeight()), paint);
                }
            } catch (IllegalArgumentException | IllegalStateException error) {
                Log.w("ArlecchinoWallpaper", "Wallpaper surface changed", error);
            } finally {
                if (canvas != null) {
                    try { holder.unlockCanvasAndPost(canvas); }
                    catch (IllegalArgumentException | IllegalStateException ignored) { }
                }
            }
        }
        private RectF frame(int width, int height, int imageWidth, int imageHeight) {
            Point display = new Point();
            ((WindowManager) getSystemService(WINDOW_SERVICE)).getDefaultDisplay().getRealSize(display);
            float viewportWidth = Math.min(width, display.x);
            float viewportHeight = Math.min(height, display.y);
            // Some launchers request a wide scrolling surface. Keep the character
            // within the visible screen rather than its offscreen right margin.
            float left = (width - viewportWidth) * horizontalOffset;
            float top = (height - viewportHeight) * verticalOffset;
            float scale = Math.min(viewportWidth / imageWidth, viewportHeight / imageHeight);
            float w = imageWidth * scale, h = imageHeight * scale;
            return new RectF(left + viewportWidth - w, top + (viewportHeight - h) / 2f,
                left + viewportWidth, top + (viewportHeight + h) / 2f);
        }
        @Override public void invalidateDrawable(Drawable who) { requestDraw(); }
        @Override public void scheduleDrawable(Drawable who, Runnable what, long when) { handler.postAtTime(what, who, when); }
        @Override public void unscheduleDrawable(Drawable who, Runnable what) { handler.removeCallbacks(what, who); }
        @Override public void onDestroy() {
            destroyed = true;
            unregisterReceiver(screenReceiver);
            stopAnimation();
            handler.removeCallbacksAndMessages(null);
            decoder.shutdownNow();
            if (restingFrame != null) { restingFrame.recycle(); restingFrame = null; }
            super.onDestroy();
        }
    }
}
