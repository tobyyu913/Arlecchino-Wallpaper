package com.tobyyu.arlecchinowallpaper;

import android.app.*;
import android.content.*;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.net.*;
import android.net.wifi.*;
import android.os.*;
import android.provider.Settings;
import android.telephony.*;
import android.text.format.DateFormat;
import android.view.*;
import android.widget.*;
import java.util.Calendar;

/** A launcher-local Duo-inspired status column. No overlay, root or dangerous phone permission. */
final class DuoStatusView extends View {
  private final Activity activity;
  private final Handler handler = new Handler(Looper.getMainLooper());
  private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final RectF arc = new RectF();
  private final ConnectivityManager connectivity;
  private final WifiManager wifi;
  private final TelephonyManager baseTelephony;
  private TelephonyManager telephony;
  private volatile boolean started;
  private boolean networkRegistered, signalRegistered, wifiConnected, charging, airplane, saver;
  private int battery = -1, wifiLevel = -1, cellLevel = -1;
  private Object mobileCallback;
  private boolean mobileConnected;
  private int networkType, networkOverride;
  private String mobileLabel = "DATA";
  private Runnable settingsAction;
  private final Runnable refreshTask = this::refresh;
  private final BroadcastReceiver receiver = new BroadcastReceiver() {
    @Override public void onReceive(Context context, Intent intent) {
      if (Intent.ACTION_BATTERY_CHANGED.equals(intent.getAction())) {
        int level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1), scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 0);
        battery = scale > 0 && level >= 0 ? Math.max(0, Math.min(100, Math.round(level * 100f / scale))) : -1;
        charging = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0;
      }
      scheduleRefresh();
    }
  };
  private final ConnectivityManager.NetworkCallback network = new ConnectivityManager.NetworkCallback() {
    @Override public void onAvailable(Network value) { scheduleRefresh(); }
    @Override public void onLost(Network value) { scheduleRefresh(); }
    @Override public void onCapabilitiesChanged(Network value, NetworkCapabilities capabilities) { scheduleRefresh(); }
  };
  private final PhoneStateListener signal = new PhoneStateListener() {
    @Override public void onSignalStrengthsChanged(SignalStrength strength) { cellLevel = strength == null ? -1 : strength.getLevel(); scheduleRefresh(); }
  };
  DuoStatusView(Activity activity, Runnable settingsAction) {
    super(activity);
    this.activity = activity; this.settingsAction = settingsAction;
    connectivity = activity.getSystemService(ConnectivityManager.class);
    wifi = (WifiManager)activity.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
    baseTelephony = activity.getSystemService(TelephonyManager.class); telephony = baseTelephony;
    setClickable(false); setFocusable(true);
    setOnLongClickListener(v -> { if (this.settingsAction != null) this.settingsAction.run(); return true; });
    setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
    refresh();
  }
  private int dp(float value) { return Math.round(value * getResources().getDisplayMetrics().density); }
  private void scheduleRefresh() {
    handler.removeCallbacks(refreshTask);
    if (started) handler.postDelayed(refreshTask, 80);
  }
  void start() {
    if (started) { refresh(); return; }
    started = true;
    networkType = networkOverride = 0;
    try {
      int subscription = Build.VERSION.SDK_INT >= 30 ? SubscriptionManager.getActiveDataSubscriptionId() : SubscriptionManager.getDefaultDataSubscriptionId();
      telephony = baseTelephony != null && SubscriptionManager.isValidSubscriptionId(subscription) ? baseTelephony.createForSubscriptionId(subscription) : baseTelephony;
    } catch (RuntimeException ignored) { telephony = baseTelephony; }
    IntentFilter filter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
    filter.addAction(Intent.ACTION_TIME_TICK); filter.addAction(Intent.ACTION_TIME_CHANGED);
    filter.addAction(Intent.ACTION_TIMEZONE_CHANGED); filter.addAction(Intent.ACTION_AIRPLANE_MODE_CHANGED);
    filter.addAction(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED);
    filter.addAction("miui.intent.action.POWER_SAVE_MODE_CHANGED");
    filter.addAction(WifiManager.RSSI_CHANGED_ACTION); filter.addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION);
    if (Build.VERSION.SDK_INT >= 33) activity.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED);
    else activity.registerReceiver(receiver, filter);
    try { if (connectivity != null) { connectivity.registerDefaultNetworkCallback(network); networkRegistered = true; } } catch (RuntimeException ignored) {}
    try { if (telephony != null) { telephony.listen(signal, PhoneStateListener.LISTEN_SIGNAL_STRENGTHS); signalRegistered = true; } } catch (RuntimeException ignored) {}
    mobileCallback = MobileDisplay.start(activity, telephony, (type, override) -> { networkType = type; networkOverride = override; scheduleRefresh(); });
    refresh();
  }
  void stop() {
    if (!started) return;
    started = false; handler.removeCallbacks(refreshTask);
    activity.unregisterReceiver(receiver);
    if (networkRegistered) { try { connectivity.unregisterNetworkCallback(network); } catch (RuntimeException ignored) {} networkRegistered = false; }
    if (signalRegistered) { try { telephony.listen(signal, PhoneStateListener.LISTEN_NONE); } catch (RuntimeException ignored) {} signalRegistered = false; }
    MobileDisplay.stop(telephony, mobileCallback); mobileCallback = null;
  }
  private void refresh() {
    airplane = Settings.Global.getInt(activity.getContentResolver(), Settings.Global.AIRPLANE_MODE_ON, 0) != 0;
    PowerManager power = activity.getSystemService(PowerManager.class);
    saver = BatterySaverState.enabled(activity);
    wifiConnected = false; wifiLevel = -1; mobileConnected = false;
    try {
      NetworkCapabilities active = connectivity == null ? null : connectivity.getNetworkCapabilities(connectivity.getActiveNetwork());
      // VPN capabilities include underlying transports when Android exposes them.
      mobileConnected = active != null && active.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) && !active.hasTransport(NetworkCapabilities.TRANSPORT_WIFI);
    } catch (RuntimeException ignored) {}
    try {
      WifiInfo info = wifi == null ? null : wifi.getConnectionInfo();
      wifiConnected = info != null && info.getSupplicantState() == android.net.wifi.SupplicantState.COMPLETED;
      if (wifiConnected) wifiLevel = WifiManager.calculateSignalLevel(info.getRssi(), 5);
    } catch (RuntimeException ignored) {}
    try {
      if (telephony != null) {
        SignalStrength strength = telephony.getSignalStrength();
        cellLevel = strength == null ? -1 : strength.getLevel();
        if (airplane || telephony.getSimState() == TelephonyManager.SIM_STATE_ABSENT) cellLevel = 0;
      }
    } catch (RuntimeException ignored) { cellLevel = -1; }
    try { if (telephony != null) networkType = telephony.getDataNetworkType(); } catch (RuntimeException ignored) {
      try { NetworkInfo info = connectivity == null ? null : connectivity.getActiveNetworkInfo(); if (info != null && info.getType() == ConnectivityManager.TYPE_MOBILE) networkType = info.getSubtype(); } catch (RuntimeException unavailable) {}
    }
    mobileLabel = NetworkLabel.mobile(networkType, networkOverride);
    setContentDescription("Status. Battery " + (battery < 0 ? "unavailable" : battery + " percent") + (charging ? ", charging" : "")
        + ". " + (mobileConnected ? "Mobile data " + mobileLabel : "Wi-Fi " + (wifiConnected ? "connected, signal " + wifiLevel + " of 4" : "not connected"))
        + ". " + (airplane ? "Airplane mode" : "Cellular signal " + (cellLevel < 0 ? "unavailable" : cellLevel + " of 4")) + ".");
    invalidate();
  }
  @Override protected void onDraw(Canvas canvas) {
    super.onDraw(canvas);
    float density = getResources().getDisplayMetrics().density;
    canvas.save(); canvas.scale(density, density);
    float width = getWidth() / density, cx = width / 2, cy = 53, radius = 19;
    paint.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
    paint.setTextAlign(Paint.Align.CENTER); paint.setStyle(Paint.Style.FILL); paint.setColor(Color.WHITE);
    paint.setShadowLayer(1.5f, 0, .5f, 0xAA000000); paint.setTextSize(13);
    canvas.drawText(DateFormat.format(DateFormat.is24HourFormat(activity) ? "HH:mm" : "h:mm", Calendar.getInstance()).toString(), cx, 18, paint);
    paint.clearShadowLayer(); paint.setStrokeWidth(2.4f); paint.setStrokeCap(Paint.Cap.ROUND); paint.setStyle(Paint.Style.STROKE);
    arc.set(cx-radius, cy-radius, cx+radius, cy+radius);
    paint.setColor(0x55FFFFFF); canvas.drawArc(arc, 135, 120, false, paint); canvas.drawArc(arc, 285, 120, false, paint);
    paint.setColor(charging ? 0xFF95E8AB : battery >= 0 && battery <= 20 ? 0xFFFF858D : saver ? 0xFFFFD680 : Color.WHITE);
    float fill = Math.max(0, battery) / 100f;
    canvas.drawArc(arc, 135, 120*fill, false, paint); canvas.drawArc(arc, 405-120*fill, 120*fill, false, paint);
    paint.setStyle(Paint.Style.FILL); paint.setTextSize(9); paint.setColor(Color.WHITE);
    canvas.drawText(battery < 0 ? "—" : Integer.toString(battery), cx, cy-radius+3, paint);
    paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(1.8f);
    if (mobileConnected) {
      paint.setStyle(Paint.Style.FILL); paint.setTextSize(mobileLabel.length()>3 ? 8 : 12); paint.setColor(Color.WHITE); canvas.drawText(mobileLabel,cx,cy+5,paint);
    } else if (wifiConnected) {
    for (int i=0;i<3;i++) {
      float r=3+i*3;
      paint.setColor(wifiConnected && wifiLevel >= i+1 ? Color.WHITE : 0x55FFFFFF);
      arc.set(cx-r,cy-r+4,cx+r,cy+r+4); canvas.drawArc(arc,225,90,false,paint);
    }
    paint.setStyle(Paint.Style.FILL);paint.setColor(wifiConnected ? Color.WHITE : 0x55FFFFFF);canvas.drawCircle(cx,cy+5,1.3f,paint);
    } else {
      paint.setStyle(Paint.Style.FILL);paint.setTextSize(8);paint.setColor(0xAAFFFFFF);canvas.drawText(airplane ? "OFF" : "—",cx,cy+4,paint);
    }
    for(int i=0;i<4;i++){paint.setColor(cellLevel>i&&!airplane ? Color.WHITE : 0x55FFFFFF);canvas.drawCircle(cx-7.5f+i*5,cy+radius-1,1.4f,paint);}
    if(charging){paint.setTextSize(8);paint.setColor(0xFF95E8AB);canvas.drawText("ϟ",cx+radius+3,cy+3,paint);}
    canvas.restore();
  }
}
