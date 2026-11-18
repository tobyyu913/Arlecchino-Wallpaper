package com.tobyyu.arlecchinowallpaper;

/** No guessed generations: unknown radio information is labelled DATA. */
final class NetworkLabel {
  static String mobile(int type, int override) {
    if (override == 3 || override == 4 || override == 5 || type == 20) return "5G";
    switch (type) {
      case 13: case 19: return "4G";
      case 3: case 5: case 6: case 8: case 9: case 10: case 12: case 14: case 15: case 17: return "3G";
      case 1: case 2: case 4: case 7: case 11: case 16: return "2G";
      default: return "DATA";
    }
  }
}
