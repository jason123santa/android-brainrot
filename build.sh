#!/usr/bin/env bash
# SPDX-License-Identifier: Apache-2.0
# Requires JDK 8 javac, java, aapt, jarsigner, keytool, Python 3, curl and sha256sum.
# Downloads pinned API 3 stubs, dx and a manifest-only framework; no Gradle/NDK or HF checkpoint.
set -euo pipefail
ROOT=$(cd "$(dirname "$0")" && pwd)
BUILD="$ROOT/build/apk"
ANDROID_JAR=${ANDROID_JAR:-"$ROOT/build/toolchain/android-1.5_r4.jar"}
# Only AAPT sees this newer framework; javac must continue to use API 3 stubs.
MANIFEST_ANDROID_JAR=${MANIFEST_ANDROID_JAR:-"$ROOT/build/toolchain/android-26.jar"}
DX_JAR=${DX_JAR:-"$ROOT/build/toolchain/dx-1.7.jar"}
JAVAC=${JAVAC:-javac}
JAVA=${JAVA:-java}
JARSIGNER=${JARSIGNER:-jarsigner}
KEYTOOL=${KEYTOOL:-keytool}
AAPT=${AAPT:-aapt}
for tool in "$JAVAC" "$JAVA" "$JARSIGNER" "$KEYTOOL" "$AAPT" python3 sha256sum; do
    command -v "$tool" >/dev/null 2>&1 || { echo "Missing required build tool: $tool" >&2; exit 1; }
done
# Check cached default downloads as well as fetching missing ones.
if [[ "$ANDROID_JAR" == "$ROOT/build/toolchain/android-1.5_r4.jar" ||
      "$MANIFEST_ANDROID_JAR" == "$ROOT/build/toolchain/android-26.jar" ||
      "$DX_JAR" == "$ROOT/build/toolchain/dx-1.7.jar" ]]; then
    "$ROOT/tools/fetch_toolchain.sh"
fi
[[ -f "$ANDROID_JAR" && -f "$MANIFEST_ANDROID_JAR" && -f "$DX_JAR" ]] || { echo 'Configured Android, manifest or dx JAR is missing' >&2; exit 1; }
[[ -f "$ROOT/assets/model.bin" ]] || { echo 'Bundled assets/model.bin is missing; restore it from Git or export a compatible checkpoint' >&2; exit 1; }
(cd "$ROOT/assets" && sha256sum --check model.bin.sha256)
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
"$AAPT" package -f -M "$ROOT/AndroidManifest.xml" -I "$MANIFEST_ANDROID_JAR" \
    -S "$ROOT/res" -A "$ROOT/assets" -0 bin -F "$BUILD/unsigned.apk"
# Keep readable licenses, attribution and modification notices with the model/APK.
mkdir -p "$BUILD/assets/legal/licenses"
for document in LICENSE NOTICE MODEL_LICENSE.md THIRD_PARTY_NOTICES.md; do
    cp "$ROOT/$document" "$BUILD/assets/legal/$document"
done
cp "$ROOT/docs/upstream-licenses.json" "$BUILD/assets/legal/upstream-licenses.json"
cp "$ROOT/licenses/OpenAI-GPT2-LICENSE.txt" "$BUILD/assets/legal/licenses/OpenAI-GPT2-LICENSE.txt"
(cd "$BUILD" && "$AAPT" add unsigned.apk classes.dex \
    assets/legal/LICENSE assets/legal/NOTICE assets/legal/MODEL_LICENSE.md \
    assets/legal/THIRD_PARTY_NOTICES.md assets/legal/upstream-licenses.json \
    assets/legal/licenses/OpenAI-GPT2-LICENSE.txt)
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
