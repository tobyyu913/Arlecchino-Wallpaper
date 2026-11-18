package com.tobyyu.arlecchinowallpaper;

/** The OS owns its edge/back and bottom/home/overview gesture regions. */
final class NavigationZones {
  static boolean systemStart(float x, float y, int width, int height,
      int left, int right, int bottom) {
    return x < left || x >= width - right || y >= height - bottom;
  }
  static boolean alphabetStart(float x, float y, int width, int height,
      int left, int right, int bottom, int leftBand, int rightBand) {
    return !systemStart(x, y, width, height, left, right, bottom)
        && (x < left + leftBand || x > width - right - rightBand);
  }
}
