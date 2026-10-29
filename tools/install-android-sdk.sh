#!/usr/bin/env bash
# Installs the Android SDK command-line tools and the packages this project needs.
# Needs network access to dl.google.com. Usage: tools/install-android-sdk.sh [install dir]
set -euo pipefail
SDK="${1:-$HOME/android-sdk}"
TOOLS_ZIP="commandlinetools-linux-11076708_latest.zip"
mkdir -p "$SDK/cmdline-tools"
if [ ! -x "$SDK/cmdline-tools/latest/bin/sdkmanager" ]; then
  tmp="$(mktemp -d)"
  curl -fsSL "https://dl.google.com/android/repository/$TOOLS_ZIP" -o "$tmp/tools.zip"
  unzip -q "$tmp/tools.zip" -d "$tmp"
  rm -rf "$SDK/cmdline-tools/latest"
  mv "$tmp/cmdline-tools" "$SDK/cmdline-tools/latest"
  rm -rf "$tmp"
fi
yes | "$SDK/cmdline-tools/latest/bin/sdkmanager" --sdk_root="$SDK" --licenses > /dev/null || true
"$SDK/cmdline-tools/latest/bin/sdkmanager" --sdk_root="$SDK" "platform-tools" "platforms;android-35" "build-tools;35.0.0"
here="$(cd "$(dirname "$0")/.." && pwd)"
echo "sdk.dir=$SDK" > "$here/local.properties"
echo "Done. Wrote local.properties. Now: ./gradlew :app:assembleDebug"
