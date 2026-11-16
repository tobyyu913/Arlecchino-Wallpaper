package com.tobyyu.arlecchinowallpaper;

import android.app.*;
import android.content.*;
import android.graphics.*;
import android.graphics.drawable.*;
import android.media.MediaMetadata;
import android.media.session.*;
import android.media.session.MediaController;
import android.os.*;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.*;
import android.widget.*;
import java.util.*;

/** Local-only controller for Android's real media sessions. Never starts playback by itself. */
final class MusicPlayerView extends LinearLayout {
  private final Handler handler = new Handler(Looper.getMainLooper());
  private final MediaSessionManager manager;
  private final ComponentName listener;
  private final TextView title, artist;
  private final ImageView artwork;
  private final ImageButton previous, play, next;
  private android.animation.ValueAnimator visibilityAnimation;
  private boolean mediaVisible, artworkResolved;
  private String artworkIdentity = "";
  private final List<MediaController> controllers = new ArrayList<>();
  private MediaController current;
  private boolean started, listening, allowed;
  private int healthTicks;
  private final MediaSessionManager.OnActiveSessionsChangedListener sessions = this::bind;
  private final MediaController.Callback callback = new MediaController.Callback() {
    @Override public void onMetadataChanged(MediaMetadata metadata) { artworkResolved = false; render(); }
    @Override public void onPlaybackStateChanged(PlaybackState state) { render(); }
    @Override public void onSessionDestroyed() { refresh(); }
  };
  private final Runnable tick = new Runnable() {
    @Override public void run() {
      if (!started) return;
      boolean access = hasAccess();
      if (access != allowed || (access && !listening) || ++healthTicks % 5 == 0) refresh();

      handler.postDelayed(this, 1000);
    }
  };

