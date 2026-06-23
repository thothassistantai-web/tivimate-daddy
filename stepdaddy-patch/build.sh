#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")" && pwd)"
PATCH_ROOT="$ROOT"
DECODED="$PATCH_ROOT/decoded"
SRC="$PATCH_ROOT/src"
OUT="$PATCH_ROOT/out"
TOOLS="$ROOT/../tools"
FINAL_APK="$ROOT/../TiviMate-4.6.1-StepDaddy.apk"

ANDROID_SDK="${ANDROID_HOME:-$HOME/Android/Sdk}"
BUILD_TOOLS="$(ls -1 "$ANDROID_SDK/build-tools" | sort -V | tail -1)"
D8="$ANDROID_SDK/build-tools/$BUILD_TOOLS/d8"
ZIPALIGN="$ANDROID_SDK/build-tools/$BUILD_TOOLS/zipalign"
APKSIGNER="$ANDROID_SDK/build-tools/$BUILD_TOOLS/apksigner"
BAKSMALI_JAR="$TOOLS/baksmali.jar"

# Pick newest installed platform android.jar (bootclasspath for javac)
ANDROID_JAR=""
for api in $(ls -1 "$ANDROID_SDK/platforms" 2>/dev/null | sed 's/android-//' | sort -n); do
  jar="$ANDROID_SDK/platforms/android-$api/android.jar"
  if [[ -f "$jar" ]]; then
    ANDROID_JAR="$jar"
  fi
done
if [[ -z "$ANDROID_JAR" || ! -f "$ANDROID_JAR" ]]; then
  echo "Fatal: no android.jar under $ANDROID_SDK/platforms" >&2
  exit 1
fi
echo "==> Using platform jar: $ANDROID_JAR"

mkdir -p "$OUT/classes" "$OUT/dex" "$OUT/smali" "$OUT/dist"

if [[ -f "$ROOT/../scripts/sync-version.sh" ]]; then
  echo "==> Syncing PATCH_VERSION from VERSION"
  bash "$ROOT/../scripts/sync-version.sh"
fi

echo "==> Compiling StepDaddy patch sources"
find "$SRC" -name '*.java' | sort > "$OUT/sources.txt"
rm -rf "$OUT/classes"
mkdir -p "$OUT/classes"

# Java 9+ ignores -bootclasspath unless combined with --release or explicit classpath;
# android.jar as bootclasspath is the standard APK patch workflow.
javac -encoding UTF-8 -source 1.8 -target 1.8 \
  -bootclasspath "$ANDROID_JAR" \
  -d "$OUT/classes" @"$OUT/sources.txt"

echo "==> Dexing patch classes"
rm -f "$OUT/dex"/*.dex 2>/dev/null || true
"$D8" --lib "$ANDROID_JAR" --release --min-api 21 --output "$OUT/dex" $(find "$OUT/classes" -name '*.class')

if [[ ! -f "$BAKSMALI_JAR" ]]; then
  echo "==> Downloading baksmali"
  curl -fsSL -o "$BAKSMALI_JAR" \
    https://github.com/JesusFreke/smali/releases/download/2.5.2/baksmali-2.5.2.jar
fi

echo "==> Disassembling patch dex to smali"
rm -rf "$OUT/smali"
java -jar "$BAKSMALI_JAR" d "$OUT/dex/classes.dex" -o "$OUT/smali"

echo "==> Merging smali into decoded APK"
rm -rf "$DECODED/smali/ar/tvplayer/tv/stepdaddy"
mkdir -p "$DECODED/smali/ar/tvplayer/tv/stepdaddy"
cp -r "$OUT/smali/ar/tvplayer/tv/stepdaddy/." "$DECODED/smali/ar/tvplayer/tv/stepdaddy/"

echo "==> Applying manifest and hook patches"
python3 "$PATCH_ROOT/apply_hooks.py" "$DECODED"

echo "==> Building APK"
java -jar "$TOOLS/apktool.jar" b "$DECODED" -o "$OUT/dist/tivimate-stepdaddy-unsigned.apk"

echo "==> Aligning and signing"
"$ZIPALIGN" -f 4 "$OUT/dist/tivimate-stepdaddy-unsigned.apk" "$OUT/dist/tivimate-stepdaddy-aligned.apk"

KEYSTORE="$OUT/stepdaddy.keystore"
if [[ ! -f "$KEYSTORE" ]]; then
  keytool -genkeypair -v -keystore "$KEYSTORE" -storepass stepdaddy -keypass stepdaddy \
    -alias stepdaddy -keyalg RSA -keysize 2048 -validity 10000 \
    -dname "CN=StepDaddy Patch, OU=Research, O=ThothAssistant, L=Local, S=NA, C=US"
fi

"$APKSIGNER" sign --ks "$KEYSTORE" --ks-pass pass:stepdaddy --key-pass pass:stepdaddy \
  --out "$OUT/dist/TiviMate-4.6.1-StepDaddy.apk" "$OUT/dist/tivimate-stepdaddy-aligned.apk"

cp -f "$OUT/dist/TiviMate-4.6.1-StepDaddy.apk" "$FINAL_APK"
ls -lh "$FINAL_APK"
echo "Built: $FINAL_APK"
