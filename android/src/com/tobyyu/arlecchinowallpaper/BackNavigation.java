package com.tobyyu.arlecchinowallpaper;

import android.app.Activity;
import android.os.Build;

/** Isolate API 33 types so Android 9–12 keep their legacy Back callback. */
final class BackNavigation {
  static Object register(Activity activity, Runnable back) {
    return Build.VERSION.SDK_INT >= 33 ? Api33.register(activity, back) : null;
  }
  static void unregister(Activity activity, Object callback) {
    if (Build.VERSION.SDK_INT >= 33 && callback != null) Api33.unregister(activity, callback);
  }
  private static final class Api33 {
    static Object register(Activity activity, Runnable back) {
      android.window.OnBackInvokedCallback callback = back::run;
      activity.getOnBackInvokedDispatcher().registerOnBackInvokedCallback(
          android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT, callback);
      return callback;
    }
    static void unregister(Activity activity, Object callback) {
      activity.getOnBackInvokedDispatcher().unregisterOnBackInvokedCallback(
          (android.window.OnBackInvokedCallback)callback);
    }
  }
}