  MusicPlayerView(Context context) {
    super(context);
    manager = context.getSystemService(MediaSessionManager.class);
    listener = new ComponentName(context, HomeNotifications.class);
    setOrientation(HORIZONTAL);
    setGravity(Gravity.CENTER_VERTICAL);
    setPadding(0, dp(9), 0, dp(9));
    setVisibility(GONE);
    artwork = new ImageView(context);
    artwork.setScaleType(ImageView.ScaleType.CENTER_CROP);
    artwork.setContentDescription("Album artwork · open player");
    GradientDrawable corners = new GradientDrawable();
    corners.setColor(0x16000000); corners.setCornerRadius(dp(6));
    artwork.setBackground(corners); artwork.setClipToOutline(true);
    artwork.setOnClickListener(v -> openPlayer());
    LayoutParams artParams = new LayoutParams(dp(88), dp(88));
    artParams.rightMargin = dp(16);
    addView(artwork, artParams);
    LinearLayout words = new LinearLayout(context);
    words.setOrientation(VERTICAL);
    title = label("", 16, 0xFFF8F4F1);
    artist = label("", 14, 0xFFEEE7E8);
    title.setShadowLayer(dp(1.5f), 0, dp(.5f), 0xBF000000);
    artist.setShadowLayer(dp(1.5f), 0, dp(.5f), 0xBF000000);
    title.setOnClickListener(v -> openPlayer());
    artist.setOnClickListener(v -> openPlayer());
    words.addView(title); words.addView(artist);
    LinearLayout controls = new LinearLayout(context);
    controls.setGravity(Gravity.CENTER_VERTICAL);
    previous = control("Previous track", 0);
    play = control("Play", 1);
    next = control("Next track", 2);
    controls.addView(previous);
    Space firstGap = new Space(context);
    controls.addView(firstGap, new LayoutParams(0, 1, 1));
    controls.addView(play);
    Space secondGap = new Space(context);
    controls.addView(secondGap, new LayoutParams(0, 1, 1));
    controls.addView(next);
    previous.setOnClickListener(v -> transport(0));
    play.setOnClickListener(v -> transport(1));
    next.setOnClickListener(v -> transport(2));
    words.addView(controls, new LayoutParams(-1, dp(48)));
    addView(words, new LayoutParams(0, -2, 1));
    render();
  }
  private int dp(float value) { return Math.round(value * getResources().getDisplayMetrics().density); }
  private TextView label(String value, float size, int color) {
    TextView view = new TextView(getContext());
    view.setText(value); view.setTextSize(size); view.setTextColor(color);
    view.setSingleLine(true); view.setEllipsize(TextUtils.TruncateAt.END);
    return view;
  }
  private ImageButton control(String name, int type) {
    ImageButton button = new ImageButton(getContext());
    button.setLayoutParams(new LayoutParams(dp(48), dp(48)));
    button.setPadding(dp(12), dp(12), dp(12), dp(12));
    button.setImageDrawable(new TransportIcon(type));
    if (type == 1) button.setPadding(dp(3), dp(3), dp(3), dp(3));
    button.setContentDescription(name);
    android.util.TypedValue ripple = new android.util.TypedValue();
    getContext().getTheme().resolveAttribute(android.R.attr.selectableItemBackgroundBorderless, ripple, true);
    if (ripple.resourceId != 0) button.setBackgroundResource(ripple.resourceId);
    else button.setBackgroundColor(Color.TRANSPARENT);
    return button;
  }
  private boolean hasAccess() {
    NotificationManager notifications = getContext().getSystemService(NotificationManager.class);
    return getContext().getSharedPreferences("home", 0).getBoolean("mediaPlayer", true) && notifications != null && notifications.isNotificationListenerAccessGranted(listener);
  }
  void start() {
    if (started) { refresh(); return; }
    started = true; refresh(); handler.postDelayed(tick, 1000);
  }
  void stop() {
    started = false; handler.removeCallbacks(tick);
    if (manager != null && listening) manager.removeOnActiveSessionsChangedListener(sessions);
    listening = false;
    for (MediaController controller : controllers) controller.unregisterCallback(callback);
    controllers.clear(); current = null;
  }
  void refresh() {
    allowed = hasAccess();
    if (!started) { render(); return; }
    try {
      if (!allowed || manager == null) {
        if (manager != null && listening) manager.removeOnActiveSessionsChangedListener(sessions);
        listening = false; bind(Collections.emptyList()); return;
      }
      if (!listening) {
        manager.addOnActiveSessionsChangedListener(sessions, listener, handler);
        listening = true;
      }
      bind(manager.getActiveSessions(listener));
    } catch (SecurityException ignored) {
      allowed = false;
      if (manager != null && listening) manager.removeOnActiveSessionsChangedListener(sessions);
      listening = false; bind(Collections.emptyList());
    }
  }
  private void bind(List<MediaController> values) {
    for (MediaController controller : controllers) controller.unregisterCallback(callback);
    controllers.clear();
    if (values != null) controllers.addAll(values);
    for (MediaController controller : controllers) controller.registerCallback(callback, handler);
    render();
  }
  private static boolean playing(MediaController controller) {
    PlaybackState state = controller == null ? null : controller.getPlaybackState();
    return state != null && (state.getState() == PlaybackState.STATE_PLAYING
        || state.getState() == PlaybackState.STATE_BUFFERING
        || state.getState() == PlaybackState.STATE_CONNECTING);
  }
  private static boolean eligible(MediaController controller) {
    PlaybackState state = controller.getPlaybackState();
    return state != null && (playing(controller) || state.getState() == PlaybackState.STATE_PAUSED)
        && !metadata(controller.getMetadata(), MediaMetadata.METADATA_KEY_DISPLAY_TITLE, MediaMetadata.METADATA_KEY_TITLE).isEmpty();
  }
  private MediaController choose() {
    if (!allowed) return null;
    for (MediaController controller : controllers) if (eligible(controller) && playing(controller)) return controller;
    if (current != null)
      for (MediaController controller : controllers)
        if (eligible(controller) && controller.getSessionToken().equals(current.getSessionToken())) return controller;
    for (MediaController controller : controllers) if (eligible(controller)) return controller;
    return null;
  }
  private String appName(MediaController controller) {
    try {
      android.content.pm.PackageManager pm = getContext().getPackageManager();
      return pm.getApplicationLabel(pm.getApplicationInfo(controller.getPackageName(), 0)).toString();
    } catch (android.content.pm.PackageManager.NameNotFoundException e) { return "Music player"; }
  }
  private static String metadata(MediaMetadata data, String primary, String fallback) {
    if (data == null) return "";
    String value = data.getString(primary);
    if (value == null || value.isEmpty()) value = data.getString(fallback);
    return value == null ? "" : value;
  }
  private void render() {
    current = choose();
    MediaMetadata data = current == null ? null : current.getMetadata();
    String track = metadata(data, MediaMetadata.METADATA_KEY_DISPLAY_TITLE, MediaMetadata.METADATA_KEY_TITLE);
    String by = metadata(data, MediaMetadata.METADATA_KEY_ARTIST, MediaMetadata.METADATA_KEY_ALBUM_ARTIST);
    title.setText(track);
    artist.setText(by.isEmpty() && current != null ? appName(current) : by);
    updateArtwork(data);
    showMedia(current != null);
    PlaybackState state = current == null ? null : current.getPlaybackState();
    long actions = state == null ? 0 : state.getActions();
    boolean active = playing(current);
    play.setImageDrawable(new TransportIcon(active ? 3 : 1));
    play.setContentDescription(active ? "Pause" : "Play");
    setEnabled(previous, (actions & PlaybackState.ACTION_SKIP_TO_PREVIOUS) != 0);
    setEnabled(next, (actions & PlaybackState.ACTION_SKIP_TO_NEXT) != 0);
    setEnabled(play, (actions & (PlaybackState.ACTION_PLAY_PAUSE | (active ? PlaybackState.ACTION_PAUSE : PlaybackState.ACTION_PLAY))) != 0);

  }
  private void setEnabled(View view, boolean enabled) { view.setEnabled(enabled); view.setAlpha(enabled ? 1 : .3f); }
  void refreshArtwork() { artworkResolved = false; if (started) render(); }
  private void updateArtwork(MediaMetadata data) {
    String identity = current == null ? "" : current.getPackageName() + "|" + metadata(data, MediaMetadata.METADATA_KEY_DISPLAY_TITLE, MediaMetadata.METADATA_KEY_TITLE) + "|" + metadata(data, MediaMetadata.METADATA_KEY_ARTIST, MediaMetadata.METADATA_KEY_ALBUM_ARTIST);
    if (artworkResolved && identity.equals(artworkIdentity)) return;
    artworkIdentity = identity; artworkResolved = true;
    Bitmap cover = null;
    if (data != null) {
      cover = data.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON);
      if (cover == null) cover = data.getBitmap(MediaMetadata.METADATA_KEY_ART);
      if (cover == null) cover = data.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART);
    }
    if (cover != null) { artwork.setImageBitmap(cover); return; }
    Drawable image = current == null ? null : HomeNotifications.mediaArtwork(current.getPackageName());
    if (image == null && current != null) {
      try { image = getContext().getPackageManager().getApplicationIcon(current.getPackageName()); }
      catch (android.content.pm.PackageManager.NameNotFoundException ignored) {}
    }
    artwork.setImageDrawable(image);
  }
  private void showMedia(boolean show) {
    if (show == mediaVisible) return;
    mediaVisible = show;
    if (visibilityAnimation != null) visibilityAnimation.cancel();
    ViewGroup.LayoutParams raw = getLayoutParams();
    if (!isLaidOut() || !(raw instanceof LinearLayout.LayoutParams)
        || !android.animation.ValueAnimator.areAnimatorsEnabled()) {
      setVisibility(show ? VISIBLE : GONE); setAlpha(1);
      if (raw instanceof LinearLayout.LayoutParams) {
        LinearLayout.LayoutParams p = (LinearLayout.LayoutParams) raw;
        p.height = -2; p.bottomMargin = 0; setLayoutParams(p);
      }
      return;
    }
    LinearLayout.LayoutParams p = (LinearLayout.LayoutParams) raw;
    int from = getVisibility() == GONE ? 0 : getHeight();
    int width = getWidth();
    if (width == 0 && getParent() instanceof View) width = ((View)getParent()).getWidth();
    measure(MeasureSpec.makeMeasureSpec(Math.max(width, dp(280)), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
    int target = show ? getMeasuredHeight() : 0;
    setVisibility(VISIBLE);
    visibilityAnimation = android.animation.ValueAnimator.ofInt(from, target);
    visibilityAnimation.setDuration(200);
    visibilityAnimation.setInterpolator(new android.view.animation.DecelerateInterpolator());
    visibilityAnimation.addUpdateListener(animation -> {
      int height = (Integer) animation.getAnimatedValue();
      p.height = height;
      float fraction = target > 0 ? height / (float) target : (from > 0 ? height / (float) from : 0);
      p.bottomMargin = 0;
      setAlpha(fraction); setLayoutParams(p);
    });
    visibilityAnimation.addListener(new android.animation.AnimatorListenerAdapter() {
      private boolean canceled;
      @Override public void onAnimationCancel(android.animation.Animator animation) { canceled = true; }
      @Override public void onAnimationEnd(android.animation.Animator animation) {
        if (canceled) return;
        setVisibility(mediaVisible ? VISIBLE : GONE); setAlpha(1);
        p.height = -2; p.bottomMargin = 0; setLayoutParams(p);
      }
    });
    visibilityAnimation.start();
  }
  private void transport(int action) {
    if (current == null || !allowed) return;
    try {
      MediaController.TransportControls controls = current.getTransportControls();
      if (action == 0) controls.skipToPrevious();
      else if (action == 2) controls.skipToNext();
      else if (playing(current)) controls.pause();
      else controls.play();
    } catch (RuntimeException ignored) { refresh(); }
  }
  private void openAccess() {
    try { getContext().startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)); }
    catch (ActivityNotFoundException e) { Toast.makeText(getContext(), "Enable Arlecchino Home in notification access settings", Toast.LENGTH_LONG).show(); }
  }
  private void openPlayer() {
    try {
      if (current != null && current.getSessionActivity() != null) { current.getSessionActivity().send(); return; }
      Intent launch = current == null ? null : getContext().getPackageManager().getLaunchIntentForPackage(current.getPackageName());
      if (launch == null) launch = Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_MUSIC);
      getContext().startActivity(launch);
    } catch (ActivityNotFoundException | PendingIntent.CanceledException ignored) {
      Toast.makeText(getContext(), "Open your music app to start listening", Toast.LENGTH_SHORT).show();
    }
  }
  private static final class TransportIcon extends Drawable {
    private final int type;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    TransportIcon(int type) { this.type = type; paint.setColor(0xFFF8F4F1); paint.setShadowLayer(1, 0, .5f, 0x99000000); }
    @Override public void draw(Canvas canvas) {
      Rect bounds = getBounds();
      canvas.save(); canvas.translate(bounds.left, bounds.top); canvas.scale(bounds.width() / 24f, bounds.height() / 24f);
      paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(1.5f); paint.setStrokeJoin(Paint.Join.ROUND); paint.setStrokeCap(Paint.Cap.ROUND);
      if (type == 1 || type == 3) canvas.drawCircle(12, 12, 11, paint);
      if (type == 3) { canvas.drawLine(9, 8, 9, 16, paint); canvas.drawLine(15, 8, 15, 16, paint); }
      else {
        if (type == 0) { canvas.translate(24, 0); canvas.scale(-1, 1); }
        Path triangle = new Path(); triangle.moveTo(type == 1 ? 10 : 7, type == 1 ? 8 : 7); triangle.lineTo(type == 1 ? 16 : 15, 12); triangle.lineTo(type == 1 ? 10 : 7, type == 1 ? 16 : 17); triangle.close(); canvas.drawPath(triangle, paint);
        if (type != 1) canvas.drawLine(17, 7, 17, 17, paint);
      }
      canvas.restore();
    }
    @Override public void setAlpha(int value) { paint.setAlpha(value); invalidateSelf(); }
    @Override public void setColorFilter(ColorFilter value) { paint.setColorFilter(value); invalidateSelf(); }
    @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
  }
}
