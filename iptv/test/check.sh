#!/bin/bash
# يشغّل test/LibraryCheck.java على الكمبيوتر ضد كلاسات آخر بناء (bash build.sh أول)
set -euo pipefail
DIR="$(cd "$(dirname "$0")/.." && pwd)"
SDK="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-$DIR/../../sdk}}"
JAR="$SDK/platforms/android-34/android.jar"
[ -n "${JAVA_HOME:-}" ] && PATH="$(cygpath -u "$JAVA_HOME" 2>/dev/null || echo "$JAVA_HOME")/bin:$PATH"
SEP=":"   # Git Bash يحولها لـ ; لحاله
OUT="$DIR/build/test"
mkdir -p "$OUT"
javac -nowarn -encoding UTF-8 -source 8 -target 8 -Xlint:-options -cp "$DIR/build/classes$SEP$JAR" -d "$OUT" "$DIR/test/LibraryCheck.java"
java -Dfile.encoding=UTF-8 -Dstdout.encoding=UTF-8 -cp "$OUT$SEP$DIR/build/classes$SEP$JAR" io.github.saadharazi.shasha.LibraryCheck
