#!/usr/bin/env bash
# Builds build/local-dictation.apk with plain SDK tools (no Gradle).
# Usage: ./build.sh            -> build
#        ./build.sh test       -> run the TextPost, PhraseTracker and Languages tests (plain JDK, no Android SDK needed)
#        ./build.sh install    -> build + adb install -r
#        ./build.sh trace      -> development trace tool (debug/), separate package, + adb install
set -euo pipefail
cd "$(dirname "$0")"

SDK="${ANDROID_HOME:-$HOME/Android/Sdk}"
BT="$SDK/build-tools/${BUILD_TOOLS_VERSION:-35.0.0}"
PLATFORM="$SDK/platforms/android-35/android.jar"
if [ -n "${JAVA_HOME:-}" ]; then JBIN="$JAVA_HOME/bin/"; else JBIN=""; fi
JAVAC="${JBIN}javac"
KEYTOOL="${JBIN}keytool"

if [ "${1:-}" = "test" ]; then
    rm -rf build/test && mkdir -p build/test
    "$JAVAC" -encoding UTF-8 -d build/test \
        src/com/bvs/localdictation/TextPost.java src/com/bvs/localdictation/Languages.java \
        src/com/bvs/localdictation/PhraseTracker.java test/com/bvs/localdictation/*.java
    "${JBIN}java" -cp build/test com.bvs.localdictation.TextPostTest
    "${JBIN}java" -cp build/test com.bvs.localdictation.PhraseTrackerTest
    exec "${JBIN}java" -cp build/test com.bvs.localdictation.LanguagesTest
fi

rm -rf build && mkdir -p build/gen build/classes build/dex

MANIFEST=AndroidManifest.xml; SRC=src; RENAME=(); OUT=local-dictation
if [ "${1:-}" = "trace" ]; then
    MANIFEST=debug/AndroidManifest.xml; SRC="src debug/src"; OUT=local-dictation-trace
    RENAME=(--rename-manifest-package com.bvs.localdictation.trace)
fi

# 1. resources + manifest
"$BT/aapt2" compile --dir res -o build/res.zip
"$BT/aapt2" link -o build/base.apk -I "$PLATFORM" \
    --manifest "$MANIFEST" --java build/gen "${RENAME[@]}" \
    --min-sdk-version 31 --target-sdk-version 35 \
    --version-code 2 --version-name 0.2.0 \
    build/res.zip

# 2. java -> class -> dex
"$JAVAC" -source 17 -target 17 -Xlint:-options -encoding UTF-8 \
    -classpath "$PLATFORM" -d build/classes \
    $(find $SRC build/gen -name '*.java')
"$BT/d8" --release --min-api 31 --lib "$PLATFORM" --output build/dex \
    $(find build/classes -name '*.class')

# 3. package, align, sign
cp build/base.apk build/unsigned.apk
(cd build/dex && zip -q ../unsigned.apk classes.dex)
"$BT/zipalign" -f -p 4 build/unsigned.apk build/aligned.apk
[ -f debug.keystore ] || "$KEYTOOL" -genkeypair -keystore debug.keystore -storepass android \
    -keypass android -alias debug -keyalg RSA -keysize 2048 -validity 10000 \
    -dname "CN=Local Dictation Debug" >/dev/null 2>&1
"$BT/apksigner" sign --ks debug.keystore --ks-pass pass:android --ks-key-alias debug \
    --out "build/$OUT.apk" build/aligned.apk
echo "APK: $(pwd)/build/$OUT.apk"

if [ "${1:-}" = "install" ] || [ "${1:-}" = "trace" ]; then
    "$SDK/platform-tools/adb" install -r "build/$OUT.apk"
fi
