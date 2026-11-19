package com.tobyyu.arlecchinowallpaper;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.database.ContentObserver;
import android.graphics.*;
import android.graphics.drawable.*;
import android.hardware.camera2.*;
import android.media.*;
import android.media.session.*;
import android.media.session.MediaController;
import android.net.Uri;
import android.os.*;
import android.provider.*;
import android.view.*;
import android.view.animation.PathInterpolator;
import android.widget.*;
import java.util.*;

/** Launcher-window controls, using public Android APIs and explicit permission screens. */
final class DuoControlCenter extends Dialog {
  static final int TORCH_PERMISSION = 431;
  private final Activity activity;
  private final View anchor;
  private final Handler handler = new Handler(Looper.getMainLooper());
  private final AudioManager audio;
  private final CameraManager cameras;
  private final NotificationManager notifications;
  private final MediaSessionManager media;
  private final ArrayList<MediaController> sessions = new ArrayList<>();
  private final ArrayList<View> tiles = new ArrayList<>();
  private FrameLayout root;
  private LinearLayout body;
  private TextView status, mediaTitle, mediaArtist;
  private ImageView artwork;
  private Bitmap shownArt;
  private Drawable idleArt;
  private IconButton wifi, cellular, airplaneButton, rotation, silent, focus, torch, play, previous, next;
  private Object backCallback;
  private BatterySaverControl batteryControl;
  private IconButton batterySaver, autoBrightness;
  private LevelSlider brightness, volume;
  private MediaController current;
  private boolean closing, cleaned, mediaListening, cameraListening, torchOn, pendingTorch;
  private boolean wifiConnected, mobileConnected, airplane, charging;
  private int battery = -1;
  private String mobileLabel = "DATA", cameraId;
  private float downY;
  private android.animation.ValueAnimator panelAnimation;
  private final Runnable refreshTask = new Runnable() {
    @Override public void run() { if (isShowing() && !closing) { refreshControls(); handler.postDelayed(this, 1000); } }
  };
  private final ContentObserver observer = new ContentObserver(handler) {
    @Override public void onChange(boolean self) { refreshControls(); }
  };
  private final MediaController.Callback mediaCallback = new MediaController.Callback() {
    @Override public void onMetadataChanged(MediaMetadata data) { renderMedia(); }
    @Override public void onPlaybackStateChanged(PlaybackState state) { chooseMedia(); }
    @Override public void onSessionDestroyed() { bindMedia(); }
  };
  private final MediaSessionManager.OnActiveSessionsChangedListener mediaChanged = ignored -> bindMedia();
  private final CameraManager.TorchCallback cameraCallback = new CameraManager.TorchCallback() {
    @Override public void onTorchModeChanged(String id, boolean enabled) { if (id.equals(cameraId)) { torchOn = enabled; refreshControls(); } }
    @Override public void onTorchModeUnavailable(String id) { if (id.equals(cameraId)) { torchOn = false; refreshControls(); } }
  };
  DuoControlCenter(Activity activity, View anchor) {
    super(activity, android.R.style.Theme_Material_NoActionBar);
    this.activity = activity; this.anchor = anchor;
    audio = activity.getSystemService(AudioManager.class);
    cameras = activity.getSystemService(CameraManager.class);
    notifications = activity.getSystemService(NotificationManager.class);
    media = activity.getSystemService(MediaSessionManager.class);
  }
  private int dp(float value) { return Math.round(value * activity.getResources().getDisplayMetrics().density); }
  private TextView text(String value, int size) {
    TextView v = new TextView(activity); v.setText(value); v.setTextSize(size); v.setTextColor(Color.WHITE); v.setFontFeatureSettings("tnum"); return v;
  }
  private GradientDrawable glass(int radius) {
    GradientDrawable d = new GradientDrawable(GradientDrawable.Orientation.TL_BR,new int[]{0x70696B75,0x60464852,0x8033353E});
    d.setCornerRadius(dp(radius)); d.setStroke(Math.max(1,dp(.5f)),0x22FFFFFF); return d;
  }
  @Override protected void onCreate(Bundle state) {
    super.onCreate(state);
    root = new FrameLayout(activity); root.setBackgroundColor(0x3A080A10); root.setOnClickListener(v -> dismiss());
    ScrollView scroll = new ScrollView(activity); scroll.setClipToPadding(false); scroll.setFillViewport(false); scroll.setVerticalScrollBarEnabled(false);
    body = new LinearLayout(activity); body.setOrientation(LinearLayout.VERTICAL); body.setPadding(dp(4),dp(4),dp(4),dp(12));
    body.setAlpha(0); body.setOnClickListener(v -> {}); scroll.addView(body);
    FrameLayout.LayoutParams outer = new FrameLayout.LayoutParams(Math.min(dp(344),activity.getResources().getDisplayMetrics().widthPixels-dp(48)),-2,Gravity.TOP|Gravity.CENTER_HORIZONTAL);
    outer.topMargin=dp(32); outer.bottomMargin=dp(24);root.addView(scroll,outer);
    scroll.setOnApplyWindowInsetsListener((v,insets)->{
      int top=0,bottom=dp(24);
      if(Build.VERSION.SDK_INT>=30){Insets safe=insets.getInsets(WindowInsets.Type.displayCutout()|WindowInsets.Type.navigationBars());top=safe.top;bottom=Math.max(bottom,safe.bottom);}
      else {top=insets.getSystemWindowInsetTop();bottom=Math.max(bottom,insets.getSystemWindowInsetBottom());}
      FrameLayout.LayoutParams p=(FrameLayout.LayoutParams)scroll.getLayoutParams();p.topMargin=Math.max(dp(30),top+dp(12));p.bottomMargin=bottom+dp(12);scroll.setLayoutParams(p);return insets;
    });
    LinearLayout heading = new LinearLayout(activity);heading.setGravity(Gravity.CENTER_VERTICAL);
    status=text("",13);status.setGravity(Gravity.RIGHT|Gravity.CENTER_VERTICAL);heading.addView(status,new LinearLayout.LayoutParams(0,dp(42),1));
    IconButton close=button("close","Close Control Center",()->dismiss());heading.addView(close,new LinearLayout.LayoutParams(dp(42),dp(42)));body.addView(heading);gap(body,12);
    LinearLayout first=row();LinearLayout connectivity=card();
    LinearLayout radios=row();airplaneButton=button("airplane","Airplane mode",()->open(Settings.ACTION_AIRPLANE_MODE_SETTINGS));wifi=button("wifi","Wi-Fi",()->open(Build.VERSION.SDK_INT>=29?Settings.Panel.ACTION_WIFI:Settings.ACTION_WIFI_SETTINGS));
    radios.addView(airplaneButton,new LinearLayout.LayoutParams(0,dp(64),1));radios.addView(wifi,new LinearLayout.LayoutParams(0,dp(64),1));connectivity.addView(radios);gap(connectivity,8);
    LinearLayout lower=row();cellular=button("cell","Mobile data",()->open(Build.VERSION.SDK_INT>=29?Settings.Panel.ACTION_INTERNET_CONNECTIVITY:Settings.ACTION_DATA_ROAMING_SETTINGS));
    IconButton bluetooth=button("bluetooth","Bluetooth",()->open(Settings.ACTION_BLUETOOTH_SETTINGS));lower.addView(cellular,new LinearLayout.LayoutParams(0,dp(64),1));lower.addView(bluetooth,new LinearLayout.LayoutParams(0,dp(64),1));connectivity.addView(lower);
    for(IconButton radio:new IconButton[]{airplaneButton,wifi,cellular,bluetooth}){radio.radio=true;radio.setBackground(null);}
    first.addView(connectivity,new LinearLayout.LayoutParams(0,dp(164),1));gap(first,12);
    LinearLayout music=card();artwork=new ImageView(activity);artwork.setScaleType(ImageView.ScaleType.CENTER_CROP);artwork.setClipToOutline(true);music.addView(artwork,new LinearLayout.LayoutParams(dp(36),dp(36)));
    mediaTitle=text("Not Playing",14);mediaTitle.setSingleLine(true);mediaTitle.setEllipsize(android.text.TextUtils.TruncateAt.END);mediaTitle.setTypeface(null,Typeface.BOLD);music.addView(mediaTitle);mediaArtist=text("",11);mediaArtist.setSingleLine(true);music.addView(mediaArtist);
    LinearLayout transports=row();previous=button("previous","Previous track",()->transport(0));previous.setBackground(null);transports.addView(previous,new LinearLayout.LayoutParams(0,dp(48),1));play=button("play","Play",()->transport(1));play.setBackground(null);transports.addView(play,new LinearLayout.LayoutParams(0,dp(48),1));next=button("next","Next track",()->transport(2));next.setBackground(null);transports.addView(next,new LinearLayout.LayoutParams(0,dp(48),1));music.addView(transports);
    music.setOnClickListener(v->{if(current!=null&&current.getSessionActivity()!=null){try{current.getSessionActivity().send();dismiss();}catch(PendingIntent.CanceledException ignored){}}else open(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS);});
    first.addView(music,new LinearLayout.LayoutParams(0,dp(164),1));body.addView(first);gap(body,12);
    LinearLayout middle=row();LinearLayout left=new LinearLayout(activity);left.setOrientation(LinearLayout.VERTICAL);
    LinearLayout toggles=row();rotation=button("rotation","Rotation lock",this::toggleRotation);silent=button("bell","Silent mode",this::toggleSilent);toggles.addView(rotation,new LinearLayout.LayoutParams(0,dp(72),1));gap(toggles,10);toggles.addView(silent,new LinearLayout.LayoutParams(0,dp(72),1));left.addView(toggles);gap(left,12);
    focus=button("moon","Focus · Do Not Disturb",this::toggleFocus);left.addView(focus,new LinearLayout.LayoutParams(-1,dp(72)));middle.addView(left,new LinearLayout.LayoutParams(0,dp(156),2));gap(middle,12);
    brightness=new LevelSlider("sun","Brightness",true);volume=new LevelSlider("volume","Volume",false);middle.addView(brightness,new LinearLayout.LayoutParams(0,dp(156),1));gap(middle,12);middle.addView(volume,new LinearLayout.LayoutParams(0,dp(156),1));body.addView(middle);gap(body,12);
    LinearLayout utilities=row();torch=button("torch","Flashlight",this::toggleTorch);addUtility(utilities,torch);addUtility(utilities,button("timer","Timer",()->open(AlarmClock.ACTION_SHOW_TIMERS)));addUtility(utilities,button("calculator","Calculator",()->launchCategory(Intent.CATEGORY_APP_CALCULATOR)));addUtility(utilities,button("camera","Camera",()->open(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA)));body.addView(utilities);gap(body,12);
    LinearLayout extras=row();
    batterySaver=button("battery","Battery Saver",()->batteryControl.set(!activity.getSystemService(PowerManager.class).isPowerSaveMode()));
    extras.addView(batterySaver,new LinearLayout.LayoutParams(0,dp(68),2));gap(extras,12);
    autoBrightness=button("auto","Automatic brightness",this::toggleAutoBrightness);extras.addView(autoBrightness,new LinearLayout.LayoutParams(0,dp(68),1));gap(extras,12);
    extras.addView(button("settings","System settings",()->open(Settings.ACTION_SETTINGS)),new LinearLayout.LayoutParams(0,dp(68),1));body.addView(extras);gap(body,18);
    batteryControl=new BatterySaverControl(activity,this::refreshControls);
    View handle=new View(activity);GradientDrawable line=new GradientDrawable();line.setColor(0xAAFFFFFF);line.setCornerRadius(dp(3));handle.setBackground(line);LinearLayout.LayoutParams hp=new LinearLayout.LayoutParams(dp(100),dp(5));hp.gravity=Gravity.CENTER_HORIZONTAL;body.addView(handle,hp);
    setContentView(root);
    Window w=getWindow();w.setBackgroundDrawableResource(android.R.color.transparent);w.setLayout(-1,-1);w.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN|WindowManager.LayoutParams.FLAG_DIM_BEHIND);
    WindowManager.LayoutParams attrs=w.getAttributes();attrs.dimAmount=0f;attrs.windowAnimations=0;
    if(Build.VERSION.SDK_INT>=31){w.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND);attrs.setBlurBehindRadius(dp(24));}
    w.setAttributes(attrs);w.setNavigationBarColor(Color.TRANSPARENT);
    if(Build.VERSION.SDK_INT>=30){w.setDecorFitsSystemWindows(false);WindowInsetsController c=w.getInsetsController();if(c!=null){c.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);c.hide(WindowInsets.Type.statusBars());}}
    else w.getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN|View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN|View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
    setOnDismissListener(ignored->cleanup());
  }
  private void addUtility(LinearLayout row, IconButton button){if(row.getChildCount()>0)gap(row,12);row.addView(button,new LinearLayout.LayoutParams(0,dp(70),1));}
  private LinearLayout row(){LinearLayout l=new LinearLayout(activity);l.setOrientation(LinearLayout.HORIZONTAL);return l;}
  private LinearLayout card(){LinearLayout l=new LinearLayout(activity);l.setOrientation(LinearLayout.VERTICAL);l.setPadding(dp(10),dp(10),dp(10),dp(10));l.setBackground(glass(30));tiles.add(l);return l;}
  private void gap(LinearLayout parent,int size){View v=new View(activity);parent.addView(v,parent.getOrientation()==LinearLayout.HORIZONTAL?new LinearLayout.LayoutParams(dp(size),1):new LinearLayout.LayoutParams(1,dp(size)));}
  private IconButton button(String icon,String description,Runnable action){IconButton b=new IconButton(icon,description);b.setOnClickListener(v->{b.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);action.run();});tiles.add(b);return b;}
  @Override public void show(){super.show();cleaned=false;closing=false;backCallback=BackNavigation.register(this,this::dismiss);root.setAlpha(0);root.animate().alpha(1).setDuration(animated()?250:0).start();getWindow().setLayout(-1,-1);root.requestApplyInsets();
    try{activity.getContentResolver().registerContentObserver(Settings.System.CONTENT_URI,true,observer);}catch(RuntimeException ignored){}
    if(cameras!=null){try{for(String id:cameras.getCameraIdList()){CameraCharacteristics c=cameras.getCameraCharacteristics(id);if(Boolean.TRUE.equals(c.get(CameraCharacteristics.FLASH_INFO_AVAILABLE))&&Integer.valueOf(CameraCharacteristics.LENS_FACING_BACK).equals(c.get(CameraCharacteristics.LENS_FACING))){cameraId=id;break;}}cameras.registerTorchCallback(cameraCallback,handler);cameraListening=true;}catch(CameraAccessException|RuntimeException ignored){}}
    bindMedia();refreshControls();handler.postDelayed(refreshTask,1000);
    if(Build.VERSION.SDK_INT>=31)anchor.getRootView().setRenderEffect(RenderEffect.createBlurEffect(dp(10),dp(10),Shader.TileMode.CLAMP));
    body.post(()->{if(isShowing()&&!closing)animatePanel(true);});
  }
  private boolean animated(){return android.animation.ValueAnimator.areAnimatorsEnabled();}
  private void animatePanel(boolean opening) {
    if(panelAnimation!=null)panelAnimation.cancel();
    body.animate().cancel();
    int[] source=new int[2],target=new int[2];anchor.getLocationOnScreen(source);body.getLocationOnScreen(target);
    int size=dp(56),width=body.getWidth(),height=body.getHeight();
    int x=Math.max(0,Math.min(width-size,source[0]+anchor.getWidth()/2-target[0]-size/2));
    int y=Math.max(0,Math.min(height-size,source[1]+anchor.getHeight()/2-target[1]-size/2));
    Rect seed=new Rect(x,y,x+size,y+size);
    body.setPivotX(x+size/2f);body.setPivotY(y+size/2f);
    panelAnimation=android.animation.ValueAnimator.ofFloat(opening?0:1,opening?1:0);
    panelAnimation.setDuration(animated()?(opening?360:240):0);
    panelAnimation.setInterpolator(opening?new PathInterpolator(.16f,.8f,.2f,1):new PathInterpolator(.4f,0,.75f,1));
    panelAnimation.addUpdateListener(animation->{float t=(float)animation.getAnimatedValue();
      body.setClipBounds(new Rect(Math.round(seed.left*(1-t)),Math.round(seed.top*(1-t)),Math.round(seed.right+(width-seed.right)*t),Math.round(seed.bottom+(height-seed.bottom)*t)));
      body.setAlpha(t);body.setScaleX(.97f+.03f*t);body.setScaleY(.97f+.03f*t);
    });
    panelAnimation.addListener(new android.animation.AnimatorListenerAdapter(){boolean canceled;
      @Override public void onAnimationCancel(android.animation.Animator animation){canceled=true;}
      @Override public void onAnimationEnd(android.animation.Animator animation){if(canceled)return;if(opening)body.setClipBounds(null);else closeImmediately();}
    });
    panelAnimation.start();
  }
  @Override public void dismiss(){if(closing||!isShowing())return;closing=true;handler.removeCallbacks(refreshTask);root.animate().alpha(0).setDuration(animated()?240:0).start();animatePanel(false);}
  void closeImmediately(){if(panelAnimation!=null){panelAnimation.cancel();panelAnimation=null;}if(body!=null)body.animate().cancel();if(root!=null)root.animate().cancel();cleanup();try{super.dismiss();}catch(IllegalArgumentException failure){if(getWindow()!=null&&getWindow().getDecorView().isAttachedToWindow())throw failure;}}
  @Override public void onBackPressed(){dismiss();}
  @Override public boolean dispatchTouchEvent(android.view.MotionEvent e){if(e.getActionMasked()==MotionEvent.ACTION_DOWN)downY=e.getY();if(e.getActionMasked()==MotionEvent.ACTION_UP&&downY-e.getY()>dp(70)&&e.getY()>body.getBottom()-dp(100)){dismiss();return true;}return super.dispatchTouchEvent(e);}
  void updateStatus(int battery,boolean charging,boolean wifi,boolean mobile,String network,boolean airplane){this.battery=battery;this.charging=charging;this.wifiConnected=wifi;this.mobileConnected=mobile;this.mobileLabel=network;this.airplane=airplane;if(status!=null)refreshControls();}
  private void refreshControls(){if(status==null)return;status.setText((airplane?"Airplane mode":mobileConnected?mobileLabel:wifiConnected?"Wi-Fi":"Offline")+"    "+(battery<0?"—":battery+"%")+(charging?" · Charging":""));
    wifi.active=wifiConnected;cellular.active=mobileConnected;cellular.setContentDescription("Mobile data · "+(mobileConnected?mobileLabel:"not active"));airplaneButton.active=airplane;
    rotation.active=Settings.System.getInt(activity.getContentResolver(),Settings.System.ACCELEROMETER_ROTATION,1)==0;
    silent.active=audio!=null&&audio.getRingerMode()!=AudioManager.RINGER_MODE_NORMAL;focus.active=notifications!=null&&notifications.getCurrentInterruptionFilter()!=NotificationManager.INTERRUPTION_FILTER_ALL;
    PowerManager power=activity.getSystemService(PowerManager.class);
    batterySaver.active=power!=null&&power.isPowerSaveMode();batterySaver.setEnabled(!batteryControl.busy());
    batterySaver.setContentDescription("Battery Saver "+(batterySaver.active?"on":"off")+(batteryControl.busy()?", changing":""));
    autoBrightness.active=Settings.System.getInt(activity.getContentResolver(),Settings.System.SCREEN_BRIGHTNESS_MODE,0)==1;
    torch.active=torchOn;for(View tile:tiles)tile.invalidate();
    if(!brightness.dragging)brightness.setLevel(Settings.System.getInt(activity.getContentResolver(),Settings.System.SCREEN_BRIGHTNESS,128)/255f);
    if(!volume.dragging&&audio!=null)volume.setLevel(audio.getStreamVolume(AudioManager.STREAM_MUSIC)/(float)Math.max(1,audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)));
    renderMedia();
  }
  private void open(String action){open(new Intent(action));}
  private void open(Intent intent){closeImmediately();try{activity.startActivity(intent);}catch(ActivityNotFoundException|SecurityException ignored){try{activity.startActivity(new Intent(Settings.ACTION_SETTINGS));}catch(RuntimeException ignoredAgain){}}}
  private void launchCategory(String category){Intent intent=new Intent(Intent.ACTION_MAIN).addCategory(category);open(intent);}
  private boolean settingsAccess(){if(Settings.System.canWrite(activity))return true;open(new Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS,Uri.parse("package:"+activity.getPackageName())));return false;}
  boolean isAwaitingPermission(){return batteryControl!=null&&(batteryControl.awaitingPermission()||batteryControl.busy());}
  private void toggleAutoBrightness(){if(!settingsAccess())return;try{Settings.System.putInt(activity.getContentResolver(),Settings.System.SCREEN_BRIGHTNESS_MODE,autoBrightness.active?0:1);refreshControls();}catch(SecurityException ignored){Toast.makeText(activity,"Android did not allow this change",Toast.LENGTH_SHORT).show();}}
  private void toggleFocus(){
    if(notifications==null)return;
    if(!notifications.isNotificationPolicyAccessGranted()){
      new AlertDialog.Builder(activity).setTitle("Focus control").setMessage("Allow Do Not Disturb access to switch Focus on and off here.").setPositiveButton("Allow access",(d,w)->open(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)).setNegativeButton("Cancel",null).show();return;
    }
    try{boolean enabled=activity.getSharedPreferences("home",0).getBoolean("controlFocus",false);notifications.setInterruptionFilter(enabled?NotificationManager.INTERRUPTION_FILTER_ALL:NotificationManager.INTERRUPTION_FILTER_PRIORITY);activity.getSharedPreferences("home",0).edit().putBoolean("controlFocus",!enabled).apply();refreshControls();}catch(SecurityException ignored){Toast.makeText(activity,"Focus access was removed",Toast.LENGTH_SHORT).show();}
  }
  private void toggleRotation(){if(!settingsAccess())return;try{Settings.System.putInt(activity.getContentResolver(),Settings.System.ACCELEROMETER_ROTATION,rotation.active?1:0);refreshControls();}catch(SecurityException ignored){open(Settings.ACTION_DISPLAY_SETTINGS);}}
  private void toggleSilent(){if(audio==null)return;try{audio.setRingerMode(audio.getRingerMode()==AudioManager.RINGER_MODE_NORMAL?AudioManager.RINGER_MODE_VIBRATE:AudioManager.RINGER_MODE_NORMAL);refreshControls();}catch(SecurityException ignored){open(Settings.ACTION_SOUND_SETTINGS);}}
  private void toggleTorch(){if(cameraId==null){Toast.makeText(activity,"No flashlight is available",Toast.LENGTH_SHORT).show();return;}if(activity.checkSelfPermission(Manifest.permission.CAMERA)!=PackageManager.PERMISSION_GRANTED){pendingTorch=true;activity.requestPermissions(new String[]{Manifest.permission.CAMERA},TORCH_PERMISSION);return;}try{cameras.setTorchMode(cameraId,!torchOn);}catch(CameraAccessException|RuntimeException ignored){Toast.makeText(activity,"Flashlight is in use or unavailable",Toast.LENGTH_SHORT).show();}}
  void permissionResult(int request,int[] grants){if(request==TORCH_PERMISSION&&pendingTorch){pendingTorch=false;if(isShowing()&&grants.length>0&&grants[0]==PackageManager.PERMISSION_GRANTED)toggleTorch();}}
  private final ComponentName listener(){return new ComponentName(activity,HomeNotifications.class);}
  private void bindMedia(){try{if(media==null)return;if(!mediaListening){media.addOnActiveSessionsChangedListener(mediaChanged,listener(),handler);mediaListening=true;}for(MediaController c:sessions)c.unregisterCallback(mediaCallback);sessions.clear();sessions.addAll(media.getActiveSessions(listener()));for(MediaController c:sessions)c.registerCallback(mediaCallback,handler);chooseMedia();}catch(SecurityException ignored){current=null;renderMedia();}}
  private boolean playing(MediaController c){PlaybackState s=c==null?null:c.getPlaybackState();return s!=null&&(s.getState()==PlaybackState.STATE_PLAYING||s.getState()==PlaybackState.STATE_BUFFERING||s.getState()==PlaybackState.STATE_CONNECTING);}
  private void chooseMedia(){MediaController selected=null;for(MediaController c:sessions){PlaybackState s=c.getPlaybackState();if(playing(c)){selected=c;break;}if(s!=null&&s.getState()==PlaybackState.STATE_PAUSED&&selected==null)selected=c;}current=selected;renderMedia();}
  private void renderMedia(){if(mediaTitle==null)return;
    PlaybackState state=current==null?null:current.getPlaybackState();long actions=state==null?0:state.getActions();
    previous.setEnabled((actions&PlaybackState.ACTION_SKIP_TO_PREVIOUS)!=0);previous.setAlpha(previous.isEnabled()?1:.35f);
    next.setEnabled((actions&PlaybackState.ACTION_SKIP_TO_NEXT)!=0);next.setAlpha(next.isEnabled()?1:.35f);
    play.setEnabled((actions&(playing(current)?PlaybackState.ACTION_PAUSE|PlaybackState.ACTION_PLAY_PAUSE:PlaybackState.ACTION_PLAY|PlaybackState.ACTION_PLAY_PAUSE))!=0);play.setAlpha(play.isEnabled()?1:.35f);
    MediaMetadata m=current==null?null:current.getMetadata();CharSequence title=m==null?null:m.getText(MediaMetadata.METADATA_KEY_DISPLAY_TITLE);if(title==null&&m!=null)title=m.getText(MediaMetadata.METADATA_KEY_TITLE);mediaTitle.setText(title==null?"Not Playing":title);mediaArtist.setText(m==null?"":m.getText(MediaMetadata.METADATA_KEY_ARTIST));Bitmap image=m==null?null:m.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART);if(image==null&&m!=null)image=m.getBitmap(MediaMetadata.METADATA_KEY_ART);if(image!=shownArt||artwork.getDrawable()==null){shownArt=image;if(image!=null)artwork.setImageBitmap(image);else{if(idleArt==null)idleArt=new Drawable(){@Override public void draw(Canvas c){Rect b=getBounds();c.save();c.translate(b.exactCenterX(),b.exactCenterY());c.scale(b.width()/34f,b.height()/34f);drawIcon(c,"music",0x88FFFFFF);c.restore();}@Override public void setAlpha(int value){}@Override public void setColorFilter(ColorFilter filter){}@Override public int getOpacity(){return PixelFormat.TRANSLUCENT;}};artwork.setImageDrawable(idleArt);}}play.icon=playing(current)?"pause":"play";play.setContentDescription(playing(current)?"Pause":"Play");play.invalidate();}
  private void transport(int action){if(current==null){open(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS);return;}PlaybackState s=current.getPlaybackState();long supported=s==null?0:s.getActions();MediaController.TransportControls c=current.getTransportControls();try{if(action==0&&(supported&PlaybackState.ACTION_SKIP_TO_PREVIOUS)!=0)c.skipToPrevious();else if(action==2&&(supported&PlaybackState.ACTION_SKIP_TO_NEXT)!=0)c.skipToNext();else if(action==1){if(playing(current)&&(supported&(PlaybackState.ACTION_PAUSE|PlaybackState.ACTION_PLAY_PAUSE))!=0)c.pause();else if(!playing(current)&&(supported&(PlaybackState.ACTION_PLAY|PlaybackState.ACTION_PLAY_PAUSE))!=0)c.play();}}catch(RuntimeException ignored){}}
  private void cleanup(){if(cleaned)return;cleaned=true;if(batteryControl!=null)batteryControl.close();BackNavigation.unregister(this,backCallback);backCallback=null;pendingTorch=false;handler.removeCallbacksAndMessages(null);try{activity.getContentResolver().unregisterContentObserver(observer);}catch(RuntimeException ignored){}if(mediaListening){media.removeOnActiveSessionsChangedListener(mediaChanged);mediaListening=false;}for(MediaController c:sessions)c.unregisterCallback(mediaCallback);sessions.clear();current=null;if(cameraListening){cameras.unregisterTorchCallback(cameraCallback);cameraListening=false;}if(Build.VERSION.SDK_INT>=31)anchor.getRootView().setRenderEffect(null);}
  final class IconButton extends View {
    String icon;boolean active,radio;
    IconButton(String icon,String description){super(activity);this.icon=icon;setContentDescription(description);setFocusable(true);setClickable(true);setBackground(glass(40));setMinimumHeight(dp(48));}
    @Override protected void onDraw(Canvas c) {
      super.onDraw(c);
      float d=activity.getResources().getDisplayMetrics().density;
      c.save();c.translate(getWidth()/2f,getHeight()/2f);c.scale(d,d);
      boolean wide=icon.equals("moon")||icon.equals("battery");
      if(radio||active){
        Paint fill=new Paint(3);fill.setColor(!active?0x22FFFFFF:icon.equals("wifi")?0xFF198CFF:icon.equals("cell")?0xFF36B871:icon.equals("airplane")?0xFFF5A231:icon.equals("battery")?0xFFEAC35A:0xFFF0F2F6);
        c.drawRoundRect(-getWidth()/d/2,-getHeight()/d/2,getWidth()/d/2,getHeight()/d/2,36,36,fill);
      }
      if(wide)c.translate(-getWidth()/d/2+27,0);
      c.save();c.scale(.85f,.85f);drawIcon(c,icon,active&&!icon.equals("wifi")&&!icon.equals("cell")&&!icon.equals("airplane")?0xFF30343D:Color.WHITE);c.restore();
      if(wide){Paint label=new Paint(3);label.setColor(active?0xFF30343D:Color.WHITE);label.setTextSize(icon.equals("battery")?12:15);c.drawText(icon.equals("battery")?(batteryControl.busy()?"Changing…":"Low Power"):"Focus",23,5,label);}
      c.restore();
    }
    @Override public void setPressed(boolean pressed){super.setPressed(pressed);animate().scaleX(pressed?.90f:1).scaleY(pressed?.90f:1).setDuration(100).start();}
  }
  final class LevelSlider extends FrameLayout {
    final String icon;final boolean isBrightness;float level;boolean dragging;
    private final View indicator;
    LevelSlider(String icon,String description,boolean brightness) {
      super(activity);this.icon=icon;this.isBrightness=brightness;
      setWillNotDraw(false);setContentDescription(description);setFocusable(true);setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
      indicator=new View(activity) {
        @Override protected void onDraw(Canvas c) {
          float d=activity.getResources().getDisplayMetrics().density;
          c.save();c.translate(getWidth()/2f,getHeight()/2f);c.scale(d,d);
          drawIcon(c,LevelSlider.this.icon,level>.25f?0xFF4C5059:Color.WHITE);c.restore();
        }
      };
      indicator.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
      addView(indicator,new FrameLayout.LayoutParams(-1,dp(56),Gravity.BOTTOM));
    }
    void setLevel(float value){level=Math.max(0,Math.min(1,value));setContentDescription((isBrightness?"Brightness":"Volume")+" "+Math.round(level*100)+" percent");invalidate();indicator.invalidate();}
    private void change(float value){setLevel(value);try{if(isBrightness){if(!settingsAccess()){dragging=false;return;}Settings.System.putInt(activity.getContentResolver(),Settings.System.SCREEN_BRIGHTNESS_MODE,Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL);Settings.System.putInt(activity.getContentResolver(),Settings.System.SCREEN_BRIGHTNESS,Math.max(4,Math.round(level*255)));}else if(audio!=null)audio.setStreamVolume(AudioManager.STREAM_MUSIC,Math.round(level*audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)),0);}catch(SecurityException ignored){refreshControls();}}
    @Override public boolean onTouchEvent(MotionEvent e){if(e.getActionMasked()==MotionEvent.ACTION_DOWN){if(isBrightness&&!Settings.System.canWrite(activity)){settingsAccess();return true;}dragging=true;getParent().requestDisallowInterceptTouchEvent(true);}if(e.getActionMasked()==MotionEvent.ACTION_DOWN||e.getActionMasked()==MotionEvent.ACTION_MOVE)change(1-e.getY()/getHeight());if(e.getActionMasked()==MotionEvent.ACTION_UP||e.getActionMasked()==MotionEvent.ACTION_CANCEL){dragging=false;getParent().requestDisallowInterceptTouchEvent(false);if(e.getActionMasked()==MotionEvent.ACTION_UP)performClick();}return true;}
    @Override public boolean performClick(){super.performClick();return true;}
    @Override protected void onDraw(Canvas c) {
      super.onDraw(c);
      Paint p=new Paint(3);RectF bounds=new RectF(0,0,getWidth(),getHeight());
      Path clip=new Path();clip.addRoundRect(bounds,dp(38),dp(38),Path.Direction.CW);
      int saved=c.save();c.clipPath(clip);getGlass().setBounds(0,0,getWidth(),getHeight());getGlass().draw(c);
      p.setColor(0xF8FFFFFF);c.drawRect(0,getHeight()*(1-level),getWidth(),getHeight(),p);c.restoreToCount(saved);
    }
    private Drawable surface;private Drawable getGlass(){if(surface==null)surface=glass(38);return surface;}
    @Override public void onInitializeAccessibilityNodeInfo(android.view.accessibility.AccessibilityNodeInfo info){super.onInitializeAccessibilityNodeInfo(info);info.setClassName(SeekBar.class.getName());info.setRangeInfo(android.view.accessibility.AccessibilityNodeInfo.RangeInfo.obtain(1,0,100,level*100));info.addAction(android.view.accessibility.AccessibilityNodeInfo.AccessibilityAction.ACTION_SET_PROGRESS);info.addAction(4096);info.addAction(8192);}
    @Override public boolean performAccessibilityAction(int action,Bundle args){if(action==android.view.accessibility.AccessibilityNodeInfo.AccessibilityAction.ACTION_SET_PROGRESS.getId()&&args!=null){change(args.getFloat(android.view.accessibility.AccessibilityNodeInfo.ACTION_ARGUMENT_PROGRESS_VALUE)/100);return true;}if(action==4096||action==8192){change(level+(action==4096?.05f:-.05f));return true;}return super.performAccessibilityAction(action,args);}
  }
  private void drawIcon(Canvas c,String name,int color){Paint p=new Paint(3);p.setColor(color);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(1.9f);p.setStrokeCap(Paint.Cap.ROUND);p.setStrokeJoin(Paint.Join.ROUND);Path path=new Path();
    switch(name){
      case "music":path.moveTo(-5,9);path.lineTo(-5,-9);path.lineTo(10,-12);path.lineTo(10,6);c.drawPath(path,p);p.setStyle(Paint.Style.FILL);c.drawOval(-13,5,-3,12,p);c.drawOval(2,2,12,9,p);break;
      case "wifi":for(int i=0;i<3;i++){float r=4+i*5;c.drawArc(-r,-r+6,r,r+6,225,90,false,p);}p.setStyle(Paint.Style.FILL);c.drawCircle(0,8,1.6f,p);break;
      case "cell":for(int i=0;i<4;i++)c.drawLine(-10+i*7,11,-10+i*7,6-i*6,p);break;
      case "airplane":path.moveTo(-13,1);path.lineTo(-3,3);path.lineTo(-9,12);path.lineTo(-5,12);path.lineTo(3,4);path.lineTo(12,3);path.quadTo(17,0,12,-2);path.lineTo(3,-3);path.lineTo(-5,-12);path.lineTo(-9,-12);path.lineTo(-3,-2);path.lineTo(-13,-5);path.close();p.setStyle(Paint.Style.FILL);c.drawPath(path,p);break;
      case "bluetooth":path.moveTo(0,-13);path.lineTo(9,-6);path.lineTo(-8,7);path.moveTo(0,-13);path.lineTo(0,13);path.lineTo(9,6);path.lineTo(-8,-7);c.drawPath(path,p);break;
      case "rotation":c.drawArc(-13,-13,13,13,195,280,false,p);path.moveTo(-14,-9);path.lineTo(-14,-1);path.lineTo(-6,-1);c.drawPath(path,p);c.drawRoundRect(-5,-1,5,8,2,2,p);c.drawArc(-3,-7,3,3,180,180,false,p);break;
      case "bell":path.moveTo(-10,6);path.quadTo(-7,2,-7,-5);path.quadTo(0,-15,7,-5);path.quadTo(7,2,10,6);path.close();c.drawPath(path,p);c.drawArc(-3,7,3,12,0,180,false,p);break;
      case "moon":path.moveTo(5,-12);path.cubicTo(-14,-10,-14,14,5,12);path.cubicTo(-3,8,-4,-6,5,-12);p.setStyle(Paint.Style.FILL);c.drawPath(path,p);break;
      case "auto":p.setTextSize(16);p.setTextAlign(Paint.Align.CENTER);p.setStyle(Paint.Style.FILL);p.setTypeface(Typeface.create("sans-serif-medium",0));c.drawText("A",0,6,p);p.setStyle(Paint.Style.STROKE);c.drawArc(-14,-14,14,14,205,130,false,p);c.drawArc(-14,-14,14,14,25,130,false,p);break;
      case "sun":c.drawCircle(0,0,6,p);for(int i=0;i<8;i++){double a=i*Math.PI/4;c.drawLine((float)Math.cos(a)*10,(float)Math.sin(a)*10,(float)Math.cos(a)*14,(float)Math.sin(a)*14,p);}break;
      case "volume":path.moveTo(-12,-4);path.lineTo(-6,-4);path.lineTo(1,-10);path.lineTo(1,10);path.lineTo(-6,4);path.lineTo(-12,4);path.close();p.setStyle(Paint.Style.FILL);c.drawPath(path,p);p.setStyle(Paint.Style.STROKE);c.drawArc(0,-9,12,9,-65,130,false,p);break;
      case "torch":c.drawRoundRect(-4,-2,4,13,2,2,p);path.moveTo(-8,-12);path.lineTo(8,-12);path.lineTo(8,-7);path.lineTo(4,-2);path.lineTo(-4,-2);path.lineTo(-8,-7);path.close();c.drawPath(path,p);c.drawLine(-7,-8,7,-8,p);break;
      case "timer":c.drawCircle(0,2,11,p);c.drawLine(-3,-13,3,-13,p);c.drawLine(0,-13,0,-10,p);c.drawLine(0,-5,0,2,p);c.drawLine(0,2,5,5,p);break;
      case "calculator":c.drawRoundRect(-9,-13,9,13,3,3,p);c.drawRect(-5,-9,5,-3,p);for(int x=-5;x<7;x+=5)for(int y=2;y<11;y+=5)c.drawPoint(x,y,p);break;
      case "camera":c.drawRoundRect(-14,-8,14,11,4,4,p);c.drawCircle(0,1,6,p);path.moveTo(-7,-8);path.lineTo(-4,-12);path.lineTo(4,-12);path.lineTo(7,-8);c.drawPath(path,p);break;
      case "battery":c.drawRoundRect(-14,-7,11,7,3,3,p);c.drawLine(14,-3,14,3,p);p.setStyle(Paint.Style.FILL);c.drawRoundRect(-10,-4,3,4,1,1,p);break;
      case "cast":c.drawRoundRect(-14,-10,14,10,2,2,p);for(int i=0;i<3;i++){float r=4+i*5;c.drawArc(-14-r,10-r,-14+r,10+r,270,90,false,p);}break;
      case "settings":c.drawCircle(0,0,5,p);c.drawCircle(0,0,11,p);for(int i=0;i<8;i++){double a=i*Math.PI/4;c.drawLine((float)Math.cos(a)*11,(float)Math.sin(a)*11,(float)Math.cos(a)*15,(float)Math.sin(a)*15,p);}break;
      case "previous":case "next":case "play":case "pause":float direction=name.equals("previous")?-1:1;c.save();c.scale(direction*.8f,.8f);p.setStyle(Paint.Style.FILL);if(name.equals("pause")){c.drawRoundRect(-8,-10,-3,10,1,1,p);c.drawRoundRect(3,-10,8,10,1,1,p);}else{path.moveTo(-7,-11);path.lineTo(10,0);path.lineTo(-7,11);path.close();c.drawPath(path,p);if(!name.equals("play"))c.drawRect(11,-11,14,11,p);}c.restore();break;
      case "close":c.drawLine(-7,-7,7,7,p);c.drawLine(7,-7,-7,7,p);break;
      case "power":c.drawLine(0,-11,0,1,p);c.drawArc(-11,-10,11,12,-55,290,false,p);break;
    }
  }
}
