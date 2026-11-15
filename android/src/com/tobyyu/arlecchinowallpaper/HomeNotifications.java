package com.tobyyu.arlecchinowallpaper;

import android.app.Notification;
import android.app.PendingIntent;
import android.content.Intent;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import java.util.HashMap;
import java.util.Map;

/** Optional local notification previews. Contents stay in memory, never on disk. */
public final class HomeNotifications extends NotificationListenerService {
    static final String CHANGED = "com.tobyyu.arlecchinowallpaper.NOTIFICATIONS_CHANGED";
    static final class Preview {
        final String text; final PendingIntent action; final long time;
        Preview(String text, PendingIntent action, long time) { this.text = text; this.action = action; this.time = time; }
    }
    private static final Map<String, Map<String, Preview>> notifications = new HashMap<>();
    static synchronized Preview latest(String packageName) {
        Map<String, Preview> entries = notifications.get(packageName);
        Preview latest = null;
        if (entries != null) for (Preview entry : entries.values()) if (latest == null || entry.time > latest.time) latest = entry;
        return latest;
    }
    private void notifyHome() { sendBroadcast(new Intent(CHANGED).setPackage(getPackageName())); }
    private synchronized static void clear() { notifications.clear(); }
    @Override public void onListenerConnected() {
        clear();
        try { for (StatusBarNotification item : getActiveNotifications()) store(item); }
        catch (SecurityException ignored) { }
        notifyHome();
    }
    private static synchronized void store(StatusBarNotification item) {
        Notification n = item.getNotification();
        if ((n.flags & Notification.FLAG_GROUP_SUMMARY) != 0 || n.contentIntent == null) return;
        CharSequence title = n.extras.getCharSequence(Notification.EXTRA_TITLE);
        CharSequence body = n.extras.getCharSequence(Notification.EXTRA_TEXT);
        if (title == null && body == null) return;
        String text = title == null ? "" : title.toString();
        if (body != null && body.length() > 0) text += (text.isEmpty() ? "" : " · ") + body.toString();
        if (text.length() > 240) text = text.substring(0, 240);
        Map<String, Preview> entries = notifications.get(item.getPackageName());
        if (entries == null) { entries = new HashMap<>(); notifications.put(item.getPackageName(), entries); }
        entries.put(item.getKey(), new Preview(text, n.contentIntent, item.getPostTime()));
    }
    @Override public void onNotificationPosted(StatusBarNotification item) { store(item); notifyHome(); }
    @Override public void onNotificationRemoved(StatusBarNotification item) {
        synchronized (HomeNotifications.class) {
            Map<String, Preview> entries = notifications.get(item.getPackageName());
            if (entries != null) { entries.remove(item.getKey()); if (entries.isEmpty()) notifications.remove(item.getPackageName()); }
        }
        notifyHome();
    }
    @Override public void onListenerDisconnected() { clear(); notifyHome(); }
}
