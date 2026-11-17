#!/usr/bin/env bash
# Build SwarmLocate.app (Apple silicon + Intel) and zip it. Needs Xcode command line tools.
#   VERSION=0.1.0 BUILD=1 ./build-app.sh   ->  dist/SwarmLocate-mac-0.1.0.zip
set -euo pipefail
cd "$(dirname "$0")"
VERSION="${VERSION:-0.1.0}"
BUILD="${BUILD:-1}"
ARCHS=(--arch arm64 --arch x86_64)

swift build -c release "${ARCHS[@]}"
BIN="$(swift build -c release "${ARCHS[@]}" --show-bin-path)/SwarmLocate"

APP=dist/SwarmLocate.app
rm -rf dist
mkdir -p "$APP/Contents/MacOS" "$APP/Contents/Resources"
cp "$BIN" "$APP/Contents/MacOS/SwarmLocate"
sed -e "s/__VERSION__/$VERSION/" -e "s/__BUILD__/$BUILD/" Info.plist > "$APP/Contents/Info.plist"

# App icon from the 1024 px picture.
SET=dist/AppIcon.iconset
mkdir -p "$SET"
for s in 16 32 128 256 512; do
  sips -z $s $s Resources/AppIcon-1024.png --out "$SET/icon_${s}x${s}.png" > /dev/null
  sips -z $((s * 2)) $((s * 2)) Resources/AppIcon-1024.png --out "$SET/icon_${s}x${s}@2x.png" > /dev/null
done
iconutil -c icns "$SET" -o "$APP/Contents/Resources/AppIcon.icns"
rm -rf "$SET"

# Ad-hoc signature: no Apple developer account. macOS asks once before the first open.
codesign --force --sign - --timestamp=none "$APP"
codesign --verify --verbose=2 "$APP"
lipo -info "$APP/Contents/MacOS/SwarmLocate"

(cd dist && ditto -c -k --sequesterRsrc --keepParent SwarmLocate.app "SwarmLocate-mac-$VERSION.zip")
echo "Built dist/SwarmLocate-mac-$VERSION.zip"
