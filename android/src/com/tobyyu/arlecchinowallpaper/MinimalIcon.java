package com.tobyyu.arlecchinowallpaper;

import android.graphics.*;
import android.graphics.drawable.Drawable;
import java.util.Locale;

/** Bundled original outline glyphs. No downloaded icon-pack artwork. */
final class MinimalIcon extends Drawable {
  private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final String pkg, label;

  MinimalIcon(String pkg, String label) {
    this.pkg = pkg.toLowerCase(Locale.ROOT);
    this.label = label;
    p.setColor(0xFFF8F4F1);
    p.setStyle(Paint.Style.STROKE);
    p.setStrokeWidth(1.5f);
    p.setStrokeCap(Paint.Cap.ROUND);
    p.setStrokeJoin(Paint.Join.ROUND);
  }

  private boolean has(String... words) {
    for (String word : words) if (pkg.contains(word)) return true;
    return false;
  }

  private void line(Canvas c, float... points) {
    Path path = new Path();
    path.moveTo(points[0], points[1]);
    for (int i = 2; i < points.length; i += 2) path.lineTo(points[i], points[i + 1]);
    c.drawPath(path, p);
  }

  public void draw(Canvas original) {
    int saved = original.save();
    Rect b = getBounds();
    original.translate(b.left, b.top);
    original.scale(b.width() / 40f, b.height() / 40f);
    Canvas c = original;
    if (has("chrome", "browser", "opera", "firefox", "brave")) {
      c.drawCircle(20, 20, 16, p);
      c.drawOval(12, 4, 28, 36, p);
      line(c, 5, 14, 35, 14);
      line(c, 5, 26, 35, 26);
    } else if (has("whatsapp", "telegram", "tencent.mm", "messag", "signal", "discord", "wechat")) {
      Path s = new Path();
      s.moveTo(7, 7);
      s.lineTo(33, 7);
      s.quadTo(36, 7, 36, 11);
      s.lineTo(36, 27);
      s.quadTo(36, 31, 32, 31);
      s.lineTo(14, 31);
      s.lineTo(5, 37);
      s.lineTo(7, 27);
      s.close();
      c.drawPath(s, p);
      line(c, 13, 16, 29, 16);
      line(c, 13, 22, 25, 22);
    } else if (has("youtube", "video", "vanced", "netflix", "player", "bilibili")) {
      c.drawRoundRect(3, 8, 37, 32, 6, 6, p);
      line(c, 17, 14, 27, 20, 17, 26, 17, 14);
    } else if (has("spotify", "music", "audio", "podcast")) {
      line(c, 16, 29, 16, 8, 32, 5, 32, 25);
      c.drawOval(6, 26, 16, 34, p);
      c.drawOval(22, 22, 32, 30, p);
    } else if (has("nothing", "headphone", "sound", "earbud")) {
      c.drawArc(5, 4, 35, 34, 180, 180, false, p);
      c.drawRoundRect(5, 18, 12, 33, 3, 3, p);
      c.drawRoundRect(28, 18, 35, 33, 3, 3, p);
    } else if (has("mail", "outlook", "gmail")) {
      c.drawRoundRect(3, 7, 37, 33, 3, 3, p);
      line(c, 4, 9, 20, 22, 36, 9);
    } else if (has("setting")) {
      for (int i = 0; i < 8; i++) {
        c.save();
        c.rotate(i * 45, 20, 20);
        line(c, 20, 3, 20, 8);
        c.restore();
      }
      c.drawCircle(20, 20, 12, p);
      c.drawCircle(20, 20, 5, p);
    } else if (has("clock", "alarm")) {
      c.drawCircle(20, 21, 15, p);
      line(c, 20, 10, 20, 21, 28, 25);
      line(c, 5, 5, 10, 2);
      line(c, 30, 2, 35, 5);
    } else if (has("calendar")) {
      c.drawRoundRect(4, 7, 36, 36, 3, 3, p);
      line(c, 4, 15, 36, 15);
      line(c, 12, 3, 12, 11);
      line(c, 28, 3, 28, 11);
      line(c, 12, 23, 17, 23);
      line(c, 23, 23, 28, 23);
      line(c, 12, 29, 17, 29);
    } else if (has("camera")) {
      c.drawRoundRect(3, 10, 37, 33, 4, 4, p);
      line(c, 11, 10, 15, 5, 25, 5, 29, 10);
      c.drawCircle(20, 22, 8, p);
    } else if (has("photo", "gallery", "image")) {
      c.drawRoundRect(4, 5, 36, 35, 3, 3, p);
      c.drawCircle(13, 13, 3, p);
      line(c, 5, 30, 17, 19, 23, 25, 29, 18, 35, 25);
    } else if (has("garena", "game", "steam", "xbox", "playstation")) {
      c.drawRoundRect(3, 10, 37, 31, 8, 8, p);
      line(c, 10, 20, 20, 20);
      line(c, 15, 15, 15, 25);
      c.drawCircle(29, 17, 1, p);
      c.drawCircle(26, 23, 1, p);
    } else if (has("duolingo", "learn", "education")) {
      Path s = new Path();
      s.moveTo(10, 32);
      s.cubicTo(3, 10, 19, 2, 34, 5);
      s.cubicTo(37, 22, 27, 35, 10, 32);
      c.drawPath(s, p);
      line(c, 8, 35, 27, 13);
    } else if (has("map", "navigation", "location")) {
      Path s = new Path();
      s.moveTo(20, 37);
      s.cubicTo(14, 29, 6, 20, 7, 13);
      s.cubicTo(9, -1, 32, 0, 33, 14);
      s.cubicTo(34, 22, 26, 31, 20, 37);
      c.drawPath(s, p);
      c.drawCircle(20, 14, 5, p);
    } else if (has("file", "drive", "document", "explorer")) {
      Path s = new Path();
      s.moveTo(3, 10);
      s.lineTo(15, 10);
      s.lineTo(19, 6);
      s.lineTo(34, 6);
      s.lineTo(37, 33);
      s.lineTo(3, 33);
      s.close();
      c.drawPath(s, p);
      line(c, 4, 15, 36, 15);
    } else if (has("phone", "dialer", "contact")) {
      Path s = new Path();
      s.moveTo(9, 4);
      s.lineTo(16, 12);
      s.lineTo(11, 17);
      s.quadTo(16, 26, 24, 29);
      s.lineTo(29, 24);
      s.lineTo(37, 31);
      s.quadTo(31, 43, 17, 32);
      s.quadTo(-1, 17, 9, 4);
      c.drawPath(s, p);
    } else if (has("store", "vending", "market")) {
      c.drawRoundRect(6, 12, 34, 36, 3, 3, p);
      c.drawArc(12, 3, 28, 22, 180, 180, false, p);
    } else if (has("password", "auth", "security", "vpn", "lock")) {
      c.drawRoundRect(8, 17, 32, 35, 3, 3, p);
      c.drawArc(12, 3, 28, 26, 180, 180, false, p);
      c.drawCircle(20, 25, 2, p);
      line(c, 20, 27, 20, 30);
    } else if (has("note", "todo", "task")) {
      c.drawRoundRect(6, 4, 34, 36, 3, 3, p);
      line(c, 12, 13, 28, 13);
      line(c, 12, 20, 28, 20);
      line(c, 12, 27, 23, 27);
    } else {
      c.drawCircle(20, 20, 16, p);
      p.setStyle(Paint.Style.FILL);
      p.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
      p.setTextAlign(Paint.Align.CENTER);
      p.setTextSize(20);
      String initial =
          label.isEmpty()
              ? "·"
              : label.substring(0, label.offsetByCodePoints(0, 1)).toUpperCase(Locale.ROOT);
      c.drawText(initial, 20, 20 - (p.ascent() + p.descent()) / 2, p);
      p.setStyle(Paint.Style.STROKE);
    }
    original.restoreToCount(saved);
  }

  public void setAlpha(int alpha) {
    p.setAlpha(alpha);
    invalidateSelf();
  }

  public void setColorFilter(ColorFilter filter) {
    p.setColorFilter(filter);
    invalidateSelf();
  }

  public int getOpacity() {
    return PixelFormat.TRANSLUCENT;
  }
}
