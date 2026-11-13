#!/bin/bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
BUILD="$ROOT/work/build"
APP="$ROOT/outputs/Arlecchino Wallpaper.app"
mkdir -p "$BUILD" "$APP/Contents/MacOS" "$APP/Contents/Resources"
for ARCH in arm64 x86_64; do
    xcrun swiftc "$ROOT/Sources/DisplayLayout.swift" "$ROOT/Sources/WindowRegistry.swift" "$ROOT/Sources/main.swift" \
        -module-cache-path "$BUILD/ModuleCache" -target "$ARCH-apple-macosx14.0" \
        -framework AppKit -framework AVFoundation -framework SwiftUI -framework Carbon \
        -framework ServiceManagement -O -o "$BUILD/ArlecchinoWallpaper-$ARCH"
done
xcrun lipo -create "$BUILD/ArlecchinoWallpaper-arm64" "$BUILD/ArlecchinoWallpaper-x86_64" \
    -output "$APP/Contents/MacOS/ArlecchinoWallpaper"
cp "$ROOT/Resources/Info.plist" "$APP/Contents/Info.plist"
cp "$ROOT/Resources/"*.mp4 "$ROOT/Resources/black.png" "$APP/Contents/Resources/"
codesign --force --sign - "$APP"
codesign --verify --strict "$APP"
echo "Built $APP"
