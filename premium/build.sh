#!/usr/bin/env bash
# Builds SmartTube Premium from the current checkout and publishes it to this repo's Releases.
# Usage: premium/build.sh "<release note>"
# Needs: GH_TOKEN (github.token), KEYSTORE_B64 and KEYSTORE_PASSWORD (repo secrets), GITHUB_REPOSITORY (set by Actions)
set -euo pipefail

NOTE="${1:-Build}"
ROOT=$(pwd)
OUT="$ROOT/premium-out"
LATEST_TAG="premium-latest"   # fixed release the app's updater reads from

if [ -z "${KEYSTORE_PASSWORD:-}" ]; then
  echo "::error::The KEYSTORE_PASSWORD secret is missing. Add it in Settings > Secrets and variables > Actions, then run this again."
  exit 1
fi
if [ -z "${KEYSTORE_B64:-}" ]; then
  echo "::error::The KEYSTORE_B64 secret is missing. Add it in Settings > Secrets and variables > Actions, then run this again."
  exit 1
fi

# Same signing key for every build, so updates install over each other.
# The key comes from the KEYSTORE_B64 secret and is never kept in the repo.
KEY_FILE="${RUNNER_TEMP:-/tmp}/smarttube-premium.p12"
echo "$KEYSTORE_B64" | base64 -d > "$KEY_FILE"
cat > keystore.properties <<PROPS
storeFile=$KEY_FILE
storePassword=$KEYSTORE_PASSWORD
keyAlias=smarttubepremium
keyPassword=$KEYSTORE_PASSWORD
PROPS

# Keep SmartTube's version name; version code always goes up
BASE_VERSION=$(grep -m1 'versionName' smarttubetv/build.gradle | awk -F'"' '{print $2}')
VERSION_CODE=$(( $(date +%s) / 60 ))
VERSION_NAME="${BASE_VERSION}-premium-${VERSION_CODE}"
sed -i "0,/versionCode [0-9]*/s//versionCode ${VERSION_CODE}/" smarttubetv/build.gradle
sed -i "0,/versionName \"[^\"]*\"/s//versionName \"${VERSION_NAME}\"/" smarttubetv/build.gradle
echo "Building SmartTube Premium ${VERSION_NAME} (code ${VERSION_CODE})"

chmod +x gradlew
./gradlew assembleStbetaDebug

# Put back build.gradle so the version bump isn't committed
git checkout -- smarttubetv/build.gradle
rm -f keystore.properties "$KEY_FILE"

mkdir -p "$OUT"
APK_DIR=smarttubetv/build/outputs/apk/stbeta/debug
for arch in armeabi-v7a arm64-v8a x86 universal; do
  f=$(ls "$APK_DIR"/*_"$arch".apk 2>/dev/null | head -n1 || true)
  if [ -n "$f" ]; then cp "$f" "$OUT/smarttube_premium_${arch}.apk"; fi
done
ls -la "$OUT"

# Update file read by the app's built-in updater (same format as SmartTube's)
DL="https://github.com/${GITHUB_REPOSITORY}/releases/download/${LATEST_TAG}"
cat > "$OUT/smarttube_premium.json" <<JSON
{
    "package": {
        "downloadUrlList": ["${DL}/smarttube_premium_armeabi-v7a.apk"],
        "downloadUrlList_arm64-v8a": ["${DL}/smarttube_premium_arm64-v8a.apk"],
        "downloadUrlList_x86": ["${DL}/smarttube_premium_x86.apk"]
    },
    "${VERSION_NAME}": {
        "versionCode": ${VERSION_CODE},
        "changelog": ["${NOTE}"]
    }
}
JSON

# 1) A release for this version, to keep the history
gh release create "premium-${BASE_VERSION}-${VERSION_CODE}" "$OUT"/*.apk --repo "$GITHUB_REPOSITORY" \
  --title "SmartTube Premium ${BASE_VERSION} (${NOTE})" \
  --notes "${NOTE}. If unsure which file to install, use smarttube_premium_universal.apk."

# 2) The fixed "premium-latest" release the app checks for updates
if ! gh release view "$LATEST_TAG" --repo "$GITHUB_REPOSITORY" > /dev/null 2>&1; then
  gh release create "$LATEST_TAG" --repo "$GITHUB_REPOSITORY" --target "$(git rev-parse HEAD)" \
    --title "SmartTube Premium (latest)" \
    --notes "Always the newest SmartTube Premium. The app updates itself from here."
fi
gh release upload "$LATEST_TAG" "$OUT"/*.apk "$OUT/smarttube_premium.json" --repo "$GITHUB_REPOSITORY" --clobber
gh release edit "$LATEST_TAG" --repo "$GITHUB_REPOSITORY" --title "SmartTube Premium ${VERSION_NAME} (latest)"
