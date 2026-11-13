package com.tobyyu.arlecchinowallpaper;

import android.app.Activity;
import android.app.WallpaperManager;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

public final class MainActivity extends Activity {
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setGravity(Gravity.CENTER);
        int padding = (int) (28 * getResources().getDisplayMetrics().density);
        layout.setPadding(padding, padding, padding, padding);
        layout.setBackgroundColor(Color.BLACK);
        TextView title = new TextView(this);
        title.setText("Arlecchino Wallpaper"); title.setTextColor(Color.WHITE);
        title.setTextSize(28); title.setGravity(Gravity.CENTER);
        layout.addView(title);
        TextView description = new TextView(this);
        description.setText("Screen on. Curtain opens.\nThen it stays open.");
        description.setTextColor(0xFFBBBBBB); description.setTextSize(17);
        description.setGravity(Gravity.CENTER); description.setPadding(0, padding, 0, padding);
        layout.addView(description);
        Button apply = new Button(this);
        apply.setText("Set wallpaper");
        apply.setOnClickListener(view -> {
            Intent intent = new Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER);
            intent.putExtra(WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT,
                new ComponentName(this, CurtainWallpaperService.class));
            try { startActivity(intent); }
            catch (ActivityNotFoundException error) {
                try { startActivity(new Intent(WallpaperManager.ACTION_LIVE_WALLPAPER_CHOOSER)); }
                catch (ActivityNotFoundException missing) { Toast.makeText(this, "This device has no live wallpaper picker.", Toast.LENGTH_LONG).show(); }
            }
        });
        layout.addView(apply);
        setContentView(layout);
    }
}
