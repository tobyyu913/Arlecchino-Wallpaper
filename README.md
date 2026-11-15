# Arlecchino Wallpaper

Native Arlecchino curtain wallpapers for macOS and Android.

**Android:** [download Arlecchino Home](https://github.com/tobyyu913/Arlecchino-Wallpaper/releases/tag/android-v1.7) or read the [Android instructions](android/README.md). The Android app now includes an original favorites-first launcher with a frame-driven alphabet wave, persistent search, bundled outline icons, app pop-ups/folders, and optional notification previews/replies, over the curtain wallpaper. The curtain opens once on screen-on, then holds the open frame; it has no middle loop or closing action. Earlier wallpaper-only builds remain in previous releases.

The macOS version is a small native menu-bar app dedicated to the Arlecchino curtain animation.

The curtain opens once on launch or your chosen wake/unlock event. Face and hair movement continues in a forward/reverse loop that excludes the original video's closing frames. A global shortcut closes the curtain and leaves it shut.

## Requirements

- macOS 14 or later.
- Apple silicon or Intel Mac. The build produces a universal executable.
- Xcode command-line tools to build from source. The installed app needs no development tools.

## Download

Download the **DMG** or zipped **.app** from the [latest release](https://github.com/tobyyu913/Arlecchino-Wallpaper/releases/latest). The DMG contains an Applications shortcut: drag the app onto it, eject the image, then open the app from Applications.

Release builds are ad-hoc signed and are not Developer ID signed or notarized. If macOS blocks the downloaded app, review the app in **System Settings → Privacy & Security**; use **Open Anyway** only if you trust the release. SHA-256 checksums accompany each release.

## Build and install

```sh
./scripts/build.sh
```

The result is `outputs/Arlecchino Wallpaper.app`. Copy it to Applications and open it. Settings are available from its theatre-mask menu-bar icon. Enable **Start at login** after placing the app in its final location.

The app is locally ad-hoc signed. This repository does not include a Developer ID notarization workflow.

## Controls and settings

- **Control–Option–Command–Q:** close the curtain. Settings also offers C or Space as the final key.
- **Control–Option–Command–O:** open or replay the curtain.
- **Open the curtain:** wake and unlock, unlock only, wake only, or manually.
- **Screen layout:** preserve the full character at the right edge (default), fit the whole video, or fill the display with cropping.
- **Lock-screen background:** black or the existing Mac wallpaper. Black mode uses a matching, display-sized still of Arlecchino beneath the open animation so fullscreen-to-desktop Space transitions do not reveal an empty black desktop. It switches the backing to black when the curtain closes or the session locks, and restores the previous wallpaper on quit. Choosing Keep Mac wallpaper preserves your original system wallpaper instead. macOS controls the actual secure lock screen; its clock, authentication UI, screen saver, and pre-login/FileVault screen are not replaced by this app.

When the session locks, video playback pauses and its desktop layer becomes black. Wake-triggered openings are deferred until unlock while the session is locked. The configured opening behavior then resumes.

## Display support

Each connected display gets its own desktop window with a synchronized player. Windows follow the full screen rectangle, including the areas behind the Dock and menu bar, on all desktop Spaces. Connecting a monitor, changing resolution, or rotating a display updates existing windows in place without restarting playback. Windows are only created or removed when a display is actually added or disconnected; redundant screen notifications preserve the same video layers. Layout uses logical points and updates its backing scale for Retina displays.

The default layout scales the video to the display height and anchors its right edge to the screen. Wide monitors gain black space on the left; narrow monitors crop the video's empty left margin. This preserves the character on standard MacBook, 16:9, ultrawide, and portrait displays. Fit mode preserves the entire composition for unusual display shapes. Fill mode intentionally permits cropping.

Bundled video assets are optimized to 1920 × 1082 at 60 fps and scale to the connected display; they are not native 4K renders.

## Package a release

```sh
./scripts/package.sh
```

This builds both architectures and generates a compressed DMG, a zipped app, and `SHA256SUMS.txt` in `outputs/`.

## Verification

```sh
mkdir -p work/tests
xcrun swiftc Sources/DisplayLayout.swift Tests/main.swift \
  -module-cache-path work/build/ModuleCache -o work/tests/display-layout
work/tests/display-layout
xcrun swiftc Sources/WindowRegistry.swift Tests/WindowRegistry/main.swift \
  -module-cache-path work/build/ModuleCache -o work/tests/window-registry
work/tests/window-registry
xcrun swiftc Sources/DisplayLayout.swift Sources/DesktopBacking.swift Tests/DesktopBacking/main.swift \
  -module-cache-path work/build/ModuleCache -o work/tests/desktop-backing
work/tests/desktop-backing
```

Geometry checks cover 12 display sizes, three layout modes, negative/offset screen origins, character visibility, and aspect-ratio preservation. Window lifecycle checks verify that 100 redundant display events preserve the same window instances and cover monitor hot-plug, reordering, and removal. Physical display hot-plug and secure lock-screen behavior need testing on the target Mac.

An optional `--self-test` application argument exercises real playback through opening, a full middle-loop wrap, and closing, then writes a report to `/tmp/curtain-test.txt` and exits. It does not change wallpaper settings.

## Repository contents

- `Sources/`: native AppKit, AVFoundation, SwiftUI, and Carbon shortcut code.
- `Resources/`: opening, seamless middle, closing, black image, and app metadata.
- `Tests/`: display-layout checks.
- `scripts/build.sh`: universal build and local signing.
- `scripts/package.sh`: DMG, zipped app, and checksum packaging.

The historical bundle identifier is retained so existing settings survive the rename. Video and character artwork remain the property of their respective rights holders; their inclusion does not grant redistribution rights.
