#!/usr/bin/env bash
# يبني نسخة التلفزيون في build/:
#   io.github.saadharazi.shasha_<الإصدار>_all.ipk  لتلفزيونات إل جي (webOS)، بـ ares-package
#   Shasha.wgt                                      لتلفزيونات سامسونج (Tizen)، موقّع لو فيه أداة tizen وشهادة
#
#   bash tv-web/build.sh
#   TIZEN_PROFILE=shasha bash tv-web/build.sh   # اسم ملف الشهادة في Tizen Studio (Certificate Manager)
set -euo pipefail
cd "$(dirname "$0")"
OUT=build
FILES="index.html app.css app.js data.js lib fonts icon.png largeIcon.png"
rm -rf "$OUT/webos" "$OUT/tizen" "$OUT"/*.ipk "$OUT"/*.wgt
mkdir -p "$OUT/webos" "$OUT/tizen"
cp -r $FILES appinfo.json "$OUT/webos/"
cp -r $FILES config.xml "$OUT/tizen/"

# ---- إل جي: ares-package من @webos-tools/cli (لو مو مثبّت، npx يجيبه مؤقت)
if command -v ares-package >/dev/null 2>&1; then ARES=(ares-package); else ARES=(npx -y -p @webos-tools/cli ares-package); fi
"${ARES[@]}" --no-minify -o "$OUT" "$OUT/webos"

# ---- سامسونج: الـ wgt ملف zip فيه config.xml؛ التلفزيون ما يثبّته إلا موقّع بشهادة سامسونج
if command -v tizen >/dev/null 2>&1; then
  tizen package -t wgt -s "${TIZEN_PROFILE:-shasha}" -o "$OUT" -- "$OUT/tizen"
  mv "$OUT"/*.wgt "$OUT/Shasha.wgt" 2>/dev/null || true
else
  PY=python3; "$PY" -c 1 2>/dev/null || PY=python   # في ويندوز python3 أحيانا اختصار للمتجر
  "$PY" -c "import shutil, sys; shutil.make_archive(sys.argv[1], 'zip', sys.argv[2])" "$OUT/Shasha" "$OUT/tizen"
  mv "$OUT/Shasha.zip" "$OUT/Shasha.wgt"
  echo "ملاحظة: Shasha.wgt بدون توقيع (ما فيه أداة tizen). وقّعه من Tizen Studio قبل التثبيت، الشرح في README.md"
fi
ls -la "$OUT"/*.ipk "$OUT"/*.wgt
