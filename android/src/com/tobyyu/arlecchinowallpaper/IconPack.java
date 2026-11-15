package com.tobyyu.arlecchinowallpaper;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ResolveInfo;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.util.Xml;
import java.io.InputStream;
import java.util.*;
import org.xmlpull.v1.XmlPullParser;

/** Reads standard appfilter metadata from a separately installed icon pack. */
final class IconPack {
  static final String[] CATEGORIES = {
    "org.adw.launcher.THEMES", "com.anddoes.launcher.THEME", "com.teslacoilsw.launcher.THEME"
  };

  static Map<String, String> installed(Context c) {
    Map<String, String> packs = new LinkedHashMap<>();
    for (String category : CATEGORIES) {
      Intent i = new Intent(Intent.ACTION_MAIN).addCategory(category);
      for (ResolveInfo info : c.getPackageManager().queryIntentActivities(i, 0))
        packs.put(info.activityInfo.packageName, info.loadLabel(c.getPackageManager()).toString());
    }
    return packs;
  }

  private Resources resources;
  private final Map<String, String> drawables = new HashMap<>();

  IconPack(Context context, String pkg) {
    if (pkg.isEmpty()) return;
    try {
      resources = context.getPackageManager().getResourcesForApplication(pkg);
      int id = resources.getIdentifier("appfilter", "xml", pkg);
      if (id != 0) parse(resources.getXml(id));
      else {
        try (InputStream stream = resources.getAssets().open("appfilter.xml")) {
          XmlPullParser parser = Xml.newPullParser();
          parser.setInput(stream, "UTF-8");
          parse(parser);
        }
      }
    } catch (Exception e) {
      resources = null;
      drawables.clear();
    }
  }

  private void parse(XmlPullParser p) throws Exception {
    for (int event = p.getEventType(); event != XmlPullParser.END_DOCUMENT; event = p.next())
      if (event == XmlPullParser.START_TAG && "item".equals(p.getName())) {
        String component = p.getAttributeValue(null, "component"),
            drawable = p.getAttributeValue(null, "drawable");
        if (component != null && drawable != null) drawables.put(component, drawable);
      }
  }

  Drawable icon(String component, String pkg) {
    if (resources == null) return null;
    String name = drawables.get("ComponentInfo{" + component + "}");
    if (name == null) return null;
    int id = resources.getIdentifier(name, "drawable", pkg);
    if (id == 0) return null;
    try {
      return resources.getDrawable(id, null);
    } catch (Resources.NotFoundException e) {
      return null;
    }
  }
}
