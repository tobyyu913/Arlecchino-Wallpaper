# Arlecchino Home for Android

An independently implemented, minimalist Android launcher with the Arlecchino curtain wallpaper. No Niagara code, assets, branding, purchase checks, or modified APKs are used. The Niagara-Issues ZIP supplied during development contains release documentation and issue templates, not launcher source.

## Install and setup

Download `Arlecchino-Home-Android-1.6.apk` from the [Android launcher release](https://github.com/tobyyu913/Arlecchino-Wallpaper/releases/tag/android-v1.6). Requires Android 9 or later.

1. Install the APK and open **Arlecchino Home**.
2. Tap **Choose favorites** and select your home-screen apps.
3. Tap **Make this your home screen** and confirm Arlecchino Home in Android's home-app picker.
4. Tap **Enable curtain wallpaper** if the wallpaper is not already active, then confirm it in the system wallpaper picker.

Version 1.6 updates the existing Arlecchino Wallpaper package using the same signing key. The old wallpaper-only APKs remain available in earlier releases. Installing this version does not automatically change the default launcher or grant notification access.

## Home-screen workflow

- Favorites appear on Home; long-press to remove or move them up/down.
- Slide along the right alphabet rail to browse all apps at a letter.
- Drag either screen edge to scrub the alphabet. The wave updates on display frames, with one stable track per gesture. During dragging, a filled crimson letter bubble and the selected app group float over a dimmed wallpaper; releasing transitions into the full app list. Tap the star, press Home, or press Back to return to favorites.
- Use the Home search box or swipe up to search by app name, package, or phonetic spelling. Search starts with recent apps. Chinese phonetic grouping uses Android ICU on Android 10+; Android 9 falls back to character grouping.
- Tap an app to launch it. Long-press any app for favorite and app-info actions.
- Tap the clock to open alarms.
- Long-press the clock or use Arlecchino settings at the end of the app list for setup and appearance controls.
- Swipe right on an app for its pop-up. Long-press for favorite, rename, hide, info and uninstall actions.
- Create pop-up folders from Edit favorites or settings.
- Arlecchino Outline is a built-in original minimalist icon style. Theme controls also support system icons, standard separately installed icon packs, dimming, label sizes, and resetting renamed app labels.
- App installation/removal and clock/date changes refresh the launcher.

This is an independent implementation of the free-version workflow, with additional Arlecchino styling. See [reference checks](REFERENCE-CHECKS.md) for observed surfaces, matching behavior and test limits. It does not claim exhaustive or pixel-for-pixel Niagara parity. Pro-only widget/calendar/weather integrations and work-profile support are not implemented.

## Optional notification previews

Use **Arlecchino settings → Notification previews & replies** to read the explanation and open Android's notification-access settings. Access is off until the user explicitly enables it. With access, the latest available notification appears beneath its app; tapping it opens the notification action. App pop-ups can dismiss it or invoke an available direct-reply action. No message is sent until the user explicitly submits a reply.

Preview contents stay in process memory, are cleared when the listener disconnects, and are not written to disk or sent anywhere. This permission can expose message contents, so it is optional. The app has no internet permission. Notification access was not enabled automatically during testing, and real notification preview rendering has not yet been tested.

## Wallpaper

The three-second curtain opening plays once each time the screen turns on and the wallpaper becomes visible, then holds the open frame. No middle loop or close action is added on Android. Surface/visibility transitions, display-state notifications, and power state are reconciled; deadlines recover stalled decoding or completion callbacks. Returning from an app does not normally restart a successfully presented opening. Android may recreate the whole wallpaper process, which starts with an initial opening.

The launcher displays the system wallpaper underneath its transparent layout. Android controls lock-screen placement. The bundled animation is a portrait-framed 30 fps animated WebP decoded by Android, with a pixel-matched final PNG. There is no continuous idle animation; a visible-only health check runs while interactive and stops while hidden or asleep.

## Build

Install JDK 17, Android SDK platform 35, and build-tools 34.0.0, then run:

```sh
./scripts/build-android.sh
```

The script uses Android SDK tools directly without Gradle or third-party runtime libraries. Set `ANDROID_HOME` for another SDK path, `JAVA_HOME` for JDK 17, or `ANDROID_BUILD_TOOLS` / `ANDROID_PLATFORM_JAR` to override SDK versions.

A private signing key is generated in ignored `work/android-signing/` on the first build. Back that directory up privately; do not publish it. Compatible updates must use the same signing key. A build with a different key cannot update an existing installation without uninstalling it.

## Tests

```sh
mkdir -p work/android-tests
javac -d work/android-tests android/src/com/tobyyu/arlecchinowallpaper/WakeCycle.java android/tests/WakeCycleTest.java android/src/com/tobyyu/arlecchinowallpaper/AppIndex.java android/tests/AppIndexTest.java
java -cp work/android-tests com.tobyyu.arlecchinowallpaper.WakeCycleTest
java -cp work/android-tests com.tobyyu.arlecchinowallpaper.AppIndexTest
```

Wake tests cover missed/duplicate/rapid wake events, decode cancellation, replaced surfaces, timeouts, and returning home. App-index tests cover sorting and search.

Physical Xiaomi testing verifies Home, search, favorite persistence, app launching, renaming/resetting labels, swipe-right app pop-ups, alphabet navigation, and pop-up folder management. Frame-timing tests compare repeated rail drags on the same device. See the reference checks for permission-gated test limits. The test left the user's original default launcher and notification-access settings unchanged. Prior wallpaper testing verified three normal wake cycles and recovery after five rapid screen-off/on cycles. APK signatures and alignment are checked by the build script.
