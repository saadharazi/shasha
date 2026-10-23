#!/bin/bash
# يبني shasha.apk بدون Android Studio ولا Gradle: aapt + javac + d8 + zipalign + apksigner
# المطلوب: Android SDK (build-tools وplatforms/android-34) وJDK 17 أو أحدث.
#   ANDROID_SDK_ROOT=/path/to/sdk JAVA_HOME=/path/to/jdk bash build.sh
# التوقيع: مفتاح shasha.keystore بجنب السكربت (ينسوى أول مرة، ولا تنشره).
# كلمة سره من SHASHA_KS_PASS، وإلا الافتراضية للبناء المحلي.
set -euo pipefail
DIR="$(cd "$(dirname "$0")" && pwd)"
SDK="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-$DIR/../../sdk}}"
BT="$(ls -d "$SDK"/build-tools/*/ 2>/dev/null | sort -V | tail -1)"
[ -n "$BT" ] || { echo "ما لقيت build-tools في $SDK" >&2; exit 1; }
PATH="$BT:$PATH"
JAR="$SDK/platforms/android-34/android.jar"
[ -n "${JAVA_HOME:-}" ] && PATH="$(cygpath -u "$JAVA_HOME" 2>/dev/null || echo "$JAVA_HOME")/bin:$PATH"
tool() { command -v "$1" >/dev/null 2>&1 && echo "$1" || echo "$1.bat"; }   # في ويندوز بعضها .bat
OUT="$DIR/build"
rm -rf "$OUT" && mkdir -p "$OUT/gen" "$OUT/classes"
cd "$DIR/app"
aapt package -f -m -J "$OUT/gen" -M AndroidManifest.xml -S res -A assets -I "$JAR" -F "$OUT/unsigned.apk" -0 arsc -0 ttf
mkdir -p "$OUT/stub"
javac -nowarn -Xlint:-options -source 8 -target 8 -encoding UTF-8 -bootclasspath "$JAR" -d "$OUT/stub" "$DIR/stub/LambdaMetafactory.java"
javac -nowarn -Xlint:-options -source 8 -target 8 -encoding UTF-8 -bootclasspath "$JAR" -classpath "$OUT/stub" \
  -d "$OUT/classes" $(find "$OUT/gen" src -name '*.java')
(cd "$OUT/classes" && jar cf ../classes.jar .)
"$(tool d8)" --min-api 24 --lib "$JAR" --output "$OUT" "$OUT/classes.jar"
(cd "$OUT" && aapt add -f unsigned.apk classes.dex >/dev/null)
zipalign -f -p 4 "$OUT/unsigned.apk" "$OUT/aligned.apk"
KS="$DIR/shasha.keystore"
PASS="${SHASHA_KS_PASS:-shasha-local}"
if [ ! -f "$KS" ]; then
  keytool -genkeypair -keystore "$KS" -storepass "$PASS" -keypass "$PASS" -alias shasha \
    -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=Shasha, O=Shasha" >/dev/null 2>&1
fi
"$(tool apksigner)" sign --ks "$KS" --ks-pass "pass:$PASS" --key-pass "pass:$PASS" --ks-key-alias shasha \
  --out "$OUT/shasha.apk" "$OUT/aligned.apk"
"$(tool apksigner)" verify "$OUT/shasha.apk"
ls -la "$OUT/shasha.apk"
