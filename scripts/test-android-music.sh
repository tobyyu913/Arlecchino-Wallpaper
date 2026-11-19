#!/bin/bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
SDK="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$HOME/Library/Android/sdk}}"
SERIAL="${1:?Usage: test-android-music.sh EMULATOR_SERIAL [ADB_PORT]}"
PORT="${2:-5037}"
SUITE="${3:-MusicTests}"
case "$SUITE" in MusicTests|TransitionTests|NavigationTests|DuoStatusTests|ControlCenterTests|BatterySaverTests|OemBatterySaverTests|WallpaperPlaybackTests) ;; *) echo 'Unknown test suite' >&2; exit 1 ;; esac
ADB=("$SDK/platform-tools/adb" -P "$PORT" -s "$SERIAL")
if [[ "$("${ADB[@]}" shell getprop ro.kernel.qemu | tr -d '\r')" != 1 ]]; then
    echo 'Music instrumentation is restricted to an emulator; it grants notification access to test synthetic sessions.' >&2
    exit 1
fi
cd "$ROOT"
./scripts/build-android.sh
BUILD="$ROOT/work/music-android-tests"
mkdir -p "$BUILD/src/com/tobyyu/arlecchinowallpaper"
cp android/tests/instrumentation/*.java "$BUILD/src/com/tobyyu/arlecchinowallpaper/"
cp android/tests/instrumentation/AndroidManifest.xml "$BUILD/AndroidManifest.xml"
JDK="$(/usr/libexec/java_home -v 17)"
export JAVA_HOME="$JDK"
export PATH="$JDK/bin:$PATH"
python3 - "$ROOT" "$SDK" "$JDK" <<'PY'
import pathlib,subprocess,sys,os,shutil
root,sdk,jdk=map(pathlib.Path,sys.argv[1:]);b=root/'work/music-android-tests';tools=sdk/'build-tools/34.0.0';jar=sdk/'platforms/android-35/android.jar'
for name in ['classes','dex']:
    shutil.rmtree(b/name,ignore_errors=True);(b/name).mkdir()
def run(args): subprocess.run([str(a) for a in args],check=True)
run([tools/'aapt2','link','-o',b/'base.apk','-I',jar,'--manifest',b/'AndroidManifest.xml'])
run([jdk/'bin/javac','--release','8','-cp',str(jar)+os.pathsep+str(root/'work/android-build/classes')+os.pathsep+os.pathsep.join(map(str,(root/'work/android-dependencies').glob('*.jar'))),'-d',b/'classes']+list((b/'src').rglob('*.java')))
run([tools/'d8','--lib',jar,'--min-api','28','--output',b/'dex']+list((b/'classes').rglob('*.class')))
run(['zip','-q','-j',b/'base.apk',b/'dex/classes.dex'])
run([tools/'zipalign','-f','4',b/'base.apk',b/'aligned.apk'])
run([tools/'apksigner','sign','--ks',root/'work/android-signing/release.jks','--ks-key-alias','arlecchino','--ks-pass','file:'+str(root/'work/android-signing/password.txt'),'--out',b/'test.apk',b/'aligned.apk'])
PY
"${ADB[@]}" install --no-incremental -r outputs/Arlecchino-Home-Android-1.18.apk
"${ADB[@]}" install --no-incremental -r "$BUILD/test.apk"
if [[ "$SUITE" == NavigationTests ]]; then
    "${ADB[@]}" shell cmd overlay enable-exclusive --category com.android.internal.systemui.navbar.gestural
    "${ADB[@]}" shell cmd package set-home-activity com.tobyyu.arlecchinowallpaper/.HomeActivity
fi
if [[ "$SUITE" == MusicTests || "$SUITE" == ControlCenterTests ]]; then
    "${ADB[@]}" shell cmd notification allow_listener com.tobyyu.arlecchinowallpaper/.HomeNotifications
fi
if [[ "$SUITE" == ControlCenterTests ]]; then
    "${ADB[@]}" shell appops set com.tobyyu.arlecchinowallpaper WRITE_SETTINGS allow
fi
if [[ "$SUITE" == DuoStatusTests || "$SUITE" == BatterySaverTests || "$SUITE" == OemBatterySaverTests ]]; then
    "${ADB[@]}" shell cmd package set-home-activity com.tobyyu.arlecchinowallpaper/.HomeActivity
    "${ADB[@]}" shell cmd battery unplug
    "${ADB[@]}" shell cmd battery set level 55
fi
if [[ "$SUITE" == OemBatterySaverTests ]]; then
    "${ADB[@]}" shell settings put system POWER_SAVE_MODE_OPEN 0
    "${ADB[@]}" shell cmd power set-mode 1
fi
RESULT=$("${ADB[@]}" shell am instrument -w com.tobyyu.arlecchinowallpaper.tests/com.tobyyu.arlecchinowallpaper."$SUITE")
printf '%s\n' "$RESULT"
if [[ "$SUITE" == DuoStatusTests || "$SUITE" == BatterySaverTests || "$SUITE" == OemBatterySaverTests ]]; then "${ADB[@]}" shell cmd battery reset; fi
if [[ "$SUITE" == OemBatterySaverTests ]]; then
    "${ADB[@]}" shell settings delete system POWER_SAVE_MODE_OPEN
    "${ADB[@]}" shell cmd power set-mode 0
fi
[[ "$RESULT" == *'PASS: '* && "$RESULT" != *'FAIL:'* ]]
