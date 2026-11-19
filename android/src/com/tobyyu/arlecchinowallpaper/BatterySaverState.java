package com.tobyyu.arlecchinowallpaper;

import android.content.Context;
import android.os.PowerManager;
import android.provider.Settings;

/** OEM state takes precedence over Android's independent saver flag. */
final class BatterySaverState {
  static final String XIAOMI_KEY="POWER_SAVE_MODE_OPEN";
  static boolean enabled(Context context) {
    String nativeState=Settings.System.getString(context.getContentResolver(),XIAOMI_KEY);
    PowerManager power=context.getSystemService(PowerManager.class);
    return resolve(nativeState,power!=null&&power.isPowerSaveMode());
  }
  static boolean resolve(String nativeState,boolean androidState) {
    if("0".equals(nativeState))return false;
    if("1".equals(nativeState))return true;
    return androidState;
  }
}
