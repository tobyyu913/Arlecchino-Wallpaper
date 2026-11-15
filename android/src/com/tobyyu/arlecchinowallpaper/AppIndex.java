package com.tobyyu.arlecchinowallpaper;

import java.text.Collator;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/** Locale-aware app ordering and accent-insensitive search; no launcher-vendor code. */
final class AppIndex {
    static String searchKey(String text) {
        return Normalizer.normalize(text, Normalizer.Form.NFD).replaceAll("\\p{M}+", "").toLowerCase(Locale.ROOT);
    }
    static String section(String label) {
        String key = searchKey(label.trim()).toUpperCase(Locale.ROOT);
        if (key.isEmpty()) return "#";
        char c = key.charAt(0);
        return c >= 'A' && c <= 'Z' ? String.valueOf(c) : "#";
    }
    static boolean matches(String label, String packageName, String query) {
        String key = searchKey(query.trim());
        return searchKey(label).contains(key) || searchKey(packageName).contains(key);
    }
    static int compare(String a, String b, Collator collator) {
        int group = section(a).compareTo(section(b));
        return group != 0 ? group : collator.compare(a, b);
    }
}
