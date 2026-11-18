package com.tobyyu.arlecchinowallpaper;

import android.app.Activity;
import android.os.Build;
import android.telephony.*;

/** API 31 callback kept separate from Android 9–11 class verification. */
final class MobileDisplay {
  interface Changed { void update(int type, int override); }
  static Object start(Activity activity, TelephonyManager manager, Changed changed) {
    if (Build.VERSION.SDK_INT < 31 || manager == null) return null;
    try { return Api31.start(activity, manager, changed); } catch (RuntimeException ignored) { return null; }
  }
  static void stop(TelephonyManager manager, Object callback) {
    if (Build.VERSION.SDK_INT >= 31 && callback != null) {
      try { Api31.stop(manager, callback); } catch (RuntimeException ignored) {}
    }
  }
  private static final class Api31 {
    static final class Listener extends TelephonyCallback implements TelephonyCallback.DisplayInfoListener {
      private final Changed changed;
      Listener(Changed changed) { this.changed = changed; }
      @Override public void onDisplayInfoChanged(TelephonyDisplayInfo info) { changed.update(info.getNetworkType(),info.getOverrideNetworkType()); }
    }
    static Object start(Activity activity, TelephonyManager manager, Changed changed) {
      Listener listener = new Listener(changed);
      manager.registerTelephonyCallback(activity.getMainExecutor(), listener);
      return listener;
    }
    static void stop(TelephonyManager manager, Object callback) { manager.unregisterTelephonyCallback((Listener)callback); }
  }
}
