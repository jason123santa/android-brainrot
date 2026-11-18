#!/usr/bin/env bash
# SPDX-License-Identifier: Apache-2.0
# Small, pinned legacy API stubs and DEX compiler. No Android Studio/Gradle needed.
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
fetch dx-1.7.jar \
    https://repo.maven.apache.org/maven2/com/google/android/tools/dx/1.7/dx-1.7.jar \
    923302e666d76e126e4cdf7129318532b258b1a66e435b92ccc29e91a60357bf
