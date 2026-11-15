package com.tobyyu.arlecchinowallpaper;

import android.content.Context;
import android.graphics.*;
import android.graphics.drawable.Drawable;

/** Offline AI-generated replacements for apps absent from the selected icon pack. */
final class AiIcons {
  private static int resource(String packageName) {
    switch (packageName) {
      case "com.liquid.launcher.os.custom.widget": return R.drawable.ai_icon_0;
      case "com.talk51.lite": return R.drawable.ai_icon_1;
      case "app.igames.ps2": return R.drawable.ai_icon_2;
      case "app.spidey.tracker": return R.drawable.ai_icon_3;
      case "au.gov.wa.pta.transperth": return R.drawable.ai_icon_4;
      case "co.faria.mobilemanagebac": return R.drawable.ai_icon_5;
      case "com.akhlaqshah.engp5": return R.drawable.ai_icon_6;
      case "com.antgroup.leopard.android": return R.drawable.ai_icon_7;
      case "com.celcomdigi.selfcare": return R.drawable.ai_icon_8;
      case "com.clouddrive2.CloudDrive2": return R.drawable.ai_icon_9;
      case "com.crazy.fish.prankster.game": return R.drawable.ai_icon_10;
      case "com.duokan.reader": return R.drawable.ai_icon_11;
      case "com.es.nsb": return R.drawable.ai_icon_12;
      case "com.fengeek.f002": return R.drawable.ai_icon_13;
      case "com.flutefingeringchart": return R.drawable.ai_icon_14;
      case "com.hahd.aygd3.mi": return R.drawable.ai_icon_15;
      case "com.hand.control.card.maker": return R.drawable.ai_icon_16;
      case "com.happyelements.AndroidAnimal": return R.drawable.ai_icon_17;
      case "com.hermes.j1game.mi": return R.drawable.ai_icon_18;
      case "com.ifengyu.intercom": return R.drawable.ai_icon_19;
      case "com.learninga_z.onyourown": return R.drawable.ai_icon_20;
      case "com.mandai.mfa": return R.drawable.ai_icon_21;
      case "com.miui.virtualsim": return R.drawable.ai_icon_22;
      case "com.miui.voiceassistProxy": return R.drawable.ai_icon_23;
      case "com.molink.john.hummingbird": return R.drawable.ai_icon_24;
      case "com.netease.sky": return R.drawable.ai_icon_25;
      case "com.newpsp.romgames": return R.drawable.ai_icon_26;
      case "com.one4studio.one4home": return R.drawable.ai_icon_27;
      case "com.qianyan2.ting.de": return R.drawable.ai_icon_28;
      case "com.scatterlab.messenger": return R.drawable.ai_icon_29;
      case "com.seedleap.loopitapp": return R.drawable.ai_icon_30;
      case "com.shortcutq.maker": return R.drawable.ai_icon_31;
      case "com.sonkins.tflute": return R.drawable.ai_icon_32;
      case "com.spidey.tracker": return R.drawable.ai_icon_33;
      case "com.superboyyy.mosstackedsignal": return R.drawable.ai_icon_34;
      case "com.tanyu.digitalclock": return R.drawable.ai_icon_35;
      case "com.tencent.kairos": return R.drawable.ai_icon_36;
      case "com.tencent.tmgp.cod": return R.drawable.ai_icon_37;
      case "com.toby.controller": return R.drawable.ai_icon_38;
      case "com.toby.gyromouse": return R.drawable.ai_icon_39;
      case "com.toby.ipchecker": return R.drawable.ai_icon_40;
      case "com.toby.jarvishud": return R.drawable.ai_icon_41;
      case "com.toby.machome": return R.drawable.ai_icon_42;
      case "com.toby.papernote": return R.drawable.ai_icon_43;
      case "com.toby.photosorter": return R.drawable.ai_icon_44;
      case "com.toby.scical": return R.drawable.ai_icon_45;
      case "com.toby.walkman": return R.drawable.ai_icon_46;
      case "com.tutteo.flat": return R.drawable.ai_icon_47;
      case "com.usmile.health": return R.drawable.ai_icon_48;
      case "com.virtualpiano.musicalkeyboard": return R.drawable.ai_icon_49;
      case "com.xiaomi.tinygame": return R.drawable.ai_icon_50;
      case "com.xiaomi.vipaccount": return R.drawable.ai_icon_51;
      case "com.ztjhuyu.tgsh.mi": return R.drawable.ai_icon_52;
      case "dev.toby.bosefree": return R.drawable.ai_icon_53;
      case "dev.victorialauncher": return R.drawable.ai_icon_54;
      case "gg.deltahub.android": return R.drawable.ai_icon_55;
      case "im.fdx.led": return R.drawable.ai_icon_56;
      case "org.chromium.webapk.ac010f30e19cd2891_v2": return R.drawable.ai_icon_9;
      case "sg.gov.ica.mobile.app": return R.drawable.ai_icon_58;
      default: return 0;
    }
  }

  static Drawable icon(Context context, String packageName) {
    int id = resource(packageName);
    if (id == 0) return null;
    BitmapFactory.Options options = new BitmapFactory.Options();
    options.inScaled = false;
    options.inSampleSize = 4;
    Bitmap bitmap = BitmapFactory.decodeResource(context.getResources(), id, options);
    return bitmap == null ? null : new MaskIcon(bitmap);
  }

  /** Treat generated RGB as irrelevant: one consistent ink color uses the alpha mask. */
  private static final class MaskIcon extends Drawable {
    private final Bitmap bitmap;
    private final Rect source;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);

    MaskIcon(Bitmap bitmap) {
      this.bitmap = bitmap;
      int width = bitmap.getWidth(), height = bitmap.getHeight();
      int[] pixels = new int[width * height];
      bitmap.getPixels(pixels, 0, width, 0, 0, width, height);
      int left = width, top = height, right = -1, bottom = -1;
      for (int y = 0; y < height; y++) for (int x = 0; x < width; x++) {
        if ((pixels[y * width + x] >>> 24) < 128) continue;
        left = Math.min(left, x); top = Math.min(top, y);
        right = Math.max(right, x); bottom = Math.max(bottom, y);
      }
      source = right < left ? new Rect(0, 0, width, height)
          : new Rect(Math.max(0, left - 2), Math.max(0, top - 2),
              Math.min(width, right + 3), Math.min(height, bottom + 3));
      paint.setColorFilter(new PorterDuffColorFilter(0xFFF8F4F1, PorterDuff.Mode.SRC_IN));
    }

    @Override public void draw(Canvas canvas) {
      Rect bounds = getBounds();
      float available = Math.min(bounds.width(), bounds.height()) * .90f;
      float scale = Math.min(available / source.width(), available / source.height());
      float width = source.width() * scale, height = source.height() * scale;
      RectF target = new RectF(bounds.exactCenterX() - width / 2,
          bounds.exactCenterY() - height / 2,
          bounds.exactCenterX() + width / 2, bounds.exactCenterY() + height / 2);
      canvas.drawBitmap(bitmap, source, target, paint);
    }
    @Override public void setAlpha(int alpha) { paint.setAlpha(alpha); invalidateSelf(); }
    @Override public void setColorFilter(ColorFilter filter) {
      paint.setColorFilter(filter == null
          ? new PorterDuffColorFilter(0xFFF8F4F1, PorterDuff.Mode.SRC_IN) : filter);
      invalidateSelf();
    }
    @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    @Override public int getIntrinsicWidth() { return 128; }
    @Override public int getIntrinsicHeight() { return 128; }
  }
}
