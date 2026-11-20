#!/usr/bin/env bash
# SPDX-License-Identifier: Apache-2.0
# Pinned API 3 stubs, manifest-only SDK framework and DEX compiler. No Android Studio/Gradle needed.
set -euo pipefail
ROOT=$(cd "$(dirname "$0")/.." && pwd)
mkdir -p "$ROOT/build/toolchain"
cd "$ROOT/build/toolchain"
fetch() {
    local name=$1 url=$2 sha=$3
    if [[ ! -f "$name" ]]; then
        curl --fail --location --retry 2 -o "$name.tmp" "$url"
        mv "$name.tmp" "$name"
    fi
    printf '%s  %s\n' "$sha" "$name" | sha256sum --check -
}
fetch android-1.5_r4.jar \
    https://repo.maven.apache.org/maven2/com/google/android/android/1.5_r4/android-1.5_r4.jar \
    f5792f7316f318c483c9ee35945ec9339c5545d63f07d15e60e8dd6ebd274473
# Manifest-only schema for screen sizes, tall displays and resizable activities.
# This SDK platform is never used as javac's classpath or bundled in the APK.
if [[ ! -f android-26.jar ]]; then
    fetch platform-26_r02.zip \
        https://dl.google.com/android/repository/platform-26_r02.zip \
        2aafa7d19c5e9c4b643ee6ade3d85ef89dc2f79e8383efdb9baf7fddad74b52a
    python3 - <<'PY'
import os
import shutil
import zipfile
with zipfile.ZipFile('platform-26_r02.zip') as archive:
    with archive.open('android-8.0.0/android.jar') as source, open('android-26.jar.tmp', 'wb') as target:
        shutil.copyfileobj(source, target)
os.replace('android-26.jar.tmp', 'android-26.jar')
PY
fi
printf '%s  %s\n' cdc1846376a14b0370cc63454a129606b4a52cc50ada75ef0d4cf956b1ad2daa android-26.jar | sha256sum --check -
fetch dx-1.7.jar \
    https://repo.maven.apache.org/maven2/com/google/android/tools/dx/1.7/dx-1.7.jar \
    923302e666d76e126e4cdf7129318532b258b1a66e435b92ccc29e91a60357bf
