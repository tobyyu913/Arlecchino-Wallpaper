# Arlecchino Home for Android

An independently implemented, minimalist Android launcher with the Arlecchino curtain wallpaper. No Niagara code, assets, branding, purchase checks, or modified APKs are used. The Niagara-Issues ZIP supplied during development contains release documentation and issue templates, not launcher source.

## Install and setup

Current source builds **Android 1.18** with `./scripts/build-android.sh`, producing `outputs/Arlecchino-Home-Android-1.18.apk`. Published APKs are available in [GitHub releases](https://github.com/tobyyu913/Arlecchino-Wallpaper/releases) and may be older than this branch. Requires Android 9 or later.

1. Install the APK and open **Arlecchino Home**.
2. Tap **Choose favorites** and select your home-screen apps.
3. Tap **Make this your home screen** and confirm Arlecchino Home in Android's home-app picker.
4. Tap **Enable curtain wallpaper** if the wallpaper is not already active, then confirm it in the system wallpaper picker.

Version 1.18 updates the existing Arlecchino Wallpaper package using the same signing key. The old wallpaper-only APKs remain available in earlier releases. Installing this version does not automatically change the default launcher or grant notification access.

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

## Duo-style status column and Control Center, 1.16

Enabled by default on the launcher: a small clock above a circular battery ring, Wi-Fi arcs and cellular dots. Tap it for the glass Control Center; long-press it for launcher settings. On mobile data the center displays 5G, 4G, 3G or 2G from the reported radio/display information. Unknown generations show DATA, and disconnected radios show a dash instead of empty Wi-Fi arcs. Use **Arlecchino settings → Duo status bar** to restore the normal Android status bar.

This is an independent Android rendering based on Apple's published iPhone Duo layout. It lives inside the launcher window, with no root, accessibility service or draw-over-other-apps permission. The normal status bar is hidden while this launcher is visible; other apps keep their own normal system bars. Android can temporarily reveal its bar for a top-edge swipe or system alert. This does not replace the lock-screen status bar or system Quick Settings.

Battery, charging, battery-saver, Wi-Fi and cellular readings update while Home is visible. Network/Wi-Fi and Android 13+ basic cellular-state access use normal install-time permissions. No dangerous phone-state or location permission is requested, and no network requests are sent. Android's display-info callback handles 5G NSA branding over LTE where available. The active data SIM is selected when Home resumes. Unknown/unsupported readings stay unavailable rather than inventing a generation. Listeners and the panel stop when Home goes into the background.

The expanded layout uses connectivity/media tiles, circular utility controls and vertical brightness/volume sliders. It grows from the compact status indicator and collapses back with eased motion, respects disabled system animations, and dismisses with Back, the close control, an outside tap or an upward swipe near its bottom. Android 12+ supplies supported backdrop blur; launcher content also softens behind the panel. The grid scrolls when the display is too short.

| Control | Android behavior |
| --- | --- |
| Media volume | Changes real media-stream volume; supports touch and accessibility adjustment. |
| Brightness / rotation lock | Changes system settings after the user grants **Modify system settings** on first use. Brightness adjustment switches to manual brightness. |
| Flashlight | Toggles the rear flash through CameraManager; asks for Camera permission only when tapped. No camera preview or recording is created. Unsupported/in-use flash reports that state. |
| Play/pause, previous/next | Sends supported transport actions to the active media session using existing optional notification access; unavailable actions are dimmed. |
| Silent | Toggles normal/vibrate where Android permits it; otherwise opens Sound settings. |
| Wi-Fi / mobile data / Bluetooth / airplane mode | Opens Android's actual connectivity panel or settings because ordinary modern Android apps cannot directly toggle these radios. |
| Low Power | Uses optional Shizuku for stock Android Battery Saver. On HyperOS/MIUI it reads the native Xiaomi state and attempts the native provider. If Xiaomi signature permissions block that provider, it reports the restriction and offers an explicit handoff to Xiaomi’s own control; it does not report the stock Android flag as native success. |
| Automatic brightness | Switches adaptive/manual brightness directly after Modify system settings access. |
| Focus | Switches Arlecchino's Do Not Disturb mode after optional policy access. Android 15+ owns this as an app-specific mode; other active system modes remain independent. |
| Timer / calculator / camera | Opens the corresponding installed app or system action; missing handlers fall back to Settings. |

Version 1.17 reduces the grid width, softens its borders, removes nested radio outlines and settings-only filler tiles, and keeps the original monochrome symbols. Opening reveals the panel from the status corner at nearly full text scale; it no longer shrinks the whole menu into a tiny thumbnail. The Low Power pill has a live amber enabled state. UI-mode changes caused by Battery Saver are handled without recreating the launcher.

For the Low Power switch, install/start [Shizuku](https://shizuku.rikka.app/), then allow Arlecchino Home when Shizuku asks. No root is needed: the optional user service runs with the shell identity and exposes only a fixed Battery Saver operation, not arbitrary command strings. Access can be revoked in Shizuku. Start Shizuku again after a reboot. The switch keeps the menu open through permission/operation handoff and shows pending state until the relevant native state confirms the result. HyperOS can block its native switch even from shell/Shizuku; this cannot be fixed by granting ordinary Shizuku access. Version 1.17.1 corrects the previous false success when Android’s separate low_power flag was on but Xiaomi’s POWER_SAVE_MODE_OPEN remained off. While charging, unplug first if Android disallows Battery Saver.

The close button dismisses the panel; no nonfunctional power-off control is presented. Special permissions remain optional and are never enabled automatically on a physical phone.

## Android navigation gestures

The launcher reserves Android's reported side and bottom gesture regions for Back, Home and Recents, and moves its alphabet touch bands inward. System-canceled drags settle to a stable list; returning Home refreshes the insets. Android 13+ uses the platform Back callback, with legacy Back retained on older releases. Only the status bar is hidden; the system navigation bar/gesture handle remains under Android's control.

Enable gesture navigation in your device settings if the OS supports it for third-party launchers. Some Xiaomi firmware restricts third-party Home/Recents gestures; an ordinary launcher cannot bypass that restriction without privileged integration. See [Niagara's explanation of system gesture restrictions](https://help.niagaralauncher.app/article/7-gesture-navigation).

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

The script uses Android SDK tools directly without Gradle. Shizuku API/provider/aidl/shared 13.1.5 dependencies are fetched from Maven Central into the ignored build cache with pinned SHA-256 checksums from `android/dependencies.json`. Their MIT license is included in the source and APK. The launcher makes no runtime internet requests. Set `ANDROID_HOME` for another SDK path, `JAVA_HOME` for JDK 17, or `ANDROID_BUILD_TOOLS` / `ANDROID_PLATFORM_JAR` to override SDK versions.

A private signing key is generated in ignored `work/android-signing/` on the first build. Back that directory up privately; do not publish it. Compatible updates must use the same signing key. A build with a different key cannot update an existing installation without uninstalling it.

## Tests

```sh
mkdir -p work/android-tests
javac -d work/android-tests android/src/com/tobyyu/arlecchinowallpaper/WakeCycle.java android/tests/WakeCycleTest.java android/src/com/tobyyu/arlecchinowallpaper/AppIndex.java android/tests/AppIndexTest.java
java -cp work/android-tests com.tobyyu.arlecchinowallpaper.WakeCycleTest
java -cp work/android-tests com.tobyyu.arlecchinowallpaper.AppIndexTest
```

Run `./scripts/test-android-music.sh EMULATOR_SERIAL [ADB_PORT]` on a disposable emulator for real MediaSession callback/transport tests. It refuses physical devices because it grants notification access for synthetic test sessions. Pass `TransitionTests` as a third argument to check intermediate departure frames, synchronized fades, quick-release settling and canceling back to favorites. The music tests cover title/artist updates, play/pause, previous/next, resume, active-player changes and destroyed sessions. `NavigationTests` checks edge reservations, native Back, search dismissal, cancellation, Home and Recents. `DuoStatusTests` checks live battery data, expansion, native-bar hiding/restoration, background cleanup and returning Home. `BatterySaverTests`, with official Shizuku running on the disposable emulator and an unplugged battery fixture, checks real Battery Saver on/off, permission handoff and retaining the panel. `ControlCenterTests` exercises actual volume/brightness/rotation changes, media transport commands, native Back dismissal, system-panel handoff and cleanup. These suites are emulator-only; the status suite temporarily sets and then resets an emulator battery fixture.

Wake tests cover missed/duplicate/rapid wake events, decode cancellation, replaced surfaces, timeouts, and returning home. App-index tests cover sorting and search.

Physical Xiaomi testing verifies Home, search, favorite persistence, app launching, renaming/resetting labels, swipe-right app pop-ups, alphabet navigation, and pop-up folder management. Frame-timing tests compare repeated rail drags on the same device. See the reference checks for permission-gated test limits. The test left the user's original default launcher and notification-access settings unchanged. Prior wallpaper testing verified three normal wake cycles and recovery after five rapid screen-off/on cycles. APK signatures and alignment are checked by the build script.

## Import favorites

Open Arlecchino settings → Import favorites and select a JSON file containing an ordered array of installed package names or launcher components. For example: `["com.android.settings", "com.android.chrome"]`. A review dialog lists the apps in order before replacing favorites; unavailable entries are reported and duplicates are removed. Cancel or malformed files leave the current list unchanged. Imported favorites are also removed from the hidden-app list. No storage permission is needed: Android's file picker grants access only to the selected file. This is an Arlecchino JSON format, not a parser for Niagara's proprietary backup format.

## Minimal icons for uncovered apps

Version 1.9 includes 58 app-specific AI-generated outline assets covering 59 packages (the CloudDrive app and its web shortcut share an identity). They supplement an installed icon pack only when that pack has no matching icon. The built-in outline mode also uses them where available. System-icon mode retains original app icons.

The assets were generated with OpenAI's image-generation tool from original app-icon identity references, reviewed as monochrome masks and at small sizes, and bundled locally. Their alpha contours are rendered in the launcher's warm white ink with consistent bounds and aspect ratio. There is no AI request, API key, internet permission or generation delay on the phone. New, unrecognized packages still require an additional icon or icon-pack update.


## Performance, 1.13

Icon-pack parsing, image decoding and icon rasterization run on background workers. Recycled rows use tagged asynchronous requests so delayed results cannot replace another app's icon. The launcher keeps a bounded 192-icon cache and four recently visited gesture-section layouts, and caches alphabet grouping rather than repeating transliteration during sorting. Favorites departures reuse a half-resolution snapshot rather than allocating a full-display bitmap each time. Media health checks keep their existing session recovery but reuse unchanged artwork.

Transition instrumentation also checks that neighboring-section visits reuse views and repeated departures reuse their snapshot. The tests run on an emulator; no phone testing or installation is required.
