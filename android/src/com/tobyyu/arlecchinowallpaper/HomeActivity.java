package com.tobyyu.arlecchinowallpaper;

import android.app.*;
import android.app.role.RoleManager;
import android.content.*;
import android.content.pm.*;
import android.graphics.*;
import android.graphics.drawable.*;
import android.net.Uri;
import android.os.*;
import android.provider.Settings;
import android.text.*;
import android.text.format.DateFormat;
import android.view.*;
import android.view.inputmethod.*;
import android.widget.*;
import java.text.Collator;
import java.util.*;
import java.util.concurrent.*;
import org.json.*;

/** Independent native launcher. Home, alphabet and search share one app surface. */
public final class HomeActivity extends Activity {
  private static final int INK = 0xFFF8F4F1, MUTED = 0xFFCBC2C5, ACCENT = 0xFFF2A2B0;
  private final Handler ui = new Handler(Looper.getMainLooper());
  private final ExecutorService worker = Executors.newSingleThreadExecutor();
  private final List<App> apps = new ArrayList<>();
  private final List<Row> rows = new ArrayList<>();
  private final Set<String> favorites = new LinkedHashSet<>(), hidden = new HashSet<>();
  private final Map<String, Integer> sections = new HashMap<>();
  private final android.util.LruCache<String, Drawable> icons = new android.util.LruCache<>(72);
  private SharedPreferences prefs;
  private IconPack iconPack;
  private String iconPackPackage = "";
  private FrameLayout root;
  private View appScrim;
  private int previewTransition;
  private ListView list;
  private LinearLayout homeHeader;
  private TextView clock, date;
  private EditText search;
  private Alphabet rail;
  private FrameLayout gestureLayer;
  private ScrollView gestureScroll;
  private int previewIndex = -1;
  private float previewTop;
  private AppsAdapter adapter;
  private boolean all, searching, dead, registered, showIcons = true;
  private int generation, topInset, bottomInset, edgePointer = -1;
  private float downX, downY;
  private Row downRow;
  private boolean edgeGesture, intercepted;
  private final BroadcastReceiver changes =
      new BroadcastReceiver() {
        public void onReceive(Context c, Intent i) {
          String action = i.getAction();
          if (HomeNotifications.CHANGED.equals(action)) {
            adapter.notifyDataSetChanged();
            return;
          }
          if (Intent.ACTION_TIME_TICK.equals(action)
              || Intent.ACTION_TIME_CHANGED.equals(action)
              || Intent.ACTION_DATE_CHANGED.equals(action)
              || Intent.ACTION_TIMEZONE_CHANGED.equals(action)) updateClock();
          else {
            icons.evictAll();
            loadApps();
          }
        }
      };

  private static final class App {
    final String id, pkg, label;
    final ResolveInfo info;
    final long installed;

    App(ResolveInfo info, String label, long installed) {
      this.info = info;
      this.label = label;
      this.installed = installed;
      pkg = info.activityInfo.packageName;
      id = new ComponentName(pkg, info.activityInfo.name).flattenToString();
    }
  }

  private static final class Row {
    final String title;
    final App app;
    final String folder;
    final String action;

    Row(String title, App app, String folder, String action) {
      this.title = title;
      this.app = app;
      this.folder = folder;
      this.action = action;
    }

    static Row app(App app) {
      return new Row(null, app, null, null);
    }

    static Row title(String title) {
      return new Row(title, null, null, null);
    }

    static Row action(String title, String action) {
      return new Row(title, null, null, action);
    }
  }

  private int dp(float n) {
    return Math.round(n * getResources().getDisplayMetrics().density);
  }

  private TextView text(String value, float size, int color) {
    TextView t = new TextView(this);
    t.setText(value);
    t.setTextSize(size);
    t.setTextColor(color);
    t.setShadowLayer(dp(1.5f), 0, dp(.5f), 0xBF000000);
    return t;
  }

  private GradientDrawable background(int color, float radius) {
    GradientDrawable d = new GradientDrawable();
    d.setColor(color);
    d.setCornerRadius(dp(radius));
    return d;
  }

  private void loadSets() {
    favorites.clear();
    hidden.clear();
    for (String s : prefs.getString("favorites", "").split("\n"))
      if (!s.isEmpty()) favorites.add(s);
    hidden.addAll(prefs.getStringSet("hidden", new HashSet<>()));
  }

