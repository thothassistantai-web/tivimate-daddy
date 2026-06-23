#!/usr/bin/env bash
# Build TiviMate Daddy APK and stage release artifacts (gateway-style).
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
VERSION_FILE="${ROOT}/VERSION"
PATCH_ROOT="${ROOT}/stepdaddy-patch"
RELEASE_DIR="${ROOT}/release"

source_version() {
  local key="$1"
  grep -E "^${key}=" "${VERSION_FILE}" | head -1 | cut -d= -f2-
}

PATCH_VERSION="$(source_version PATCH_VERSION)"
BASE_VERSION="$(source_version BASE_TIVIMATE_VERSION)"
VERSION_CODE="$(source_version VERSION_CODE)"
APK_BASENAME="$(source_version APK_BASENAME)"
GITHUB_REPO="$(source_version GITHUB_RELEASE_REPO)"

if [[ -z "${PATCH_VERSION}" || -z "${APK_BASENAME}" ]]; then
  echo "ERROR: VERSION file is incomplete (${VERSION_FILE})" >&2
  exit 1
fi

echo "==> Syncing patch version from VERSION"
"${ROOT}/scripts/sync-version.sh"

echo "==> Building patched APK"
"${PATCH_ROOT}/build.sh"

mkdir -p "${RELEASE_DIR}"

STABLE_APK="${ROOT}/${APK_BASENAME}.apk"
VERSIONED_APK="${RELEASE_DIR}/${APK_BASENAME}-${PATCH_VERSION}.apk"
TAG="tivimate-daddy-v${PATCH_VERSION}"
ASSET_NAME="$(basename "${VERSIONED_APK}")"
APK_URL_PLACEHOLDER="https://github.com/${GITHUB_REPO}/releases/download/${TAG}/${ASSET_NAME}"

cp -f "${STABLE_APK}" "${VERSIONED_APK}"
cp -f "${STABLE_APK}" "${RELEASE_DIR}/${APK_BASENAME}-latest.apk"

MANIFEST="${RELEASE_DIR}/update-manifest.json"
cat > "${MANIFEST}" <<EOF
{
  "versionCode": ${VERSION_CODE:-0},
  "versionName": "${PATCH_VERSION}",
  "baseTiviMateVersion": "${BASE_VERSION}",
  "apkUrl": "${APK_URL_PLACEHOLDER}",
  "apkFileName": "${ASSET_NAME}",
  "stableApkPath": "${APK_BASENAME}.apk",
  "releaseNotes": "See CHANGELOG.md",
  "mandatory": false
}
EOF

RELEASE_NOTES="${RELEASE_DIR}/RELEASE_NOTES-${PATCH_VERSION}.md"
cat > "${RELEASE_NOTES}" <<EOF
# TiviMate Daddy ${PATCH_VERSION}

- **Base TiViMate:** ${BASE_VERSION} (ONN USB premium mod, \`ar.tvplayer.tv\`)
- **Patch:** ${PATCH_VERSION}
- **APK:** \`${ASSET_NAME}\`
- **Git tag:** \`${TAG}\`

## Verify after install

\`\`\`bash
adb forward tcp:4617 tcp:4617
curl -s http://127.0.0.1:4617/status | jq .patchVersion
# → "${PATCH_VERSION}"
\`\`\`

See [CHANGELOG.md](../CHANGELOG.md) for full notes.
EOF

echo ""
echo "Build complete."
echo "  Stable APK:     ${STABLE_APK}"
echo "  Versioned APK:  ${VERSIONED_APK}"
echo "  Manifest:       ${MANIFEST}"
echo "  Release notes:  ${RELEASE_NOTES}"
echo ""
echo "GitHub tag (manual):  git tag -a ${TAG} -m \"TiviMate Daddy ${PATCH_VERSION}\""
echo "Release asset URL:    ${APK_URL_PLACEHOLDER}"
