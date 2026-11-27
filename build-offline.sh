#!/usr/bin/env bash
# Builds a debug APK without Gradle, using Ubuntu 24.04 packages:
#   apt-get install aapt apksigner zipalign dalvik-exchange android-sdk-platform-23 kotlin default-jdk zip
set -euo pipefail
cd "$(dirname "$0")"
ANDROID_JAR=${ANDROID_JAR:-/usr/lib/android-sdk/platforms/android-23/android.jar}
KOTLIN_LIB=${KOTLIN_LIB:-$(dirname "$(find /usr/share/kotlin /usr/share/java -name 'kotlin-stdlib.jar' 2>/dev/null | head -1)")}
DX=$(command -v dalvik-exchange || command -v dx)
OUT=build/offline; rm -rf "$OUT"; mkdir -p "$OUT/gen" "$OUT/classes"
SRC=app/src/main
sed 's|<manifest |<manifest package="com.zlitch.clackwell" |' $SRC/AndroidManifest.xml > "$OUT/AndroidManifest.xml"
aapt2 compile --dir $SRC/res -o "$OUT/res.zip"
aapt2 link -I "$ANDROID_JAR" --manifest "$OUT/AndroidManifest.xml" --min-sdk-version 23 --target-sdk-version 34 \
  -A $SRC/assets --version-code 2 --version-name 1.1 --debug-mode -o "$OUT/base.apk" "$OUT/res.zip"
kotlinc $SRC/java -no-jdk -no-stdlib -classpath "$ANDROID_JAR:$KOTLIN_LIB/kotlin-stdlib.jar" -jvm-target 1.6 -d "$OUT/classes"
(cd "$OUT/classes" && rm -rf META-INF)
mkdir -p "$OUT/stdlib" && (cd "$OUT/stdlib" && jar xf "$KOTLIN_LIB/kotlin-stdlib.jar" && rm -rf META-INF)
cp -r "$OUT/stdlib/." "$OUT/classes/"
"$DX" --dex --output="$OUT/classes.dex" "$OUT/classes"
(cd "$OUT" && zip -q base.apk classes.dex)
zipalign -f 4 "$OUT/base.apk" "$OUT/aligned.apk"
[ -f build/debug.keystore ] || keytool -genkeypair -keystore build/debug.keystore -storepass android -keypass android \
  -alias androiddebugkey -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=Android Debug,O=Android,C=US" >/dev/null 2>&1
apksigner sign --ks build/debug.keystore --ks-pass pass:android --key-pass pass:android \
  --out build/clackwell-debug.apk "$OUT/aligned.apk"
apksigner verify build/clackwell-debug.apk
echo "Built build/clackwell-debug.apk"
