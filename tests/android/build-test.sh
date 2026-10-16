#!/bin/bash
set -euo pipefail
cd "$(dirname "$0")/../.."
SDK="${ANDROID_HOME:-$HOME/Library/Android/sdk}"
BT="$SDK/build-tools/35.0.0"
ANDROID="$SDK/platforms/android-35/android.jar"
mkdir -p build/test-classes build/test-dex
"$BT/aapt2" link -o build/test-base.apk -I "$ANDROID" --manifest tests/android/AndroidManifest.xml
javac --release 8 -encoding UTF-8 -cp "$ANDROID:build/classes" -d build/test-classes tests/android/SmokeTest.java
jar cf build/tests.jar -C build/test-classes .
"$BT/d8" --lib "$ANDROID" --classpath build/classes.jar --min-api 26 --output build/test-dex build/tests.jar
cp build/test-base.apk build/test-unsigned.apk
(cd build/test-dex && zip -q -u ../test-unsigned.apk classes.dex)
"$BT/zipalign" -f 4 build/test-unsigned.apk build/test-aligned.apk
"$BT/apksigner" sign --ks build/local-test.jks --ks-pass pass:android --ks-key-alias usbdoctor --out build/test.apk build/test-aligned.apk
