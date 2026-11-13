#!/bin/bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
"$ROOT/scripts/build.sh"
APP="$ROOT/outputs/Arlecchino Wallpaper.app"
VERSION=$(/usr/libexec/PlistBuddy -c 'Print CFBundleShortVersionString' "$APP/Contents/Info.plist")
STAGE="$ROOT/work/dmg-$VERSION"
mkdir -p "$STAGE"
ditto "$APP" "$STAGE/Arlecchino Wallpaper.app"
ln -sfn /Applications "$STAGE/Applications"
cat > "$STAGE/Install.txt" <<'TEXT'
Arlecchino Wallpaper

Requires macOS 14 or later. Supports Apple silicon and Intel Macs.

1. Drag Arlecchino Wallpaper.app to Applications.
2. Eject this disk image and open the app from Applications.
3. Use the theatre-mask menu-bar icon to open Settings.
4. Enable Start at login if desired.

Close curtain: Control + Option + Command + Q
Open curtain: Control + Option + Command + O

This build is ad-hoc signed, not Developer ID signed or notarized.
If macOS blocks the downloaded app, review it in System Settings >
Privacy & Security and use Open Anyway only if you trust this release.

Source: https://github.com/tobyyu913/Arlecchino-Wallpaper
TEXT
hdiutil create -volname "Arlecchino Wallpaper" -srcfolder "$STAGE" \
    -ov -format UDZO -fs HFS+ "$ROOT/outputs/Arlecchino-Wallpaper-$VERSION.dmg"
ditto -c -k --sequesterRsrc --keepParent "$APP" "$ROOT/outputs/Arlecchino-Wallpaper-$VERSION-macOS.zip"
cd "$ROOT/outputs"
shasum -a 256 "Arlecchino-Wallpaper-$VERSION.dmg" "Arlecchino-Wallpaper-$VERSION-macOS.zip" > SHA256SUMS.txt
hdiutil verify "Arlecchino-Wallpaper-$VERSION.dmg"
echo "Packaged version $VERSION"
