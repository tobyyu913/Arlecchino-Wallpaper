package com.tobyyu.arlecchinowallpaper;
import java.text.Collator;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
public final class AppIndexTest {
    public static void main(String[] args) {
        if (!AppIndex.section("Éclair").equals("E")) throw new AssertionError("Accent index");
        if (!AppIndex.section("微信").equals("#")) throw new AssertionError("Non-Latin section");
        if (!AppIndex.section("").equals("#")) throw new AssertionError("Empty label");
        if (!AppIndex.matches("Éclair", "example.app", "ecl")) throw new AssertionError("Accent search");
        if (!AppIndex.matches("微信", "com.tencent.mm", "微信")) throw new AssertionError("Unicode search");
        if (!AppIndex.matches("Mail", "org.example.mail", "EXAMPLE")) throw new AssertionError("Package search");
        if (AppIndex.matches("Maps", "com.maps", "music")) throw new AssertionError("Incorrect match");
        List<String> names = new ArrayList<>(Arrays.asList("Zoom", "alpha", "Éclair", "微信", "Beta"));
        Collator collator = Collator.getInstance(Locale.ENGLISH);
        names.sort((a,b) -> AppIndex.compare(a,b,collator));
        if (!names.equals(Arrays.asList("微信", "alpha", "Beta", "Éclair", "Zoom"))) throw new AssertionError(names);
        System.out.println("PASS: alphabet groups, locale sorting, accent/Unicode/package search");
    }
}
