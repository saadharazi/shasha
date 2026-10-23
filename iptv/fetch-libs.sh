#!/bin/bash
# ينزّل مكتبات المشغّل (ExoPlayer/Media3 وGuava) في iptv/libs ويتحقق من بصمة SHA-1 حق كل ملف.
# مرة وحدة قبل أول بناء: bash iptv/fetch-libs.sh
set -euo pipefail
DIR="$(cd "$(dirname "$0")" && pwd)/libs"
mkdir -p "$DIR"
G=https://dl.google.com/dl/android/maven2
C=https://repo1.maven.org/maven2
M=1.11.1
FILES=(
  "$G/androidx/media3/media3-common/$M/media3-common-$M.aar"
  "$G/androidx/media3/media3-container/$M/media3-container-$M.aar"
  "$G/androidx/media3/media3-datasource/$M/media3-datasource-$M.aar"
  "$G/androidx/media3/media3-decoder/$M/media3-decoder-$M.aar"
  "$G/androidx/media3/media3-extractor/$M/media3-extractor-$M.aar"
  "$G/androidx/media3/media3-database/$M/media3-database-$M.aar"
  "$G/androidx/media3/media3-exoplayer/$M/media3-exoplayer-$M.aar"
  "$G/androidx/media3/media3-exoplayer-hls/$M/media3-exoplayer-hls-$M.aar"
  "$C/com/google/guava/guava/33.3.1-android/guava-33.3.1-android.jar"
  "$C/com/google/guava/failureaccess/1.0.2/failureaccess-1.0.2.jar"
  "$G/androidx/annotation/annotation-jvm/1.6.0/annotation-jvm-1.6.0.jar"
  "$G/androidx/exifinterface/exifinterface/1.3.6/exifinterface-1.3.6.aar"
)
for u in "${FILES[@]}"; do
  f="$DIR/$(basename "$u")"
  if [ ! -f "$f" ]; then
    curl -fsSL -o "$f.part" "$u"
    want="$(curl -fsSL "$u.sha1" | cut -c1-40)"
    got="$(sha1sum "$f.part" | cut -c1-40)"
    [ "$want" = "$got" ] || { rm -f "$f.part"; echo "بصمة غلط: $(basename "$u")" >&2; exit 1; }
    mv "$f.part" "$f"
  fi
  echo "ok $(basename "$f")"
done