  @Override
  public void onCreate(Bundle state) {
    super.onCreate(state);
    prefs = getSharedPreferences("home", MODE_PRIVATE);
    if (Build.VERSION.SDK_INT >= 29) {
      try {
        android.icu.text.Transliterator converter =
            android.icu.text.Transliterator.getInstance("Any-Latin; Latin-ASCII");
        AppIndex.setTransliterator(value -> {
          synchronized (converter) {
            return converter.transliterate(value);
          }
        });
      } catch (IllegalArgumentException ignored) {
      }
    }
    loadSets();
    showIcons = prefs.getBoolean("icons", true);
    iconPackPackage = prefs.getString("iconPack", "@outline");
    iconPack = new IconPack(this, iconPackPackage);
    getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER);
    getWindow().setBackgroundDrawableResource(android.R.color.transparent);
    if (Build.VERSION.SDK_INT >= 30) getWindow().setDecorFitsSystemWindows(false);
    getWindow().setStatusBarColor(Color.TRANSPARENT);
    getWindow().setNavigationBarColor(Color.TRANSPARENT);
    getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING);
    root = new FrameLayout(this);
    root.setFocusableInTouchMode(true);
    appScrim = new View(this);
    appScrim.setBackgroundColor(0x30000000);
    appScrim.setAlpha(0);
    root.addView(appScrim, new FrameLayout.LayoutParams(-1, -1));
    root.setBackground(
        new GradientDrawable(
            GradientDrawable.Orientation.LEFT_RIGHT,
            new int[] {0x650B080D, 0x300B080D, 0x00000000}));
    list = new ListView(this);
    list.setDivider(null);
    list.setVerticalScrollBarEnabled(false);
    list.setClipToPadding(false);
    list.setSelector(new ColorDrawable(0x264F2934));
    list.setOverScrollMode(View.OVER_SCROLL_IF_CONTENT_SCROLLS);
    homeHeader = new LinearLayout(this);
    homeHeader.setOrientation(LinearLayout.VERTICAL);
    View breathingRoom = new View(this);
    breathingRoom.setLayoutParams(
        new LinearLayout.LayoutParams(
            1, (int) (getResources().getDisplayMetrics().heightPixels * .15f)));
    homeHeader.addView(breathingRoom);
    clock = text("", 48, INK);
    clock.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
    clock.setFontFeatureSettings("tnum");
    clock.setPadding(0, 0, 0, 0);
    clock.setOnClickListener(v -> openClock());
    clock.setOnLongClickListener(v -> {
      settings();
      return true;
    });
    homeHeader.addView(clock);
    date = text("", 17, INK);
    date.setPadding(dp(1), dp(1), 0, dp(8));
    TextView searchChip = text("⌕  Search apps", 17, MUTED);
    searchChip.setGravity(Gravity.CENTER_VERTICAL);
    searchChip.setPadding(dp(14), 0, dp(14), 0);
    searchChip.setBackground(background(0x75201820, 18));
    searchChip.setOnClickListener(v -> openSearch());
    LinearLayout.LayoutParams chipParams = new LinearLayout.LayoutParams(-1, dp(44));
    chipParams.topMargin = dp(4);
    chipParams.bottomMargin = dp(16);
    date.setOnClickListener(v -> openCalendar());
    date.setOnLongClickListener(v -> {
      settings();
      return true;
    });
    homeHeader.addView(date);
    homeHeader.addView(searchChip, chipParams);
    homeHeader.setOnLongClickListener(v -> {
      settings();
      return true;
    });
    list.addHeaderView(homeHeader, null, false);
    adapter = new AppsAdapter();
    list.setAdapter(adapter);
    root.addView(list, new FrameLayout.LayoutParams(-1, -1));
    list.setOnItemClickListener((parent, view, pos, id) -> {
      int index = pos - list.getHeaderViewsCount();
      if (index < 0 || index >= rows.size()) return;
      Row row = rows.get(index);
      if (row.app != null) launch(row.app);
      else if (row.folder != null) folderPopup(row.folder);
      else if ("favorites".equals(row.action)) chooseFavorites();
      else if ("settings".equals(row.action)) settings();
      else if ("search".equals(row.action)) openSearch();
      else if ("web".equals(row.action)) {
        try {
          startActivity(
              new Intent(
                  Intent.ACTION_VIEW,
                  Uri.parse(
                      "https://www.google.com/search?q="
                          + Uri.encode(search.getText().toString()))));
        } catch (ActivityNotFoundException ignored) {
        }
      }
    });
    list.setOnItemLongClickListener((parent, view, pos, id) -> {
      int index = pos - list.getHeaderViewsCount();
      if (index < 0 || index >= rows.size()) {
        settings();
        return true;
      }
      Row row = rows.get(index);
      if (row.app != null) appMenu(row.app);
      else if (row.folder != null) folderMenu(row.folder);
      else settings();
      return true;
    });
    gestureLayer = new FrameLayout(this);
    gestureLayer.setBackgroundColor(Color.TRANSPARENT);
    gestureLayer.setVisibility(View.GONE);
    gestureScroll = new ScrollView(this);
    gestureScroll.setVerticalScrollBarEnabled(false);
    gestureLayer.addView(gestureScroll);
    root.addView(gestureLayer, new FrameLayout.LayoutParams(-1, -1));
    rail = new Alphabet();
    FrameLayout.LayoutParams rp = new FrameLayout.LayoutParams(dp(170), -1, Gravity.RIGHT);
    root.addView(rail, rp);
    search = new EditText(this);
    search.setTextColor(INK);
    search.setHintTextColor(MUTED);
    search.setTextSize(20);
    search.setSingleLine(true);
    search.setHint("Search apps");
    search.setBackground(background(0xD51A141C, 20));
    search.setPadding(dp(18), 0, dp(18), 0);
    search.setVisibility(View.GONE);
    search.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
    search.setInputType(
        android.text.InputType.TYPE_CLASS_TEXT
            | android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
    FrameLayout.LayoutParams sp = new FrameLayout.LayoutParams(-1, dp(52));
    sp.leftMargin = dp(40);
    sp.rightMargin = dp(40);
    root.addView(search, sp);
    search.addTextChangedListener(
        new TextWatcher() {
          public void beforeTextChanged(CharSequence s, int a, int c, int f) {}

          public void onTextChanged(CharSequence s, int a, int b, int c) {
            if (searching) rebuild();
          }

          public void afterTextChanged(Editable e) {}
        });
    search.setOnEditorActionListener((v, action, event) -> {
      if (action == EditorInfo.IME_ACTION_SEARCH) {
        for (Row row : rows)
          if (row.app != null) {
            launch(row.app);
            break;
          }
        return true;
      }
      return false;
    });
    root.setOnApplyWindowInsetsListener((v, insets) -> {
      int ime = 0;
      if (Build.VERSION.SDK_INT >= 30) {
        Insets bars =
            insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
        topInset = bars.top;
        bottomInset = bars.bottom;
        ime = insets.getInsets(WindowInsets.Type.ime()).bottom;
      } else {
        topInset = insets.getSystemWindowInsetTop();
        bottomInset = insets.getSystemWindowInsetBottom();
      }
      FrameLayout.LayoutParams p = (FrameLayout.LayoutParams) search.getLayoutParams();
      p.topMargin = topInset + dp(14);
      search.setLayoutParams(p);
      list.setPadding(
          dp(48), topInset + (searching ? dp(84) : 0), dp(64), Math.max(bottomInset, ime) + dp(24));
      rail.invalidate();
      return insets;
    });
    setContentView(root);
    setDim();
    root.requestFocus();
    root.requestApplyInsets();
    updateClock();
    IntentFilter pkg = new IntentFilter(Intent.ACTION_PACKAGE_ADDED);
    pkg.addAction(Intent.ACTION_PACKAGE_REMOVED);
    pkg.addAction(Intent.ACTION_PACKAGE_CHANGED);
    pkg.addDataScheme("package");
    register(pkg);
    IntentFilter time = new IntentFilter(Intent.ACTION_TIME_TICK);
    time.addAction(Intent.ACTION_TIME_CHANGED);
    time.addAction(Intent.ACTION_DATE_CHANGED);
    time.addAction(Intent.ACTION_TIMEZONE_CHANGED);
    time.addAction(Intent.ACTION_LOCALE_CHANGED);
    register(time);
    register(new IntentFilter(HomeNotifications.CHANGED));
    registered = true;
    loadApps();
  }

  private void register(IntentFilter filter) {
    if (Build.VERSION.SDK_INT >= 33)
      registerReceiver(changes, filter, Context.RECEIVER_NOT_EXPORTED);
    else registerReceiver(changes, filter);
  }

  private void updateClock() {
    if (clock == null) return;
    Calendar c = Calendar.getInstance();
    clock.setText(DateFormat.format(DateFormat.is24HourFormat(this) ? "HH:mm" : "h:mm", c));
    date.setText(
        DateFormat.format(DateFormat.getBestDateTimePattern(Locale.getDefault(), "MMMEd"), c));
  }

  @Override
  protected void onResume() {
    super.onResume();
    updateClock();
    if (adapter != null) adapter.notifyDataSetChanged();
  }

  private String label(App app) {
    return prefs.getString("name:" + app.id, app.label);
  }

  private App app(String id) {
    for (App a : apps) if (a.id.equals(id)) return a;
    return null;
  }

  private void loadApps() {
    final int token = ++generation;
    worker.execute(() -> {
      List<App> result = new ArrayList<>();
      Set<String> seen = new HashSet<>();
      Intent intent = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
      for (ResolveInfo i : getPackageManager().queryIntentActivities(intent, 0)) {
        if (i.activityInfo == null
            || !i.activityInfo.exported
            || !i.activityInfo.enabled
            || getPackageName().equals(i.activityInfo.packageName)) continue;
        try {
          long installed =
              getPackageManager().getPackageInfo(i.activityInfo.packageName, 0).firstInstallTime;
          App a = new App(i, i.loadLabel(getPackageManager()).toString(), installed);
          if (seen.add(a.id)) result.add(a);
        } catch (PackageManager.NameNotFoundException | RuntimeException ignored) {
        }
      }
      ui.post(() -> {
        if (dead || token != generation) return;
        apps.clear();
        apps.addAll(result);
        rebuild();
      });
    });
  }

  private void mode(boolean appsMode, boolean searchMode) {
    all = appsMode;
    searching = searchMode;
    appScrim.animate().cancel();
    appScrim.animate().alpha(all || searching ? 1 : 0).setDuration(140).start();
    if (all || searching) {
      if (list.getHeaderViewsCount() > 0) list.removeHeaderView(homeHeader);
    } else if (list.getHeaderViewsCount() == 0) {
      homeHeader.setVisibility(View.VISIBLE);
      homeHeader.setLayoutParams(new AbsListView.LayoutParams(-1, -2));
      list.addHeaderView(homeHeader, null, false);
    }
    search.setVisibility(searching ? View.VISIBLE : View.GONE);
    rail.setVisibility(searching ? View.GONE : View.VISIBLE);
    root.requestApplyInsets();
    rebuild();
    list.setSelection(0);
  }

  private void home() {
    if (rail != null) rail.dragging = false;
    previewTransition++;
    gestureLayer.animate().cancel();
    gestureLayer.setVisibility(View.GONE);
    previewIndex = -1;
    list.animate().cancel();
    list.setAlpha(1);
    hideKeyboard();
    search.setText("");
    mode(false, false);
  }

  private void openSearch() {
    mode(false, true);
    search.requestFocus();
    ui.postDelayed(
        () -> {
          if (!dead && searching)
            ((InputMethodManager) getSystemService(INPUT_METHOD_SERVICE))
                .showSoftInput(search, InputMethodManager.SHOW_IMPLICIT);
        },
        100);
  }

  private void hideKeyboard() {
    ((InputMethodManager) getSystemService(INPUT_METHOD_SERVICE))
        .hideSoftInputFromWindow(search.getWindowToken(), 0);
    search.clearFocus();
    root.requestFocus();
  }

  private void rebuild() {
    rows.clear();
    sections.clear();
    String query = searching ? search.getText().toString() : "";
    if (!all && !searching) {
      sections.put("★", 0);
      for (String id : favorites) {
        if (id.startsWith("folder:")) rows.add(new Row(null, null, id, null));
        else {
          App a = app(id);
          if (a != null) rows.add(Row.app(a));
        }
      }
      if (rows.isEmpty()) {
        rows.add(Row.action("Choose favorites", "favorites"));
        rows.add(Row.action("Arlecchino settings", "settings"));
      }
    } else {
      Collator collator = Collator.getInstance();
      List<App> sorted = new ArrayList<>();
      for (App a : apps)
        if (!hidden.contains(a.id) && AppIndex.matches(label(a), a.pkg, query)) sorted.add(a);
      sorted.sort((a, b) -> {
        if (searching && query.isEmpty()) {
          long av = prefs.getLong("last:" + a.id, 0), bv = prefs.getLong("last:" + b.id, 0);
          if (av != bv) return Long.compare(bv, av);
          return Long.compare(b.installed, a.installed);
        }
        int group = AppIndex.section(label(a)).compareTo(AppIndex.section(label(b)));
        if (group != 0) return group;
        if (prefs.getBoolean("usageOrder", true)) {
          int count =
              Integer.compare(prefs.getInt("count:" + b.id, 0), prefs.getInt("count:" + a.id, 0));
          if (count != 0) return count;
        }
        return collator.compare(label(a), label(b));
      });
      String group = "";
      int limit = searching && query.isEmpty() ? Math.min(8, sorted.size()) : sorted.size();
      for (int i = 0; i < limit; i++) {
        App a = sorted.get(i);
        String next = AppIndex.section(label(a));
        if (!searching && !next.equals(group)) {
          group = next;
          sections.put(group, rows.size());
          rows.add(Row.title(group));
        }
        rows.add(Row.app(a));
      }
      if (sorted.isEmpty()) rows.add(Row.title(apps.isEmpty() ? "Loading apps…" : "No apps found"));
      if (searching && !query.trim().isEmpty())
        rows.add(Row.action("Search web for “" + query + "”", "web"));
      if (all) rows.add(Row.action("Arlecchino settings", "settings"));
    }
    adapter.notifyDataSetChanged();
    rail.invalidate();
  }

  private void launch(App a) {
    try {
      Intent i =
          new Intent(Intent.ACTION_MAIN)
              .addCategory(Intent.CATEGORY_LAUNCHER)
              .setComponent(ComponentName.unflattenFromString(a.id))
              .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
      hideKeyboard();
      startActivity(i);
      prefs
          .edit()
          .putLong("last:" + a.id, System.currentTimeMillis())
          .putInt("count:" + a.id, prefs.getInt("count:" + a.id, 0) + 1)
          .apply();
    } catch (ActivityNotFoundException | SecurityException e) {
      Toast.makeText(this, "App unavailable", Toast.LENGTH_SHORT).show();
      loadApps();
    }
  }

  private void saveFavorites() {
    StringBuilder s = new StringBuilder();
    for (String id : favorites) {
      if (s.length() > 0) s.append('\n');
      s.append(id);
    }
    prefs.edit().putString("favorites", s.toString()).apply();
    rebuild();
  }

  private void chooseFavorites() {
    List<App> choices = new ArrayList<>(apps);
    Collator c = Collator.getInstance();
    choices.sort((a, b) -> AppIndex.compare(label(a), label(b), c));
    CharSequence[] names = new CharSequence[choices.size()];
    boolean[] checked = new boolean[choices.size()];
    Set<String> selection = new LinkedHashSet<>(favorites);
    for (int i = 0; i < choices.size(); i++) {
      names[i] = label(choices.get(i));
      checked[i] = selection.contains(choices.get(i).id);
    }
    new AlertDialog.Builder(this)
        .setTitle("Edit favorites")
        .setMultiChoiceItems(
            names,
            checked,
            (d, index, on) -> {
              if (on) selection.add(choices.get(index).id);
              else selection.remove(choices.get(index).id);
            })
        .setPositiveButton(
            "Done",
            (d, w) -> {
              favorites.clear();
              favorites.addAll(selection);
              saveFavorites();
              home();
            })
        .setNeutralButton("Create folder", (d, w) -> createFolder())
        .setNegativeButton("Cancel", null)
        .show();
  }

  private void sheet(String title, List<String> labels, java.util.function.IntConsumer action) {
    final Dialog dialog = new Dialog(this);
    LinearLayout body = new LinearLayout(this);
    body.setOrientation(LinearLayout.VERTICAL);
    body.setPadding(dp(24), dp(18), dp(24), dp(20));
    body.setBackground(background(0xF51D1720, 24));
    View handle = new View(this);
    handle.setBackground(background(0xFF786570, 3));
    LinearLayout.LayoutParams hp = new LinearLayout.LayoutParams(dp(32), dp(4));
    hp.gravity = Gravity.CENTER_HORIZONTAL;
    hp.bottomMargin = dp(18);
    body.addView(handle, hp);
    TextView heading = text(title, 24, INK);
    heading.setPadding(0, 0, 0, dp(14));
    body.addView(heading);
    ScrollView scroll = new ScrollView(this);
    LinearLayout items = new LinearLayout(this);
    items.setOrientation(LinearLayout.VERTICAL);
    scroll.addView(items);
    body.addView(scroll, new LinearLayout.LayoutParams(-1, -2));
    for (int i = 0; i < labels.size(); i++) {
      final int index = i;
      TextView item = text(labels.get(i), 17, INK);
      item.setGravity(Gravity.CENTER_VERTICAL);
      item.setMinHeight(dp(48));
      item.setPadding(dp(4), dp(8), dp(4), dp(8));
      item.setOnClickListener(v -> {
        dialog.dismiss();
        action.accept(index);
      });
      items.addView(item);
    }
    dialog.setContentView(body);
    Window window = dialog.getWindow();
    if (window != null) {
      window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
      window.setGravity(Gravity.BOTTOM);
      window.setLayout(-1, -2);
      window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
      WindowManager.LayoutParams p = window.getAttributes();
      p.dimAmount = .35f;
      window.setAttributes(p);
    }
    dialog.show();
    if (window != null) window.setLayout(-1, -2);
  }

  private void appMenu(App a) {
    boolean pinned = favorites.contains(a.id);
    List<String> actions =
        new ArrayList<>(
            Arrays.asList(
                pinned ? "Remove from favorites" : "Add to favorites",
                "Edit favorites",
                "App pop-up",
                "Rename",
                "Hide app",
                "App info",
                "Uninstall"));
    if (pinned) {
      actions.add("Move up");
      actions.add("Move down");
    }
    sheet(
        label(a),
        actions,
        n -> {
          String action = actions.get(n);
          switch (action) {
            case "Add to favorites":
              favorites.add(a.id);
              saveFavorites();
              break;
            case "Remove from favorites":
              favorites.remove(a.id);
              saveFavorites();
              break;
            case "Edit favorites":
              chooseFavorites();
              break;
            case "App pop-up":
              appPopup(a);
              break;
            case "Rename":
              renameApp(a);
              break;
            case "Hide app":
              hidden.add(a.id);
              favorites.remove(a.id);
              prefs.edit().putStringSet("hidden", new HashSet<>(hidden)).apply();
              saveFavorites();
              break;
            case "App info":
              startActivity(
                  new Intent(
                      Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + a.pkg)));
              break;
            case "Uninstall":
              startActivity(new Intent(Intent.ACTION_DELETE, Uri.parse("package:" + a.pkg)));
              break;
            case "Move up":
              reorder(a.id, -1);
              break;
            case "Move down":
              reorder(a.id, 1);
              break;
          }
        });
  }

  private void reorder(String id, int delta) {
    List<String> order = new ArrayList<>(favorites);
    int p = order.indexOf(id), to = p + delta;
    if (p >= 0 && to >= 0 && to < order.size()) {
      Collections.swap(order, p, to);
      favorites.clear();
      favorites.addAll(order);
      saveFavorites();
    }
  }

  private void renameApp(App a) {
    EditText name = new EditText(this);
    name.setSingleLine();
    name.setText(label(a));
    new AlertDialog.Builder(this)
        .setTitle("App name")
        .setView(name)
        .setPositiveButton(
            "Save",
            (d, w) -> {
              String value = name.getText().toString().trim();
              if (!value.isEmpty()) prefs.edit().putString("name:" + a.id, value).apply();
              rebuild();
            })
        .setNeutralButton(
            "Reset",
            (d, w) -> {
              prefs.edit().remove("name:" + a.id).apply();
              rebuild();
            })
        .setNegativeButton("Cancel", null)
        .show();
  }

  private void appPopup(App a) {
    List<String> labels = new ArrayList<>();
    List<Runnable> actions = new ArrayList<>();
    HomeNotifications.Preview notification = HomeNotifications.latest(a.pkg);
    if (notification != null) {
      labels.add(notification.text);
      actions.add(() -> openNotification(a, notification));
      if (notification.replyAction != null) {
        labels.add("Reply");
        actions.add(() -> reply(notification));
      }
      labels.add("Dismiss notification");
      actions.add(() -> HomeNotifications.dismiss(notification.key));
    }
    LauncherApps launcher = (LauncherApps) getSystemService(LAUNCHER_APPS_SERVICE);
    try {
      if (launcher.hasShortcutHostPermission()) {
        LauncherApps.ShortcutQuery query =
            new LauncherApps.ShortcutQuery()
                .setPackage(a.pkg)
                .setQueryFlags(
                    LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC
                        | LauncherApps.ShortcutQuery.FLAG_MATCH_MANIFEST
                        | LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED);
        List<ShortcutInfo> shortcuts =
            launcher.getShortcuts(query, android.os.Process.myUserHandle());
        if (shortcuts != null)
          for (ShortcutInfo shortcut : shortcuts) {
            if (!shortcut.isEnabled()) continue;
            labels.add(
                shortcut.getShortLabel() == null
                    ? "Shortcut"
                    : shortcut.getShortLabel().toString());
            actions.add(() -> {
              try {
                launcher.startShortcut(
                    a.pkg, shortcut.getId(), null, null, shortcut.getUserHandle());
              } catch (RuntimeException e) {
                launch(a);
              }
            });
          }
      }
    } catch (SecurityException ignored) {
    }
    labels.add("Open " + label(a));
    actions.add(() -> launch(a));
    labels.add("App info");
    actions.add(() ->
        startActivity(
            new Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + a.pkg))));
    sheet(label(a), labels, n -> actions.get(n).run());
  }

  private void openNotification(App a, HomeNotifications.Preview n) {
    try {
      n.action.send();
    } catch (PendingIntent.CanceledException e) {
      launch(a);
    }
  }

  private void reply(HomeNotifications.Preview n) {
    EditText field = new EditText(this);
    field.setHint("Reply");
    new AlertDialog.Builder(this)
        .setTitle("Reply")
        .setView(field)
        .setPositiveButton(
            "Send",
            (d, w) -> {
              String value = field.getText().toString().trim();
              if (value.isEmpty()) return;
              try {
                Intent result = new Intent();
                Bundle data = new Bundle();
                for (RemoteInput input : n.replyAction.getRemoteInputs())
                  data.putCharSequence(input.getResultKey(), value);
                RemoteInput.addResultsToIntent(n.replyAction.getRemoteInputs(), result, data);
                n.replyAction.actionIntent.send(this, 0, result);
              } catch (PendingIntent.CanceledException e) {
                Toast.makeText(this, "Reply is no longer available", Toast.LENGTH_SHORT).show();
              }
            })
        .setNegativeButton("Cancel", null)
        .show();
  }

  private List<String> folderApps(String id) {
    List<String> ids = new ArrayList<>();
    try {
      JSONArray values = new JSONArray(prefs.getString(id + ":apps", "[]"));
      for (int i = 0; i < values.length(); i++) ids.add(values.getString(i));
    } catch (JSONException ignored) {
    }
    return ids;
  }

  private String folderName(String id) {
    return prefs.getString(id + ":name", "Folder");
  }

  private void createFolder() {
    EditText input = new EditText(this);
    input.setSingleLine();
    input.setHint("Folder name");
    new AlertDialog.Builder(this)
        .setTitle("Create folder")
        .setView(input)
        .setPositiveButton(
            "Create",
            (d, w) -> {
              String name = input.getText().toString().trim();
              if (name.isEmpty()) return;
              String id = "folder:" + UUID.randomUUID();
              prefs.edit().putString(id + ":name", name).apply();
              favorites.add(id);
              saveFavorites();
              editFolder(id);
            })
        .setNegativeButton("Cancel", null)
        .show();
  }

  private void folderPopup(String id) {
    List<App> contents = new ArrayList<>();
    List<String> names = new ArrayList<>();
    for (String key : folderApps(id)) {
      App a = app(key);
      if (a != null) {
        contents.add(a);
        names.add(label(a));
      }
    }
    names.add("Edit folder");
    sheet(
        folderName(id),
        names,
        n -> {
          if (n < contents.size()) launch(contents.get(n));
          else editFolder(id);
        });
  }

  private void editFolder(String id) {
    List<App> choices = new ArrayList<>(apps);
    Collator col = Collator.getInstance();
    choices.sort((a, b) -> col.compare(label(a), label(b)));
    Set<String> selected = new LinkedHashSet<>(folderApps(id));
    String[] names = new String[choices.size()];
    boolean[] checked = new boolean[choices.size()];
    for (int i = 0; i < choices.size(); i++) {
      names[i] = label(choices.get(i));
      checked[i] = selected.contains(choices.get(i).id);
    }
    new AlertDialog.Builder(this)
        .setTitle(folderName(id))
        .setMultiChoiceItems(
            names,
            checked,
            (d, w, on) -> {
              if (on) selected.add(choices.get(w).id);
              else selected.remove(choices.get(w).id);
            })
        .setPositiveButton(
            "Done",
            (d, w) -> {
              prefs.edit().putString(id + ":apps", new JSONArray(selected).toString()).apply();
              rebuild();
            })
        .setNegativeButton("Cancel", null)
        .show();
  }

  private void folderMenu(String id) {
    sheet(
        folderName(id),
        Arrays.asList("Edit folder", "Rename", "Move up", "Move down", "Remove folder"),
        n -> {
          if (n == 0) editFolder(id);
          else if (n == 1) {
            EditText field = new EditText(this);
            field.setText(folderName(id));
            new AlertDialog.Builder(this)
                .setTitle("Folder name")
                .setView(field)
                .setPositiveButton(
                    "Save",
                    (d, w) -> {
                      String value = field.getText().toString().trim();
                      if (!value.isEmpty()) prefs.edit().putString(id + ":name", value).apply();
                      rebuild();
                    })
                .setNegativeButton("Cancel", null)
                .show();
          } else if (n == 2 || n == 3) reorder(id, n == 2 ? -1 : 1);
          else {
            favorites.remove(id);
            saveFavorites();
          }
        });
  }

  private void settings() {
    List<String> labels =
        Arrays.asList(
            "Edit favorites",
            "Create pop-up folder",
            "Look & theme",
            "Hidden apps",
            "Usage-based app ordering: " + (prefs.getBoolean("usageOrder", true) ? "on" : "off"),
            "Notification previews & replies",
            "Curtain wallpaper",
            "Default home app",
            "System settings");
    sheet(
        "Arlecchino settings",
        labels,
        n -> {
          switch (n) {
            case 0:
              chooseFavorites();
              break;
            case 1:
              createFolder();
              break;
            case 2:
              appearance();
              break;
            case 3:
              hiddenApps();
              break;
            case 4:
              prefs.edit().putBoolean("usageOrder", !prefs.getBoolean("usageOrder", true)).apply();
              rebuild();
              break;
            case 5:
              new AlertDialog.Builder(this)
                  .setTitle("Notification access")
                  .setMessage(
                      "Optional: show message previews and reply from Home. Text stays in memory on"
                          + " this phone. Enable notification access only if you want these"
                          + " features.")
                  .setPositiveButton(
                      "Android settings",
                      (d, w) ->
                          startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)))
                  .setNegativeButton("Cancel", null)
                  .show();
              break;
            case 6:
              MainActivity.applyWallpaper(this);
              break;
            case 7:
              chooseHome();
              break;
            case 8:
              startActivity(new Intent(Settings.ACTION_SETTINGS));
              break;
          }
        });
  }

  private void appearance() {
    String[] choices = {
      showIcons ? "Hide app icons" : "Show app icons",
      "App label size",
      "Dim wallpaper",
      "Restore original app names",
      "Icon pack"
    };
    sheet(
        "Arlecchino theme",
        Arrays.asList(choices),
        n -> {
          if (n == 0) {
            showIcons = !showIcons;
            prefs.edit().putBoolean("icons", showIcons).apply();
            rebuild();
          } else if (n == 1) {
            String[] sizes = {"Small", "Default", "Large"};
            new AlertDialog.Builder(this)
                .setTitle("App label size")
                .setSingleChoiceItems(
                    sizes,
                    prefs.getInt("textSize", 1),
                    (d, w) -> {
                      prefs.edit().putInt("textSize", w).apply();
                      adapter.notifyDataSetChanged();
                      d.dismiss();
                    })
                .show();
          } else if (n == 2) {
            boolean dim = !prefs.getBoolean("dim", false);
            prefs.edit().putBoolean("dim", dim).apply();
            setDim();
          } else if (n == 3) {
            SharedPreferences.Editor edit = prefs.edit();
            for (String key : prefs.getAll().keySet())
              if (key.startsWith("name:")) edit.remove(key);
            edit.apply();
            rebuild();
          } else chooseIconPack();
        });
  }

  private void chooseIconPack() {
    Map<String, String> installed = IconPack.installed(this);
    List<String> packages = new ArrayList<>();
    packages.add("@outline");
    packages.add("");
    packages.addAll(installed.keySet());
    List<String> names = new ArrayList<>();
    names.add("Arlecchino Outline (built in)");
    names.add("System icons");
    names.addAll(installed.values());
    sheet(
        "Icon pack",
        names,
        n -> {
          iconPackPackage = packages.get(n);
          prefs.edit().putString("iconPack", iconPackPackage).apply();
          iconPack = new IconPack(this, iconPackPackage);
          icons.evictAll();
          rebuild();
        });
  }

  private void setDim() {
    root.setBackground(
        new GradientDrawable(
            GradientDrawable.Orientation.LEFT_RIGHT,
            prefs.getBoolean("dim", false)
                ? new int[] {0xAF0B080D, 0x800B080D, 0x50000000}
                : new int[] {0x650B080D, 0x300B080D, 0x00000000}));
  }

  private void hiddenApps() {
    List<App> choices = new ArrayList<>(apps);
    Collator col = Collator.getInstance();
    choices.sort((a, b) -> col.compare(label(a), label(b)));
    String[] names = new String[choices.size()];
    boolean[] checked = new boolean[choices.size()];
    Set<String> selection = new HashSet<>(hidden);
    for (int i = 0; i < choices.size(); i++) {
      names[i] = label(choices.get(i));
      checked[i] = hidden.contains(choices.get(i).id);
    }
    new AlertDialog.Builder(this)
        .setTitle("Hidden apps")
        .setMultiChoiceItems(
            names,
            checked,
            (d, w, on) -> {
              if (on) selection.add(choices.get(w).id);
              else selection.remove(choices.get(w).id);
            })
        .setPositiveButton(
            "Save",
            (d, w) -> {
              hidden.clear();
              hidden.addAll(selection);
              prefs.edit().putStringSet("hidden", new HashSet<>(hidden)).apply();
              rebuild();
            })
        .setNegativeButton("Cancel", null)
        .show();
  }

  private void chooseHome() {
    try {
      if (Build.VERSION.SDK_INT >= 29) {
        RoleManager manager = getSystemService(RoleManager.class);
        if (manager != null
            && manager.isRoleAvailable(RoleManager.ROLE_HOME)
            && !manager.isRoleHeld(RoleManager.ROLE_HOME)) {
          startActivityForResult(manager.createRequestRoleIntent(RoleManager.ROLE_HOME), 10);
          return;
        }
      }
      startActivity(new Intent(Settings.ACTION_HOME_SETTINGS));
    } catch (ActivityNotFoundException e) {
      Toast.makeText(this, "Choose Arlecchino Home in Android default apps", Toast.LENGTH_LONG)
          .show();
    }
  }

  private void openClock() {
    try {
      startActivity(new Intent(android.provider.AlarmClock.ACTION_SHOW_ALARMS));
    } catch (ActivityNotFoundException ignored) {
    }
  }

  private void openCalendar() {
    try {
      startActivity(
          new Intent(
              Intent.ACTION_VIEW,
              Uri.parse("content://com.android.calendar/time/" + System.currentTimeMillis())));
    } catch (ActivityNotFoundException e) {
      Toast.makeText(this, date.getText(), Toast.LENGTH_SHORT).show();
    }
  }

  @Override
  protected void onNewIntent(Intent i) {
    super.onNewIntent(i);
    setIntent(i);
    home();
  }

  @Override
  public void onBackPressed() {
    home();
  }

  @Override
  protected void onDestroy() {
    dead = true;
    if (registered) unregisterReceiver(changes);
    worker.shutdownNow();
    ui.removeCallbacksAndMessages(null);
    icons.evictAll();
    super.onDestroy();
  }

  @Override
  public boolean dispatchTouchEvent(MotionEvent e) {
    if (e.getActionMasked() == MotionEvent.ACTION_DOWN) {
      downX = e.getX();
      downY = e.getY();
      intercepted = false;
      downRow = null;
      int rowPosition =
          list.pointToPosition((int) e.getX() - list.getLeft(), (int) e.getY() - list.getTop())
              - list.getHeaderViewsCount();
      if (rowPosition >= 0 && rowPosition < rows.size()) downRow = rows.get(rowPosition);
      edgeGesture = !searching && (downX < dp(20) || downX > root.getWidth() - dp(48));
      if (edgeGesture) {
        edgePointer = e.getPointerId(0);
        rail.touch(e.getY(), e.getActionMasked());
        return true;
      }
    }
    if (edgeGesture) {
      int action = e.getActionMasked(), pointer = e.findPointerIndex(edgePointer);
      boolean pointerLifted = action == MotionEvent.ACTION_POINTER_UP
          && e.getPointerId(e.getActionIndex()) == edgePointer;
      if (pointer < 0 || pointerLifted) {
        rail.touch(rail.rawY, MotionEvent.ACTION_CANCEL);
        edgeGesture = false;
        edgePointer = -1;
        // Consume the rest of this stream; it never began in the list.
        intercepted = true;
      } else if (action == MotionEvent.ACTION_MOVE || action == MotionEvent.ACTION_UP
          || action == MotionEvent.ACTION_CANCEL) {
        rail.touch(e.getY(pointer), action);
        if (action != MotionEvent.ACTION_MOVE) {
          edgeGesture = false;
          edgePointer = -1;
        }
      }
      return true;
    }
    if (!intercepted
        && downRow != null
        && e.getActionMasked() == MotionEvent.ACTION_MOVE
        && e.getX() - downX > dp(55)
        && Math.abs(e.getY() - downY) < dp(35)) {
      MotionEvent cancel = MotionEvent.obtain(e);
      cancel.setAction(MotionEvent.ACTION_CANCEL);
      super.dispatchTouchEvent(cancel);
      cancel.recycle();
      intercepted = true;
      if (downRow.app != null) appPopup(downRow.app);
      else if (downRow.folder != null) folderPopup(downRow.folder);
      return true;
    }
    if (!intercepted
        && !all
        && !searching
        && (downRow == null || (downRow.app == null && downRow.folder == null))
        && e.getActionMasked() == MotionEvent.ACTION_MOVE) {
      float dy = e.getY() - downY, dx = e.getX() - downX;
      if (dy < -dp(70) && Math.abs(dy) > Math.abs(dx) * 1.8 && downX > dp(120)) {
        MotionEvent cancel = MotionEvent.obtain(e);
        cancel.setAction(MotionEvent.ACTION_CANCEL);
        super.dispatchTouchEvent(cancel);
        cancel.recycle();
        intercepted = true;
        if (dy < 0) openSearch();
        return true;
      }
    }
    if (intercepted) {
      if (e.getActionMasked() == MotionEvent.ACTION_UP
          || e.getActionMasked() == MotionEvent.ACTION_CANCEL) intercepted = false;
      return true;
    }
    return super.dispatchTouchEvent(e);
  }

  private final class AppsAdapter extends BaseAdapter {
    public int getCount() {
      return rows.size();
    }

    public Object getItem(int p) {
      return rows.get(p);
    }

    public long getItemId(int p) {
      return p;
    }

    public int getViewTypeCount() {
      return 2;
    }

    public int getItemViewType(int p) {
      return rows.get(p).app != null || rows.get(p).folder != null ? 1 : 0;
    }

    public boolean isEnabled(int p) {
      return rows.get(p).app != null || rows.get(p).folder != null || rows.get(p).action != null;
    }

    public View getView(int p, View recycled, ViewGroup parent) {
      Row row = rows.get(p);
      if (row.app == null && row.folder == null) {
        TextView title =
            recycled instanceof TextView
                ? (TextView) recycled
                : text("", row.action == null ? 20 : 16, row.action == null ? INK : MUTED);
        title.setText(row.title);
        title.setTextSize(row.action == null ? 20 : 16);
        title.setGravity(Gravity.CENTER_VERTICAL);
        title.setPadding(0, dp(row.action == null ? 16 : 10), 0, dp(10));
        title.setMinHeight(dp(48));
        title.setTypeface(null, row.action == null ? Typeface.BOLD : Typeface.NORMAL);
        title.setClickable(false);
        return title;
      }
      LinearLayout line;
      if (recycled instanceof LinearLayout) line = (LinearLayout) recycled;
      else {
        line = new LinearLayout(HomeActivity.this);
        line.setGravity(Gravity.CENTER_VERTICAL);
        line.setPadding(0, dp(8), 0, dp(8));
        line.setMinimumHeight(dp(56));
        ImageView icon = new ImageView(HomeActivity.this);
        icon.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams(dp(40), dp(40));
        ip.rightMargin = dp(22);
        line.addView(icon, ip);
        LinearLayout words = new LinearLayout(HomeActivity.this);
        words.setOrientation(LinearLayout.VERTICAL);
        words.addView(text("", 16, INK));
        TextView preview = text("", 14, MUTED);
        preview.setPadding(0, dp(2), 0, 0);
        preview.setMaxLines(3);
        words.addView(preview);
        line.addView(words, new LinearLayout.LayoutParams(0, -2, 1));
      }
      ImageView icon = (ImageView) line.getChildAt(0);
      LinearLayout words = (LinearLayout) line.getChildAt(1);
      TextView name = (TextView) words.getChildAt(0), preview = (TextView) words.getChildAt(1);
      name.setTextSize(
          prefs.getInt("textSize", 1) == 0 ? 14 : prefs.getInt("textSize", 1) == 2 ? 19 : 16);
      name.setMaxLines(2);
      icon.setVisibility(showIcons ? View.VISIBLE : View.GONE);
      if (row.app == null) {
        name.setText(folderName(row.folder));
        line.setContentDescription(folderName(row.folder) + ", folder");
        icon.setImageDrawable(getDrawable(R.drawable.folder));
        preview.setVisibility(View.GONE);

        return line;
      }
      App a = row.app;
      name.setText(label(a));
      line.setContentDescription(label(a) + (favorites.contains(a.id) ? ", favorite" : ""));
      if (showIcons) {
        Drawable d = icons.get(a.id);
        if (d == null)
          try {
            d =
                iconPackPackage.equals("@outline")
                    ? new MinimalIcon(a.pkg, label(a))
                    : iconPack.icon(a.id, iconPackPackage);
            if (d == null) d = a.info.loadIcon(getPackageManager());
            icons.put(a.id, d);
          } catch (RuntimeException ignored) {
          }
        icon.setImageDrawable(d);
      }
      HomeNotifications.Preview n = HomeNotifications.latest(a.pkg);
      boolean recent = searching && search.getText().length() == 0;
      preview.setVisibility(n != null || recent ? View.VISIBLE : View.GONE);
      preview.setText(
          n != null
              ? n.text
              : recent
                  ? (prefs.getLong("last:" + a.id, 0) > 0 ? "Recently used" : "Recently installed")
                  : "");
      preview.setOnClickListener(n == null ? null : v -> openNotification(a, n));

      return line;
    }
  }

  private void showGestureGroup(int index) {
    if (index <= 0) {
      hideGestureGroup(false);
      return;
    }
    if (!all) mode(true, false);
    if (previewIndex == index && gestureLayer.getVisibility() == View.VISIBLE) return;
    previewTransition++;
    previewIndex = index;
    int first = rail.anchor(index), last = rows.size();
    for (int i = first + 1; i < rows.size(); i++)
      if (rows.get(i).app == null && rows.get(i).folder == null) {
        last = i;
        break;
      }
    LinearLayout group = new LinearLayout(this);
    group.setOrientation(LinearLayout.VERTICAL);
    for (int i = first; i < last; i++)
      group.addView(adapter.getView(i, null, group), new LinearLayout.LayoutParams(-1, -2));
    int width = Math.max(dp(100), root.getWidth() - dp(112));
    group.measure(
        View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
        View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
    int height;
    // The reference keeps each selected section's heading at one stable baseline.
    // Short sections must not sink lower than long sections as the finger moves.
    previewTop = Math.max(topInset + dp(24), root.getHeight() * .296f);
    height =
        Math.min(
            group.getMeasuredHeight(),
            Math.max(dp(80), root.getHeight() - bottomInset - dp(24) - Math.round(previewTop)));
    gestureScroll.removeAllViews();
    gestureScroll.addView(group);
    gestureScroll.scrollTo(0, 0);
    FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(width, height);
    params.leftMargin = dp(48);
    params.topMargin = Math.round(previewTop);
    gestureScroll.setLayoutParams(params);
    gestureLayer.animate().cancel();
    gestureLayer.setAlpha(1);
    gestureLayer.setVisibility(View.VISIBLE);
    list.animate().cancel();
    list.setAlpha(0);
  }

  private void hideGestureGroup(boolean settle) {
    if (gestureLayer.getVisibility() != View.VISIBLE) return;
    final int token = ++previewTransition;
    final int position =
        settle && all && previewIndex > 0
            ? rail.anchor(previewIndex) + list.getHeaderViewsCount()
            : -1;
    final int offset = Math.round(previewTop) - list.getPaddingTop();
    previewIndex = -1;
    Runnable finish =
        () -> {
          if (token != previewTransition || dead) return;
          list.animate().cancel();
          list.animate().alpha(1).setDuration(100).start();
          gestureLayer.animate().cancel();
          gestureLayer
              .animate()
              .alpha(0)
              .setDuration(100)
              .withEndAction(() -> {
                if (token == previewTransition && !rail.dragging)
                  gestureLayer.setVisibility(View.GONE);
              })
              .start();
        };
    if (position >= 0) {
      list.post(() -> {
        if (token != previewTransition || !all || searching) return;
        list.setSelectionFromTop(position, offset);
        list.getViewTreeObserver()
            .addOnPreDrawListener(
                new android.view.ViewTreeObserver.OnPreDrawListener() {
                  public boolean onPreDraw() {
                    if (list.getViewTreeObserver().isAlive())
                      list.getViewTreeObserver().removeOnPreDrawListener(this);
                    finish.run();
                    return true;
                  }
                });
        list.requestLayout();
      });
    } else finish.run();
  }

  private final class Alphabet extends View implements Choreographer.FrameCallback {
    final String letters = "★#ABCDEFGHIJKLMNOPQRSTUVWXYZ";
    final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    float strength, rawY, smoothY, trackStart, trackStep, animationScale = 1;
    boolean dragging, posted, tracking, moved;
    float downTrackY;
    int selected = -1, lastPosition = -1, lastOffset = Integer.MIN_VALUE;
    long lastFrame;

    Alphabet() {
      super(HomeActivity.this);
      paint.setShadowLayer(dp(2), 0, 0, Color.BLACK);
      setContentDescription("Alphabet index. Slide to jump to apps; star returns Home.");
    }

    float baseStart() {
      return getHeight() * .29f;
    }

    float baseStep() {
      return Math.max(1, (getHeight() - baseStart() - bottomInset - dp(36)) / letters.length());
    }

    private void schedule() {
      if (!posted) {
        posted = true;
        Choreographer.getInstance().postFrameCallback(this);
      }
    }

    protected void onDraw(Canvas c) {
      paint.setTextAlign(Paint.Align.CENTER);
      float begin = tracking ? trackStart : baseStart(), step = tracking ? trackStep : baseStep();
      float focus = tracking ? (smoothY - trackStart) / trackStep - .5f : selected;
      for (int i = 0; i < letters.length(); i++) {
        float distance = i - focus,
            wave = selected < 0 ? 0 : (float) Math.exp(-distance * distance / 45) * strength;
        paint.setTextSize(dp(16));
        paint.setColor(INK);
        float x = getWidth() - dp(30) - dp(80) * wave;
        float y = begin + step * (i + .5f);
        c.drawText(
            String.valueOf(letters.charAt(i)),
            x,
            y - (paint.ascent() + paint.descent()) / 2,
            paint);
      }
      if (selected >= 0 && strength > .01f) {
        float x = getWidth() - dp(30) - dp(115) * strength;
        float y = begin + step * (Math.max(0, Math.min(letters.length() - 1, focus)) + .5f);
        paint.setColor(0xFFE45776);
        paint.setStyle(Paint.Style.FILL);
        c.drawCircle(x, y, dp(22) * strength, paint);
        paint.clearShadowLayer();
        paint.setColor(INK);
        paint.setTextSize(dp(28));
        c.drawText(
            String.valueOf(letters.charAt(selected)),
            x,
            y - (paint.ascent() + paint.descent()) / 2,
            paint);
        paint.setShadowLayer(dp(2), 0, 0, Color.BLACK);
      }
    }

    private int anchor(int index) {
      for (int i = Math.max(1, index); i < letters.length(); i++) {
        Integer p = sections.get(String.valueOf(letters.charAt(i)));
        if (p != null) return p;
      }
      return Math.max(0, rows.size() - 1);
    }

    public void doFrame(long frameTime) {
      posted = false;
      if (dead || !isAttachedToWindow()) return;
      float dt =
          lastFrame == 0 ? 1f / 60f : Math.min(.05f, (frameTime - lastFrame) / 1_000_000_000f);
      lastFrame = frameTime;
      float blend = 1 - (float) Math.exp(-45 * dt / Math.max(.05f, animationScale));
      // Keep the pointer attached to the latest sampled touch. Only its
      // appearance eases; low-pass filtering the position adds finger lag.
      smoothY = dragging ? rawY : smoothY + (rawY - smoothY) * blend;
      float targetStrength = dragging ? 1 : 0;
      strength = android.animation.ValueAnimator.areAnimatorsEnabled()
          ? strength + (targetStrength - strength) * blend : targetStrength;
      float focus =
          Math.max(
              0,
              Math.min(
                  letters.length() - 1, (smoothY - trackStart) / Math.max(1, trackStep) - .5f));
      int index = Math.round(focus);
      if (dragging && index != selected) {
        selected = index;
        performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
        announceForAccessibility(String.valueOf(letters.charAt(index)));
      }
      if (dragging) {
        if (focus < .3f) {
          hideGestureGroup(false);
          if (all || searching) {
            hideKeyboard();
            search.setText("");
            mode(false, false);
          }
        } else showGestureGroup(Math.max(1, index));
      }
      invalidate();
      if (dragging || Math.abs(rawY - smoothY) > .25f || strength > .01f) schedule();
      else {
        tracking = false;
        selected = -1;
        lastFrame = 0;
        invalidate();
      }
    }

    void touch(float y, int action) {
      if (action == MotionEvent.ACTION_DOWN) {
        animationScale = Settings.Global.getFloat(
            getContentResolver(), Settings.Global.ANIMATOR_DURATION_SCALE, 1);
        trackStart = baseStart();
        trackStep = baseStep();
        rawY = smoothY = downTrackY = y;
        moved = false;
        dragging = tracking = true;
        lastPosition = -1;
        lastOffset = Integer.MIN_VALUE;
        lastFrame = 0;
        schedule();
      } else if (action == MotionEvent.ACTION_MOVE) {
        if (Math.abs(y - downTrackY) > dp(4)) moved = true;
        rawY = y;
        schedule();
      } else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
        // Commit the final touch before ending the gesture, even if UP arrives
        // before Choreographer has consumed the last MOVE.
        if (action == MotionEvent.ACTION_UP && dragging) {
          int index =
              Math.max(
                  0,
                  Math.min(
                      letters.length() - 1,
                      Math.round((y - trackStart) / Math.max(1, trackStep) - .5f)));
          if (index == 0) home();
          else showGestureGroup(index);
        }
        hideGestureGroup(action == MotionEvent.ACTION_UP);
        rawY = y;
        dragging = false;
        schedule();
      }
    }

    public boolean onTouchEvent(MotionEvent e) {
      // Activity dispatch owns the narrow edge hit target. The broad visual wave
      // must not steal list scrolling or app taps underneath it.
      return false;
    }

    protected void onDetachedFromWindow() {
      Choreographer.getInstance().removeFrameCallback(this);
      posted = false;
      super.onDetachedFromWindow();
    }
  }
}
