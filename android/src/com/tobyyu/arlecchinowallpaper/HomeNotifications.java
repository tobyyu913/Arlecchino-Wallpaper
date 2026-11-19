package com.tobyyu.arlecchinowallpaper;

import android.app.Notification;
import android.app.PendingIntent;
import android.content.Intent;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.Map;

/** Optional local notification previews. Contents stay in memory, never on disk. */
public final class HomeNotifications extends NotificationListenerService {
  static final String CHANGED = "com.tobyyu.arlecchinowallpaper.NOTIFICATIONS_CHANGED";

  static final class Preview {
    final String key, text, fullText;
    final PendingIntent action;
    final long time;
    final Notification.Action replyAction;

    Preview(
        String key, String text, String fullText, PendingIntent action, long time, Notification.Action replyAction) {
      this.key = key;
      this.text = text;
      this.fullText = fullText;
      this.action = action;
      this.time = time;
      this.replyAction = replyAction;
    }
  }

  private static WeakReference<HomeNotifications> connected = new WeakReference<>(null);

  static android.graphics.drawable.Drawable mediaArtwork(String packageName) {
    HomeNotifications service = connected.get();
    if (service == null) return null;
    try {
      for (StatusBarNotification item : service.getActiveNotifications()) {
        Notification n = item.getNotification();
        if (packageName.equals(item.getPackageName()) && n.extras.containsKey(Notification.EXTRA_MEDIA_SESSION) && n.getLargeIcon() != null)
          return n.getLargeIcon().loadDrawable(service);
      }
    } catch (RuntimeException ignored) {}
    return null;
  }

  static void dismiss(String key) {
    HomeNotifications service = connected.get();
    if (service != null) service.cancelNotification(key);
  }

  private static final Map<String, Map<String, Preview>> notifications = new HashMap<>();

  static synchronized Preview latest(String packageName) {
    Map<String, Preview> entries = notifications.get(packageName);
    Preview latest = null;
    if (entries != null)
      for (Preview entry : entries.values())
        if (latest == null || entry.time > latest.time) latest = entry;
    return latest;
  }

  private void notifyHome() {
    sendBroadcast(new Intent(CHANGED).setPackage(getPackageName()));
  }

  private static synchronized void clear() {
    notifications.clear();
  }

  @Override
  public void onListenerConnected() {
    connected = new WeakReference<>(this);
    clear();
    try {
      for (StatusBarNotification item : getActiveNotifications()) store(item);
    } catch (SecurityException ignored) {
    }
    notifyHome();
  }

  static String expandedText(Notification n) {
    StringBuilder result = new StringBuilder();
    CharSequence title = n.extras.getCharSequence(Notification.EXTRA_TITLE);
    if (title != null && title.length() > 0) result.append(title).append("\n\n");
    android.os.Parcelable[] bundles = n.extras.getParcelableArray(Notification.EXTRA_MESSAGES);
    java.util.List<Notification.MessagingStyle.Message> messages = bundles == null
        ? java.util.Collections.emptyList()
        : Notification.MessagingStyle.Message.getMessagesFromBundleArray(bundles);
    if (!messages.isEmpty()) {
      for (Notification.MessagingStyle.Message message : messages) {
        if (message.getText() == null) continue;
        if (message.getSenderPerson() != null && message.getSenderPerson().getName() != null)
          result.append(message.getSenderPerson().getName()).append(": ");
        result.append(message.getText()).append('\n');
      }
    } else {
      CharSequence body = n.extras.getCharSequence(Notification.EXTRA_BIG_TEXT);
      CharSequence[] lines = n.extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES);
      if (body != null && body.length() > 0) result.append(body);
      else if (lines != null && lines.length > 0) {
        for (CharSequence line : lines) if (line != null) result.append(line).append('\n');
      } else {
        body = n.extras.getCharSequence(Notification.EXTRA_TEXT);
        if (body != null) result.append(body);
      }
    }
    return result.toString().trim();
  }

  private static synchronized void store(StatusBarNotification item) {
    Notification n = item.getNotification();
    if ((n.flags & Notification.FLAG_GROUP_SUMMARY) != 0) return;
    CharSequence title = n.extras.getCharSequence(Notification.EXTRA_TITLE);
    CharSequence body = n.extras.getCharSequence(Notification.EXTRA_TEXT);
    if (title == null && body == null) return;
    String text = title == null ? "" : title.toString();
    if (body != null && body.length() > 0) text += (text.isEmpty() ? "" : " · ") + body.toString();
    String fullText = expandedText(n);
    Map<String, Preview> entries = notifications.get(item.getPackageName());
    if (entries == null) {
      entries = new HashMap<>();
      notifications.put(item.getPackageName(), entries);
    }
    Notification.Action reply = null;
    if (n.actions != null)
      for (Notification.Action action : n.actions)
        if (action.getRemoteInputs() != null && action.getRemoteInputs().length > 0) {
          reply = action;
          break;
        }
    entries.put(
        item.getKey(),
        new Preview(item.getKey(), text, fullText, n.contentIntent, item.getPostTime(), reply));
  }

  @Override
  public void onNotificationPosted(StatusBarNotification item) {
    store(item);
    notifyHome();
  }

  @Override
  public void onNotificationRemoved(StatusBarNotification item) {
    synchronized (HomeNotifications.class) {
      Map<String, Preview> entries = notifications.get(item.getPackageName());
      if (entries != null) {
        entries.remove(item.getKey());
        if (entries.isEmpty()) notifications.remove(item.getPackageName());
      }
    }
    notifyHome();
  }

  @Override
  public void onListenerDisconnected() {
    connected.clear();
    clear();
    notifyHome();
  }

  @Override
  public void onDestroy() {
    connected.clear();
    clear();
    super.onDestroy();
  }
}
