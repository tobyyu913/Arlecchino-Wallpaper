#!/bin/bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
SDK="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$HOME/Library/Android/sdk}}"
TOOLS="${ANDROID_BUILD_TOOLS:-$SDK/build-tools/34.0.0}"
PLATFORM="${ANDROID_PLATFORM_JAR:-$SDK/platforms/android-35/android.jar}"
BUILD="$ROOT/work/android-build"
KEYDIR="$ROOT/work/android-signing"
JDK="${JAVA_HOME:-$(/usr/libexec/java_home -v 17)}"
export JAVA_HOME="$JDK"
export PATH="$JDK/bin:$PATH"
mkdir -p "$BUILD/generated" "$BUILD/classes" "$BUILD/dex" "$KEYDIR" "$ROOT/outputs"
"$TOOLS/aapt2" compile --dir "$ROOT/android/res" -o "$BUILD/resources.zip"
"$TOOLS/aapt2" link -o "$BUILD/base.apk" -I "$PLATFORM" \
    --manifest "$ROOT/android/AndroidManifest.xml" -R "$BUILD/resources.zip" \
    --java "$BUILD/generated" --auto-add-overlay
python3 - "$ROOT" "$JDK" "$PLATFORM" "$TOOLS" <<'PY'
import pathlib, subprocess, sys
root,jdk,platform,tools=sys.argv[1:]
build=pathlib.Path(root)/'work/android-build'
source=list((pathlib.Path(root)/'android/src').rglob('*.java'))+list((build/'generated').rglob('*.java'))
subprocess.run([jdk+'/bin/javac','--release','8','-classpath',platform,'-d',str(build/'classes')]+[str(p) for p in source],check=True)
subprocess.run([tools+'/d8','--lib',platform,'--min-api','28','--output',str(build/'dex')]+[str(p) for p in (build/'classes').rglob('*.class')],check=True)
PY
cp "$BUILD/base.apk" "$BUILD/with-dex.apk"
zip -q -j "$BUILD/with-dex.apk" "$BUILD/dex/classes.dex"
"$TOOLS/zipalign" -f -p 4 "$BUILD/with-dex.apk" "$BUILD/aligned.apk"
if [ ! -f "$KEYDIR/release.jks" ]; then
    umask 077
    openssl rand -hex 32 > "$KEYDIR/password.txt"
    "$JDK/bin/keytool" -genkeypair -keystore "$KEYDIR/release.jks" \
        -storepass:file "$KEYDIR/password.txt" -keypass:file "$KEYDIR/password.txt" \
        -alias arlecchino -keyalg RSA -keysize 3072 -validity 10000 \
        -dname 'CN=Arlecchino Wallpaper' -storetype JKS
fi
APK="$ROOT/outputs/Arlecchino-Wallpaper-Android-1.1.apk"
"$TOOLS/apksigner" sign --ks "$KEYDIR/release.jks" --ks-key-alias arlecchino \
    --ks-pass "file:$KEYDIR/password.txt" \
    --out "$APK" "$BUILD/aligned.apk"
"$TOOLS/apksigner" verify --verbose "$APK"
"$TOOLS/zipalign" -c -p 4 "$APK"
echo "Built $APK"
