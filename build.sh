#!/bin/bash
set -euo pipefail
cd "$(dirname "$0")"
SDK="${ANDROID_HOME:-$HOME/Library/Android/sdk}"
BT="$SDK/build-tools/35.0.0"
ANDROID="$SDK/platforms/android-35/android.jar"
export JAVA_HOME="${JAVA_HOME:-$(/usr/libexec/java_home -v 17)}"
export PATH="$JAVA_HOME/bin:$PATH"
mkdir -p build/classes build/dex
"$BT/aapt2" compile --dir res -o build/resources.zip
"$BT/aapt2" link -o build/base.apk -I "$ANDROID" --manifest AndroidManifest.xml --java build/generated -A assets build/resources.zip
javac -encoding UTF-8 --release 8 -classpath "$ANDROID" -d build/classes src/cn/usbdoctor/*.java
jar cf build/classes.jar -C build/classes .
"$BT/d8" --lib "$ANDROID" --min-api 26 --output build/dex build/classes.jar
cp build/base.apk build/unsigned.apk
(cd build/dex && zip -q -u ../unsigned.apk classes.dex)
"$BT/zipalign" -f -p 4 build/unsigned.apk build/aligned.apk
# Local test-distribution certificate. Not a production signing identity.
if [ ! -f build/local-test.jks ]; then
 keytool -genkeypair -keystore build/local-test.jks -storepass android -keypass android -alias usbdoctor -keyalg RSA -keysize 3072 -validity 10000 -dname "CN=USB LAN Doctor Local Build"
fi
"$BT/apksigner" sign --ks build/local-test.jks --ks-pass pass:android --ks-key-alias usbdoctor --out build/USB-LAN-Doctor.apk build/aligned.apk
"$BT/apksigner" verify --verbose build/USB-LAN-Doctor.apk
OUTPUT_DIR="${OUTPUT_DIR:-$PWD/dist}"
mkdir -p "$OUTPUT_DIR"
cp build/USB-LAN-Doctor.apk "$OUTPUT_DIR/USB-LAN-Doctor.apk"
