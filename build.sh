#!/usr/bin/env bash
# Requires JDK 8 javac, java, aapt, jarsigner, keytool, Python 3; no Gradle/NDK.
set -euo pipefail
ROOT=$(cd "$(dirname "$0")" && pwd)
BUILD="$ROOT/build/apk"
ANDROID_JAR=${ANDROID_JAR:-"$ROOT/build/toolchain/android-1.5_r4.jar"}
DX_JAR=${DX_JAR:-"$ROOT/build/toolchain/dx-1.7.jar"}
JAVAC=${JAVAC:-javac}
JAVA=${JAVA:-java}
JARSIGNER=${JARSIGNER:-jarsigner}
KEYTOOL=${KEYTOOL:-keytool}
AAPT=${AAPT:-aapt}
[[ -f "$ANDROID_JAR" && -f "$DX_JAR" ]] || { echo 'Run tools/fetch_toolchain.sh first' >&2; exit 1; }
[[ -f "$ROOT/assets/model.bin" ]] || { echo 'Run tools/export_model.py first' >&2; exit 1; }
mkdir -p "$BUILD/classes" "$ROOT/dist"
# Do not leave stale classes in an incremental build.
find "$BUILD/classes" -type f -delete
"$JAVAC" -encoding UTF-8 -source 1.5 -target 1.5 -classpath "$ANDROID_JAR" \
    -d "$BUILD/classes" "$ROOT"/src/org/brainrot/legacy/*.java
"$JAVA" -cp "$DX_JAR" com.android.dx.command.Main --dex --output="$BUILD/classes.dex" "$BUILD/classes"
# Dalvik on Android 1.5 requires DEX 035, not a modern D8 default format.
python3 - "$BUILD/classes.dex" <<'PY'
import sys
with open(sys.argv[1], 'rb') as f:
    assert f.read(8) == b'dex\n035\x00', 'DEX format must be 035'
PY
"$AAPT" package -f -M "$ROOT/AndroidManifest.xml" -I "$ANDROID_JAR" \
    -A "$ROOT/assets" -0 bin -F "$BUILD/unsigned.apk"
(cd "$BUILD" && "$AAPT" add unsigned.apk classes.dex)
# Optional: use SDK zipalign if installed. Not required for streamed assets.
if command -v zipalign >/dev/null 2>&1; then
    zipalign -f 4 "$BUILD/unsigned.apk" "$BUILD/aligned.apk"
else
    cp "$BUILD/unsigned.apk" "$BUILD/aligned.apk"
fi
# Pre-2.3 Android needs SHA-1 JAR signatures. Scope this override to this process;
# do NOT change global JVM policy. This is a legacy debug APK, not a release key.
printf 'jdk.jar.disabledAlgorithms=\n' > "$BUILD/legacy-signing.security"
KEYSTORE="$BUILD/debug.keystore"
if [[ ! -f "$KEYSTORE" ]]; then
    "$KEYTOOL" -J-Djava.security.properties="$BUILD/legacy-signing.security" \
        -genkeypair -keystore "$KEYSTORE" -storetype JKS -storepass android \
        -keypass android -alias androiddebugkey -keyalg RSA -keysize 2048 -validity 10000 \
        -sigalg SHA1withRSA -dname 'CN=Android Debug,O=Brainrot,C=US'
fi
"$JARSIGNER" -J-Djava.security.properties="$BUILD/legacy-signing.security" \
    -keystore "$KEYSTORE" -storetype JKS -storepass android -keypass android \
    -sigalg SHA1withRSA -digestalg SHA1 -signedjar "$ROOT/dist/brainrot-legacy-debug.apk" \
    "$BUILD/aligned.apk" androiddebugkey
"$JARSIGNER" -J-Djava.security.properties="$BUILD/legacy-signing.security" \
    -verify "$ROOT/dist/brainrot-legacy-debug.apk"
"$AAPT" dump badging "$ROOT/dist/brainrot-legacy-debug.apk"
echo 'APK: dist/brainrot-legacy-debug.apk'
