#!/usr/bin/env bash
# Sync research/tivimate-apk/VERSION → StepDaddyConstants.java PATCH_VERSION + VERSION_CODE.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
VERSION_FILE="${ROOT}/VERSION"
CONSTANTS_JAVA="${ROOT}/stepdaddy-patch/src/ar/tvplayer/tv/stepdaddy/StepDaddyConstants.java"

if [[ ! -f "${VERSION_FILE}" ]]; then
  echo "ERROR: VERSION file not found at ${VERSION_FILE}" >&2
  exit 1
fi

PATCH_VERSION="$(grep -E '^PATCH_VERSION=' "${VERSION_FILE}" | head -1 | cut -d= -f2-)"
VERSION_CODE="$(grep -E '^VERSION_CODE=' "${VERSION_FILE}" | head -1 | cut -d= -f2-)"
GITHUB_RELEASE_REPO="$(grep -E '^GITHUB_RELEASE_REPO=' "${VERSION_FILE}" | head -1 | cut -d= -f2-)"

if [[ -z "${PATCH_VERSION}" ]]; then
  echo "ERROR: PATCH_VERSION missing in ${VERSION_FILE}" >&2
  exit 1
fi
if [[ -z "${VERSION_CODE}" ]]; then
  echo "ERROR: VERSION_CODE missing in ${VERSION_FILE}" >&2
  exit 1
fi

if [[ ! -f "${CONSTANTS_JAVA}" ]]; then
  echo "ERROR: StepDaddyConstants.java not found at ${CONSTANTS_JAVA}" >&2
  exit 1
fi

perl -i -pe "s/(PATCH_VERSION = \")[^\"]+(\")/\${1}${PATCH_VERSION}\${2}/" "${CONSTANTS_JAVA}"
perl -i -pe "s/(VERSION_CODE = )[0-9]+/\${1}${VERSION_CODE}/" "${CONSTANTS_JAVA}"
if [[ -n "${GITHUB_RELEASE_REPO}" ]]; then
  perl -i -pe "s|(GITHUB_RELEASE_REPO = \")[^\"]+(\")|\${1}${GITHUB_RELEASE_REPO}\${2}|" "${CONSTANTS_JAVA}"
fi
echo "==> PATCH_VERSION=${PATCH_VERSION} VERSION_CODE=${VERSION_CODE} → ${CONSTANTS_JAVA}"
