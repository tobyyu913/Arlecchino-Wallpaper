package com.tobyyu.arlecchinowallpaper;

import java.text.Collator;
import java.text.Normalizer;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;

/** Locale-aware app ordering and accent-insensitive search; no launcher-vendor code. */
final class AppIndex {
  private static Function<String, String> transliterate = s -> s;
  private static final Map<String, String> romanized = new HashMap<>();

  static synchronized void setTransliterator(Function<String, String> function) {
    transliterate = function;
    romanized.clear();
  }

  private static synchronized String romanize(String text) {
    String cached = romanized.get(text);
    if (cached != null) return cached;
    String result;
    try {
      result = transliterate.apply(text);
    } catch (RuntimeException e) {
      result = text;
    }
    romanized.put(text, result);
    return result;
  }

  static String searchKey(String text) {
    return Normalizer.normalize(text, Normalizer.Form.NFD)
        .replaceAll("\\p{M}+", "")
        .toLowerCase(Locale.ROOT);
  }

  static String section(String label) {
    String key = searchKey(romanize(label.trim())).toUpperCase(Locale.ROOT);
    if (key.isEmpty()) return "#";
    char c = key.charAt(0);
    return c >= 'A' && c <= 'Z' ? String.valueOf(c) : "#";
  }

  static boolean matches(String label, String packageName, String query) {
    String key = searchKey(query.trim());
    return searchKey(label).contains(key)
        || searchKey(packageName).contains(key)
        || searchKey(romanize(label)).replace(" ", "").contains(key.replace(" ", ""));
  }

  static int compare(String a, String b, Collator collator) {
    int group = section(a).compareTo(section(b));
    return group != 0 ? group : collator.compare(a, b);
  }
}
