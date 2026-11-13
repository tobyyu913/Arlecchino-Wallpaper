# Arlecchino Wallpaper for Android

A minimal native live wallpaper. The curtain opens once on each screen-on cycle, then holds the last open frame. There is no middle loop, closing animation, shortcut, account, or settings screen.

## Install

Download `Arlecchino-Wallpaper-Android-1.0.apk` from the [Android release](https://github.com/tobyyu913/Arlecchino-Wallpaper/releases/tag/android-v1.0), transfer it to your phone, and install it. Android may ask you to allow installation from the browser or file manager you use to open the APK.

Open **Arlecchino Wallpaper**, tap **Set wallpaper**, then confirm in Android's wallpaper picker. Choose home and lock screens if your device offers that option. The app requests no internet, storage, accessibility, or notification permissions.

Requires Android 9 (API 28) or newer and live-wallpaper support. Written against standard Android APIs; manufacturer-specific features are not required. Android and the device determine which screens can use the wallpaper. If another app covers it at wake, the opening waits until the wallpaper becomes visible.

## Playback

The three-second opening is bundled as a 30 fps animated WebP. Android's native decoder plays it exactly once. A separate, pixel-matched final frame is then drawn once, with no idle animation timer. Playback stops while the wallpaper is hidden or the screen is off. Returning to the home screen does not rearm playback; the next screen-off/on cycle does. Android may recreate the wallpaper process after reclaiming memory, in which case a fresh engine starts with an initial opening.

The character is cropped from the empty black left side of the desktop source, fit without stretching, and positioned at the right edge. Extra screen area remains black. Layout accounts for scrolling launcher surfaces and orientation changes.

## Build

Install JDK 17 and the Android SDK with platform 35 and build-tools 34.0.0, then run from the repository root:

```sh
./scripts/build-android.sh
```

The script uses SDK tools directly, without Gradle or third-party runtime dependencies. Set `ANDROID_HOME` if the SDK is not at `~/Library/Android/sdk`. Set `JAVA_HOME` for JDK 17 on other host platforms. `ANDROID_BUILD_TOOLS` and `ANDROID_PLATFORM_JAR` can override their default paths.

The signed APK is written to `outputs/Arlecchino-Wallpaper-Android-1.0.apk`. A local release signing key is generated under ignored `work/android-signing/`. Keep that directory privately backed up to sign compatible future updates. Do not commit or publish the keystore or its password file. Building on another machine with a new signing key produces an APK that cannot update an installation signed by the original key.

## Checks

```sh
mkdir -p work/android-tests
javac -d work/android-tests android/src/com/tobyyu/arlecchinowallpaper/WakeCycle.java android/tests/WakeCycleTest.java
java -cp work/android-tests com.tobyyu.arlecchinowallpaper.WakeCycleTest
```

The wake-cycle tests cover initial setup, screen-on/off, duplicate notifications, interruption, returning home, and deferred visibility. APK signature, alignment, and manifest were also checked. Rendering and real screen-on behavior have not yet been tested on a phone or emulator.
