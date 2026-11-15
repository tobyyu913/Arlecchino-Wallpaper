package com.tobyyu.arlecchinowallpaper;

import android.app.Activity;
import android.app.WallpaperManager;
import android.app.WallpaperInfo;
import android.app.AlertDialog;
import android.app.role.RoleManager;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.ResolveInfo;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextWatcher;
import android.text.format.DateFormat;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;
import java.text.Collator;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class HomeActivity extends Activity {
    private static final int WHITE = 0xFFF6F1ED, MUTED = 0xFFCDC2C5, ACCENT = 0xFFF19AA9;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService loader = Executors.newSingleThreadExecutor();
    private final List<App> apps = new ArrayList<>();
    private final List<Row> rows = new ArrayList<>();
    private final Map<String, Integer> sections = new HashMap<>();
    private final Set<String> favorites = new LinkedHashSet<>();
    private final android.util.LruCache<String, Drawable> icons = new android.util.LruCache<>(64);
    private ListView list;
    private EditText search;
    private TextView clock, date, setup, wallpaperSetup;
    private AlphabetRail alphabetRail;
    private AppAdapter adapter;
    private SharedPreferences preferences;
    private boolean dead, receiverRegistered;
    private int loadGeneration;
    private boolean showIcons = true;
    private boolean showAll;
    private final BroadcastReceiver changes = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (HomeNotifications.CHANGED.equals(action)) { adapter.notifyDataSetChanged(); return; }
            if (Intent.ACTION_TIME_TICK.equals(action) || Intent.ACTION_TIME_CHANGED.equals(action) || Intent.ACTION_DATE_CHANGED.equals(action) || Intent.ACTION_TIMEZONE_CHANGED.equals(action)) updateClock();
            else { icons.evictAll(); loadApps(); }
        }
    };
    private static final class App {
        final String label, id, packageName;
        final ResolveInfo info;
        App(String label, ResolveInfo info) {
            this.label = label; this.info = info;
            packageName = info.activityInfo.packageName;
            id = new ComponentName(packageName, info.activityInfo.name).flattenToString();
        }
    }
    private static final class Row {
        final String title; final App app;
        Row(String title, App app) { this.title = title; this.app = app; }
    }
    private int dp(float value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    private TextView text(String value, float size, int color) {
        TextView view = new TextView(this); view.setText(value); view.setTextSize(size); view.setTextColor(color);
        view.setFontFeatureSettings("tnum"); return view;
    }
    private GradientDrawable chip() {
        GradientDrawable shape = new GradientDrawable(); shape.setColor(0xB31E1820);
        shape.setCornerRadius(dp(18)); shape.setStroke(dp(1), 0x555D424B); return shape;
    }
    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        preferences = getSharedPreferences("home", MODE_PRIVATE);
        showIcons = preferences.getBoolean("icons", true);
        for (String id : preferences.getString("favorites", "").split("\n")) if (!id.isEmpty()) favorites.add(id);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER);
        if (Build.VERSION.SDK_INT >= 30) getWindow().setDecorFitsSystemWindows(false);
        getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        getWindow().setStatusBarColor(Color.TRANSPARENT);
        getWindow().setNavigationBarColor(Color.TRANSPARENT);
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING);
        LinearLayout root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(24), dp(12), dp(16), dp(8)); root.setFocusableInTouchMode(true);
        root.setBackground(new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
            new int[]{0xB50B090E, 0x860B090E, 0x160B090E}));
        // Insets keep controls away from the cutout, status bar, gesture area and keyboard.
        root.setOnApplyWindowInsetsListener((v, insets) -> {
            if (Build.VERSION.SDK_INT >= 30) {
                android.graphics.Insets safe = insets.getInsets(android.view.WindowInsets.Type.systemBars() | android.view.WindowInsets.Type.displayCutout() | android.view.WindowInsets.Type.ime());
                v.setPadding(dp(24) + safe.left, dp(12) + safe.top, dp(16) + safe.right, dp(8) + safe.bottom);
            } else v.setPadding(dp(24) + insets.getSystemWindowInsetLeft(), dp(12) + insets.getSystemWindowInsetTop(), dp(16) + insets.getSystemWindowInsetRight(), dp(8) + insets.getSystemWindowInsetBottom());
            return insets;
        });
        LinearLayout header = new LinearLayout(this); header.setGravity(Gravity.CENTER_VERTICAL);
        TextView brand = text("ARLECCHINO  /  HOME", 11, ACCENT); brand.setLetterSpacing(.12f);
        header.addView(brand, new LinearLayout.LayoutParams(0, dp(36), 1));
        TextView settings = text("⋮", 27, WHITE); settings.setSingleLine(true); settings.setGravity(Gravity.CENTER);
        settings.setContentDescription("Launcher settings"); settings.setOnClickListener(v -> settings());
        header.addView(settings, new LinearLayout.LayoutParams(dp(48), dp(48))); root.addView(header);
        clock = text("", 48, WHITE); clock.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        clock.setPadding(0, dp(10), 0, 0); clock.setOnClickListener(v -> openClock()); root.addView(clock);
        date = text("", 14, MUTED); date.setPadding(dp(2), 0, 0, dp(14)); root.addView(date); updateClock();
        setup = text("Make this your home screen  ↗", 13, ACCENT); setup.setGravity(Gravity.CENTER_VERTICAL);
        setup.setPadding(dp(14), 0, dp(14), 0); setup.setBackground(chip()); setup.setOnClickListener(v -> chooseHome());
        root.addView(setup, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(42)));
        wallpaperSetup = text("Enable curtain wallpaper  ↗", 13, ACCENT);
        wallpaperSetup.setGravity(Gravity.CENTER_VERTICAL); wallpaperSetup.setPadding(dp(14), 0, dp(14), 0);
        wallpaperSetup.setBackground(chip()); wallpaperSetup.setOnClickListener(v -> MainActivity.applyWallpaper(this));
        LinearLayout.LayoutParams wallpaperParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(42));
        wallpaperParams.topMargin = dp(8); root.addView(wallpaperSetup, wallpaperParams);
        search = new EditText(this); search.setSingleLine(true); search.setTextSize(17);
        search.setTextColor(WHITE); search.setHintTextColor(MUTED); search.setHint("Find an app");
        search.setPadding(dp(16), 0, dp(16), 0); search.setBackground(chip());
        search.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH);
        search.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        LinearLayout.LayoutParams searchParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48));
        searchParams.topMargin = dp(14); searchParams.bottomMargin = dp(10); root.addView(search, searchParams);
        LinearLayout content = new LinearLayout(this);
        list = new ListView(this); list.setDivider(null); list.setSelector(new android.graphics.drawable.ColorDrawable(0x304F2934));
        list.setVerticalScrollBarEnabled(false); list.setClipToPadding(false); list.setPadding(0, 0, dp(6), dp(20));
        adapter = new AppAdapter(); list.setAdapter(adapter);
        list.setOnItemClickListener((parent, view, position, id) -> {
            Row row = rows.get(position);
            if (row.app != null) launch(row.app);
            else if (row.title.startsWith("＋")) chooseFavorites();
            else if (row.title.startsWith("All apps")) { showAll = true; rebuildRows(); list.setSelection(0); }
        });
        list.setOnItemLongClickListener((parent, view, position, id) -> { App app = rows.get(position).app; if (app == null) return false; appMenu(app); return true; });
        content.addView(list, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1));
        alphabetRail = new AlphabetRail();
        content.addView(alphabetRail, new LinearLayout.LayoutParams(dp(32), ViewGroup.LayoutParams.MATCH_PARENT));
        root.addView(content, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        setContentView(root); root.requestFocus(); root.requestApplyInsets();
        search.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            public void onTextChanged(CharSequence s, int start, int before, int count) { rebuildRows(); }
            public void afterTextChanged(Editable s) { }
        });
        search.setOnEditorActionListener((v, action, event) -> {
            if (action == android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH) {
                for (Row row : rows) if (row.app != null) { launch(row.app); break; }
                return true;
            }
            return false;
        });
        IntentFilter packages = new IntentFilter(Intent.ACTION_PACKAGE_ADDED); packages.addAction(Intent.ACTION_PACKAGE_REMOVED); packages.addAction(Intent.ACTION_PACKAGE_CHANGED); packages.addDataScheme("package");
        IntentFilter time = new IntentFilter(Intent.ACTION_TIME_TICK); time.addAction(Intent.ACTION_TIME_CHANGED); time.addAction(Intent.ACTION_DATE_CHANGED); time.addAction(Intent.ACTION_TIMEZONE_CHANGED); time.addAction(Intent.ACTION_LOCALE_CHANGED);
        if (Build.VERSION.SDK_INT >= 33) { registerReceiver(changes, packages, Context.RECEIVER_NOT_EXPORTED); registerReceiver(changes, time, Context.RECEIVER_NOT_EXPORTED); }
        else { registerReceiver(changes, packages); registerReceiver(changes, time); }
        IntentFilter notifications = new IntentFilter(HomeNotifications.CHANGED);
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(changes, notifications, Context.RECEIVER_NOT_EXPORTED);
        else registerReceiver(changes, notifications);
        receiverRegistered = true; loadApps();
    }
    @Override protected void onResume() { super.onResume(); updateClock(); updateSetup(); }
    private void updateClock() {
        if (clock == null) return;
        Calendar now = Calendar.getInstance();
        clock.setText(DateFormat.format(DateFormat.is24HourFormat(this) ? "HH:mm" : "h:mm", now));
        date.setText(java.text.DateFormat.getDateInstance(java.text.DateFormat.FULL).format(now.getTime()));
    }
    private void updateSetup() {
        if (setup == null) return;
        boolean held = false;
        if (Build.VERSION.SDK_INT >= 29) {
            RoleManager manager = getSystemService(RoleManager.class);
            held = manager != null && manager.isRoleHeld(RoleManager.ROLE_HOME);
        } else {
            ResolveInfo home = getPackageManager().resolveActivity(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), 0);
            held = home != null && home.activityInfo != null && getPackageName().equals(home.activityInfo.packageName);
        }
        setup.setVisibility(held ? View.GONE : View.VISIBLE);
        WallpaperInfo wallpaper = WallpaperManager.getInstance(this).getWallpaperInfo();
        wallpaperSetup.setVisibility(wallpaper != null && getPackageName().equals(wallpaper.getPackageName()) ? View.GONE : View.VISIBLE);
    }
    private void loadApps() {
        final int token = ++loadGeneration;
        loader.execute(() -> {
            List<App> result = new ArrayList<>(); Set<String> seen = new LinkedHashSet<>();
            Intent intent = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
            for (ResolveInfo info : getPackageManager().queryIntentActivities(intent, 0)) {
                if (info.activityInfo == null || !info.activityInfo.exported || !info.activityInfo.enabled || getPackageName().equals(info.activityInfo.packageName)) continue;
                App app = new App(info.loadLabel(getPackageManager()).toString(), info);
                if (seen.add(app.id)) result.add(app);
            }
            Collator collator = Collator.getInstance();
            result.sort((a, b) -> AppIndex.compare(a.label, b.label, collator));
            handler.post(() -> {
                if (dead || token != loadGeneration) return;
                apps.clear(); apps.addAll(result); rebuildRows();
            });
        });
    }
    private void rebuildRows() {
        String query = search == null ? "" : search.getText().toString();
        rows.clear(); sections.clear();
        if (query.trim().isEmpty() && !showAll) {
            sections.put("★", 0);
            rows.add(new Row("FAVORITES", null));
            for (String id : favorites) for (App app : apps) if (app.id.equals(id)) { rows.add(new Row(null, app)); break; }
            rows.add(new Row("＋ Choose favorites", null));
            rows.add(new Row("All apps  →", null));
        } else {
            String section = ""; int count = 0;
            for (App app : apps) {
                if (!AppIndex.matches(app.label, app.packageName, query)) continue;
                String next = AppIndex.section(app.label);
                if (!next.equals(section)) { section = next; sections.put(section, rows.size()); rows.add(new Row(section, null)); }
                rows.add(new Row(null, app)); count++;
            }
            if (count == 0) rows.add(new Row(apps.isEmpty() ? "Loading apps…" : "No apps found", null));
        }
        adapter.notifyDataSetChanged();
        if (alphabetRail != null) alphabetRail.invalidate();
    }
    private void launch(App app) {
        Intent intent = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        intent.setComponent(ComponentName.unflattenFromString(app.id));
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        try { hideKeyboard(); startActivity(intent); }
        catch (ActivityNotFoundException | SecurityException error) { Toast.makeText(this, "App is unavailable", Toast.LENGTH_SHORT).show(); loadApps(); }
    }
    private void hideKeyboard() { ((InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(search.getWindowToken(), 0); search.clearFocus(); }
    private void saveFavorites() {
        StringBuilder saved = new StringBuilder();
        for (String id : favorites) { if (saved.length() > 0) saved.append('\n'); saved.append(id); }
        preferences.edit().putString("favorites", saved.toString()).apply(); rebuildRows();
    }
    private void chooseFavorites() {
        List<App> choices = new ArrayList<>(apps);
        CharSequence[] labels = new CharSequence[choices.size()]; boolean[] checked = new boolean[choices.size()];
        Set<String> selection = new LinkedHashSet<>(favorites);
        for (int i = 0; i < choices.size(); i++) { labels[i] = choices.get(i).label; checked[i] = favorites.contains(choices.get(i).id); }
        new AlertDialog.Builder(this).setTitle("Choose your favorites")
            .setMultiChoiceItems(labels, checked, (dialog, which, selected) -> {
                if (selected) selection.add(choices.get(which).id); else selection.remove(choices.get(which).id);
            }).setPositiveButton("Save", (dialog, which) -> { favorites.clear(); favorites.addAll(selection); showAll = false; search.setText(""); saveFavorites(); })
            .setNegativeButton("Cancel", null).show();
    }
    private void appMenu(App app) {
        boolean pinned = favorites.contains(app.id);
        String[] actions = pinned ? new String[]{"Remove from favorites", "Move up", "Move down", "App info"} : new String[]{"Add to favorites", "App info"};
        new AlertDialog.Builder(this).setTitle(app.label).setItems(actions, (dialog, which) -> {
            if (which == 0) { if (pinned) favorites.remove(app.id); else favorites.add(app.id); saveFavorites(); }
            else if (pinned && (which == 1 || which == 2)) {
                List<String> order = new ArrayList<>(favorites); int current = order.indexOf(app.id);
                int target = current + (which == 1 ? -1 : 1);
                if (target >= 0 && target < order.size()) { java.util.Collections.swap(order, current, target); favorites.clear(); favorites.addAll(order); saveFavorites(); }
            } else startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + app.packageName)));
        }).show();
    }
    private void settings() {
        new AlertDialog.Builder(this).setTitle("Arlecchino Home").setItems(new String[]{"Choose default home app", "Curtain wallpaper", "Manage favorites", "Notification previews", showIcons ? "Hide app icons" : "Show app icons", "System settings"}, (dialog, which) -> {
            if (which == 0) chooseHome();
            else if (which == 1) MainActivity.applyWallpaper(this);
            else if (which == 2) chooseFavorites();
            else if (which == 3) new AlertDialog.Builder(this).setTitle("Notification previews")
                .setMessage("Optional: Android notification access lets Arlecchino Home show message previews under your favorite apps. Notification text stays in memory on this phone. Enable access only if you want these previews.")
                .setPositiveButton("Open Android settings", (d, w) -> startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)))
                .setNegativeButton("Cancel", null).show();
            else if (which == 4) { showIcons = !showIcons; preferences.edit().putBoolean("icons", showIcons).apply(); adapter.notifyDataSetChanged(); }
            else startActivity(new Intent(Settings.ACTION_SETTINGS));
        }).show();
    }
    private void chooseHome() {
        try {
            if (Build.VERSION.SDK_INT >= 29) {
                RoleManager manager = getSystemService(RoleManager.class);
                if (manager != null && manager.isRoleAvailable(RoleManager.ROLE_HOME) && !manager.isRoleHeld(RoleManager.ROLE_HOME)) { startActivityForResult(manager.createRequestRoleIntent(RoleManager.ROLE_HOME), 10); return; }
            }
            startActivity(new Intent(Settings.ACTION_HOME_SETTINGS));
        } catch (ActivityNotFoundException error) { Toast.makeText(this, "Choose Arlecchino Home in your default app settings", Toast.LENGTH_LONG).show(); }
    }
    private void openClock() {
        try { startActivity(new Intent(android.provider.AlarmClock.ACTION_SHOW_ALARMS)); }
        catch (ActivityNotFoundException error) { Toast.makeText(this, date.getText(), Toast.LENGTH_SHORT).show(); }
    }
    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent); setIntent(intent);
        if (Intent.ACTION_MAIN.equals(intent.getAction())) { showAll = false; search.setText(""); hideKeyboard(); rebuildRows(); list.setSelection(0); }
    }
    @Override public void onBackPressed() { showAll = false; search.setText(""); hideKeyboard(); rebuildRows(); list.setSelection(0); }
    @Override protected void onDestroy() {
        dead = true; if (receiverRegistered) unregisterReceiver(changes);
        loader.shutdownNow(); handler.removeCallbacksAndMessages(null); icons.evictAll(); super.onDestroy();
    }
    private final class AppAdapter extends BaseAdapter {
        public int getCount() { return rows.size(); }
        public Object getItem(int position) { return rows.get(position); }
        public long getItemId(int position) { return position; }
        public int getViewTypeCount() { return 2; }
        public int getItemViewType(int position) { return rows.get(position).app == null ? 0 : 1; }
        public boolean isEnabled(int position) { Row row = rows.get(position); return row.app != null || row.title.startsWith("＋") || row.title.startsWith("All apps"); }
        public View getView(int position, View convert, ViewGroup parent) {
            Row row = rows.get(position);
            if (row.app == null) {
                TextView header = convert instanceof TextView ? (TextView)convert : text("", 11, ACCENT);
                header.setText(row.title); header.setTextSize(row.title.startsWith("＋") || row.title.startsWith("All apps") ? 16 : 11); header.setLetterSpacing(.04f); header.setGravity(Gravity.BOTTOM);
                header.setPadding(dp(4), dp(16), 0, dp(8)); header.setMinHeight(dp(42)); return header;
            }
            LinearLayout line;
            if (convert instanceof LinearLayout) line = (LinearLayout)convert;
            else {
                line = new LinearLayout(HomeActivity.this); line.setGravity(Gravity.CENTER_VERTICAL);
                line.setPadding(dp(3), dp(8), dp(6), dp(8)); line.setMinimumHeight(dp(58));
                ImageView icon = new ImageView(HomeActivity.this); icon.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
                LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams(dp(34), dp(34)); ip.rightMargin = dp(14); line.addView(icon, ip);
                TextView label = text("", 21, WHITE); label.setMaxLines(2); LinearLayout words = new LinearLayout(HomeActivity.this); words.setOrientation(LinearLayout.VERTICAL);
                words.addView(label);
                TextView preview = text("", 12, MUTED); preview.setMaxLines(2); preview.setPadding(0, dp(4), 0, 0); words.addView(preview);
                line.addView(words, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
            }
            ImageView image = (ImageView)line.getChildAt(0); LinearLayout words = (LinearLayout)line.getChildAt(1);
            TextView label = (TextView)words.getChildAt(0); TextView previewView = (TextView)words.getChildAt(1);
            HomeNotifications.Preview notification = favorites.contains(row.app.id) ? HomeNotifications.latest(row.app.packageName) : null;
            previewView.setVisibility(notification == null ? View.GONE : View.VISIBLE);
            previewView.setText(notification == null ? "" : notification.text);
            previewView.setOnClickListener(notification == null ? null : v -> {
                try { notification.action.send(); } catch (android.app.PendingIntent.CanceledException ignored) { launch(row.app); }
            });
            label.setText(row.app.label); line.setContentDescription(row.app.label + (favorites.contains(row.app.id) ? ", favorite" : ""));
            image.setVisibility(showIcons ? View.VISIBLE : View.GONE);
            if (showIcons) {
                Drawable icon = icons.get(row.app.id);
                if (icon == null) { try { icon = row.app.info.loadIcon(getPackageManager()); icons.put(row.app.id, icon); } catch (RuntimeException ignored) { } }
                image.setImageDrawable(icon);
            }
            return line;
        }
    }
    private final class AlphabetRail extends View {
        private final String letters = "★#ABCDEFGHIJKLMNOPQRSTUVWXYZ";
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private int selected = -1;
        AlphabetRail() { super(HomeActivity.this); setContentDescription("Alphabet index. Slide up or down to jump to a letter."); }
        @Override protected void onDraw(Canvas canvas) {
            float step = getHeight() / (float)letters.length(); paint.setTextSize(Math.min(dp(12), step * .72f)); paint.setTextAlign(Paint.Align.CENTER);
            for (int i = 0; i < letters.length(); i++) {
                paint.setColor(i == selected ? WHITE : (i > 0 || sections.containsKey("★")) ? ACCENT : 0x886B6369);
                if (i == selected) { Paint p = new Paint(Paint.ANTI_ALIAS_FLAG); p.setColor(0xB0552334); canvas.drawCircle(getWidth()/2f, step*(i+.5f), Math.min(dp(15), step*.55f), p); }
                canvas.drawText(String.valueOf(letters.charAt(i)), getWidth()/2f, step*(i+.5f) - (paint.ascent()+paint.descent())/2, paint);
            }
        }
        @Override public boolean onTouchEvent(MotionEvent event) {
            if (event.getActionMasked() == MotionEvent.ACTION_DOWN || event.getActionMasked() == MotionEvent.ACTION_MOVE) {
                int index = Math.max(0, Math.min(letters.length()-1, (int)(event.getY()/Math.max(1,getHeight())*letters.length())));
                if (index != selected) {
                    selected = index;
                    if (index == 0) { showAll = false; search.setText(""); rebuildRows(); hideKeyboard(); list.setSelection(0); }
                    else if (!showAll || search.length() > 0) { showAll = true; search.setText(""); hideKeyboard(); rebuildRows(); }
                    Integer position = sections.get(String.valueOf(letters.charAt(index)));
                    if (position != null) { list.setSelection(position); performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK); announceForAccessibility(String.valueOf(letters.charAt(index))); }
                    invalidate();
                }
                return true;
            }
            selected = -1; invalidate(); if (event.getActionMasked() == MotionEvent.ACTION_UP) performClick(); return true;
        }
        @Override public boolean performClick() { super.performClick(); return true; }
    }
}
