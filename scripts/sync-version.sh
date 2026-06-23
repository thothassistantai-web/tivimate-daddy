#!/usr/bin/env bash
# Sync VERSION (or monorepo STEPDADDY_VERSION) → StepDaddyConstants.java PATCH_VERSION + VERSION_CODE.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
MONOREPO_VERSION="${ROOT}/../STEPDADDY_VERSION"
VERSION_FILE="${ROOT}/VERSION"
CONSTANTS_JAVA="${ROOT}/stepdaddy-patch/src/ar/tvplayer/tv/stepdaddy/StepDaddyConstants.java"

read_prop() {
  local file="$1"
  local key="$2"
  grep -E "^${key}=" "${file}" | head -1 | cut -d= -f2-
}

if [[ -f "${MONOREPO_VERSION}" ]]; then
  PATCH_VERSION="$(read_prop "${MONOREPO_VERSION}" STEPDADDY_VERSION)"
  VERSION_CODE="$(read_prop "${MONOREPO_VERSION}" VERSION_CODE)"
  echo "==> Using monorepo ${MONOREPO_VERSION}"
elif [[ -f "${VERSION_FILE}" ]]; then
  PATCH_VERSION="$(read_prop "${VERSION_FILE}" PATCH_VERSION)"
  VERSION_CODE="$(read_prop "${VERSION_FILE}" VERSION_CODE)"
else
  echo "ERROR: Neither ${MONOREPO_VERSION} nor ${VERSION_FILE} found" >&2
  exit 1
fi

GITHUB_RELEASE_REPO=""
if [[ -f "${VERSION_FILE}" ]]; then
  GITHUB_RELEASE_REPO="$(read_prop "${VERSION_FILE}" GITHUB_RELEASE_REPO)"
fi

if [[ -z "${PATCH_VERSION}" ]]; then
  echo "ERROR: PATCH_VERSION / STEPDADDY_VERSION missing" >&2
  exit 1
fi
if [[ -z "${VERSION_CODE}" ]]; then
  echo "ERROR: VERSION_CODE missing" >&2
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
