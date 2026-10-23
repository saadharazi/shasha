#!/bin/bash
# يبني Shasha.exe: مترجم C# اللي يجي مع ويندوز (.NET Framework 4)، والسكربت والواجهة جوّه كملفات مضمّنة.
#   bash pc/exe/build.sh        -> pc/exe/out/Shasha.exe
set -euo pipefail
EXE="$(cd "$(dirname "$0")" && pwd)"
PC="$(dirname "$EXE")"
CSC="/c/Windows/Microsoft.NET/Framework64/v4.0.30319/csc.exe"
mkdir -p "$EXE/out"
w() { cygpath -w "$1"; }
"$CSC" -nologo -codepage:65001 -optimize+ -target:winexe -out:"$(w "$EXE/out/Shasha.exe")" \
  -win32icon:"$(w "$EXE/shasha.ico")" -r:System.Windows.Forms.dll \
  -resource:"$(w "$PC/Shasha.ps1"),Shasha.ps1" \
  -resource:"$(w "$PC/ui/index.html"),ui/index.html" \
  -resource:"$(w "$PC/ui/favicon.svg"),ui/favicon.svg" \
  "$(w "$EXE/Shasha.cs")"
ls -la "$EXE/out/Shasha.exe"
