# Arlecchino Home for Android

An independently implemented, minimalist Android launcher with the Arlecchino curtain wallpaper. No Niagara code, assets, branding, purchase checks, or modified APKs are used. The Niagara-Issues ZIP supplied during development contains release documentation and issue templates, not launcher source.

## Install and setup

Current source builds **Android 1.21** with `./scripts/build-android.sh`, producing `outputs/Arlecchino-Home-Android-1.21.apk`. Published APKs are available in [GitHub releases](https://github.com/tobyyu913/Arlecchino-Wallpaper/releases) and may be older than this branch. Requires Android 9 or later.

1. Install the APK and open **Arlecchino Home**.
2. Tap **Choose favorites** and select your home-screen apps.
3. Tap **Make this your home screen** and confirm Arlecchino Home in Android's home-app picker.
4. Tap **Enable curtain wallpaper** if the wallpaper is not already active, then confirm it in the system wallpaper picker.

Version 1.21 updates the existing Arlecchino Wallpaper package using the same signing key. The old wallpaper-only APKs remain available in earlier releases. Installing this version does not automatically change the default launcher or grant notification access.

## Home-screen workflow

- Favorites appear on Home; long-press to remove or move them up/down.
- Slide along the right alphabet rail to browse all apps at a letter. The star snaps to favorites while held; the gear below Z previews settings and opens them on release. Leaving favorites uses one synchronized slide-and-fade into the selected app group. The rebuilt list stays hidden until the departure completes, including on a quick release.
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

## Duo-style status column

Enabled by default on the launcher: a small clock above a circular battery ring, Wi-Fi arcs and cellular dots. Long-press it for launcher settings. On mobile data the center displays 5G, 4G, 3G or 2G from the reported radio/display information. Unknown generations show DATA, and disconnected radios show a dash instead of empty Wi-Fi arcs. Use **Arlecchino settings → Duo status bar** to restore the normal Android status bar.

This is an independent Android rendering based on Apple's published iPhone Duo layout. It lives inside the launcher window, with no root, accessibility service or draw-over-other-apps permission. The normal status bar is hidden while this launcher is visible; other apps keep their own normal system bars. Android can temporarily reveal its bar for a top-edge swipe or system alert. This does not replace the lock-screen status bar or system Quick Settings.

Battery, charging, battery-saver, Wi-Fi and cellular readings update while Home is visible. Network/Wi-Fi and Android 13+ basic cellular-state access use normal install-time permissions. No dangerous phone-state or location permission is requested, and no network requests are sent. Android's display-info callback handles 5G NSA branding over LTE where available. The active data SIM is selected when Home resumes. Unknown/unsupported readings stay unavailable rather than inventing a generation. Listeners stop when Home goes into the background.

The status column is read-only. The expanded control panel and Shizuku integration were removed in version 1.19. Long-press the status column to open launcher settings.

## Android navigation gestures

The launcher reserves Android's reported side and bottom gesture regions for Back, Home and Recents, and moves its alphabet touch bands inward. System-canceled drags settle to a stable list; returning Home refreshes the insets. Android 13+ uses the platform Back callback, with legacy Back retained on older releases. Only the status bar is hidden; the system navigation bar/gesture handle remains under Android's control.

Enable gesture navigation in your device settings if the OS supports it for third-party launchers. Some Xiaomi firmware restricts third-party Home/Recents gestures; an ordinary launcher cannot bypass that restriction without privileged integration. See [Niagara's explanation of system gesture restrictions](https://help.niagaralauncher.app/article/7-gesture-navigation).

## Notification previews

Tap notification text under an app to expand the full text directly in its list row, including on favorites. The apps below move down; there is no separate reader popup. Expanded text, conversation messages and inbox lines supplied by the app scroll with the favorites list. Tap **Collapse** or the text again to fold it back. Expanding or collapsing never opens another app. Use the inline **Open [app]** button to follow the notification, **Reply** when supported, or **Dismiss** to remove it. Expired notification links report that they are unavailable rather than launching an unrelated app screen.

## Music on favorites

The favorites view uses a transparent, artwork-led media row rather than a permanent card. The row appears only for an active playing, buffering or paused media session with a title, and gently collapses when playback stops or the session disappears. Artwork is supplied by the session or its media notification; an original player icon is the fallback. It keeps the whitespace above the clock and pushes favorites down.

The layout was checked against Niagara's built-in player on the same Xiaomi phone: 88 dp artwork at the left, title and artist alongside it, and previous/play-pause/next underneath with a larger outlined play button. The earlier heading, background, idle text and progress bar have been removed. Tapping the artwork or track opens the session's player screen when supplied by the media app.

Use **Arlecchino settings → Music player** to show/hide the adaptive row or open notification-access settings. Access uses the same optional listener as notification previews and is never granted automatically on your phone. No storage, audio recording or internet permission is added. Track details and artwork remain in memory. Controls follow each session's supported actions, and listeners reconnect on returning Home.

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

The script uses Android SDK tools directly without Gradle. The launcher makes no runtime internet requests. Set `ANDROID_HOME` for another SDK path, `JAVA_HOME` for JDK 17, or `ANDROID_BUILD_TOOLS` / `ANDROID_PLATFORM_JAR` to override SDK versions.

A private signing key is generated in ignored `work/android-signing/` on the first build. Back that directory up privately; do not publish it. Compatible updates must use the same signing key. A build with a different key cannot update an existing installation without uninstalling it.

## Tests

```sh
mkdir -p work/android-tests
javac -d work/android-tests android/src/com/tobyyu/arlecchinowallpaper/WakeCycle.java android/tests/WakeCycleTest.java android/src/com/tobyyu/arlecchinowallpaper/AppIndex.java android/tests/AppIndexTest.java
java -cp work/android-tests com.tobyyu.arlecchinowallpaper.WakeCycleTest
java -cp work/android-tests com.tobyyu.arlecchinowallpaper.AppIndexTest
```

Run `./scripts/test-android-music.sh EMULATOR_SERIAL [ADB_PORT]` on a disposable emulator for real MediaSession callback/transport tests. It refuses physical devices because it grants notification access for synthetic test sessions. Pass `TransitionTests` as a third argument to check intermediate departure frames, synchronized fades, quick-release settling and canceling back to favorites. The music tests cover title/artist updates, play/pause, previous/next, resume, active-player changes and destroyed sessions. `NavigationTests` checks edge reservations, native Back, search dismissal, cancellation, Home and Recents. `DuoStatusTests` checks live battery data, read-only status, native-bar hiding/restoration, background cleanup and returning Home. These suites are emulator-only; the status suite temporarily sets and then resets an emulator battery fixture.
