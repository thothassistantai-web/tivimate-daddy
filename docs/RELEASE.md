# TiviMate Daddy release process

How to cut a **TiviMate Daddy** (StepDaddy patch) release for fleet sideload, GitHub Releases, or Gateway **Install Apps** catalog updates.

Gateway release process: [stepdaddy-gateway-android/docs/RELEASE.md](https://github.com/thothassistantai-web/stepdaddy-gateway-android/blob/main/docs/RELEASE.md).

## Version bump

1. Edit [`VERSION`](../VERSION):

```properties
PATCH_VERSION=2.0.0
BASE_TIVIMATE_VERSION=4.6.1
VERSION_CODE=20000
GITHUB_RELEASE_REPO=thothassistantai-web/tivimate-daddy
```

`VERSION_CODE` must increase monotonically: `major*10000 + minor*100 + patch` (e.g. `2.0.0` → `20000`).  
In the monorepo, bump [`STEPDADDY_VERSION`](../../STEPDADDY_VERSION) first, then sync local `VERSION` / run `./scripts/sync-version.sh`.

2. Update [`CHANGELOG.md`](../CHANGELOG.md) — move `Unreleased` items into a dated section.

3. Sync runtime constant and rebuild:

```bash
./scripts/sync-version.sh    # VERSION → StepDaddyConstants.java
./scripts/build-release.sh   # build + stage release/
```

4. Update gateway bundled catalog in [stepdaddy-gateway-android](https://github.com/thothassistantai-web/stepdaddy-gateway-android) (`app/src/main/assets/install_apps_catalog.json`):
   - `version` → new `PATCH_VERSION`
   - `apkUrl` → GitHub Release asset URL from **this repo** (after publish)

## Prerequisites

| Tool | Notes |
|------|-------|
| Android SDK | `ANDROID_HOME` with `platforms/android-29+` and latest `build-tools` |
| JDK 8+ | `javac` for patch sources |
| Python 3 | `apply_hooks.py` |
| apktool | `tools/apktool.jar` |
| Decoded base | `stepdaddy-patch/decoded/` (4.6.1 mod — not committed; bootstrap from ONN `tivimate-usb.apk`) |

Signing uses `stepdaddy-patch/out/stepdaddy.keystore` (generated on first build). **Keep the same keystore** across patch releases so `adb install -r` upgrades work.

## Build outputs

| Artifact | Path |
|----------|------|
| Stable (latest) | `TiviMate-4.6.1-StepDaddy.apk` |
| Versioned | `release/TiviMate-4.6.1-StepDaddy-<PATCH_VERSION>.apk` |
| Update manifest | `release/update-manifest.json` |
| Release notes | `release/RELEASE_NOTES-<PATCH_VERSION>.md` |

## GitHub Release (manual)

**Self-signed APK** — release notes should mention `stepdaddy.keystore` signing.

```bash
PATCH_VERSION=$(grep ^PATCH_VERSION= VERSION | cut -d= -f2)
TAG="tivimate-daddy-v${PATCH_VERSION}"
ASSET="release/TiviMate-4.6.1-StepDaddy-${PATCH_VERSION}.apk"

git tag -a "$TAG" -m "TiviMate Daddy ${PATCH_VERSION}"
git push origin "$TAG"

gh release create "$TAG" \
  --repo thothassistantai-web/tivimate-daddy \
  --title "TiviMate Daddy ${PATCH_VERSION}" \
  --notes-file "release/RELEASE_NOTES-${PATCH_VERSION}.md" \
  "$ASSET" \
  release/update-manifest.json
```

After publish, set `apkUrl` in:

- `release/update-manifest.json` (this repo)
- `stepdaddy-gateway-android/.../install_apps_catalog.json` (StepDaddy entry)
- Optional: `DEFAULT_TIVIMATE_STEPDADDY_APK_URL` in gateway `build.gradle.kts`

### `update-manifest.json` schema

```json
{
  "versionCode": 20000,
  "versionName": "2.0.0",
  "baseTiviMateVersion": "4.6.1",
  "apkUrl": "https://github.com/thothassistantai-web/tivimate-daddy/releases/download/tivimate-daddy-v2.0.0/TiviMate-4.6.1-StepDaddy-2.0.0.apk",
  "apkFileName": "TiviMate-4.6.1-StepDaddy-2.0.0.apk",
  "stableApkPath": "TiviMate-4.6.1-StepDaddy.apk",
  "releaseNotes": "See CHANGELOG.md",
  "mandatory": false
}
```

## Pre-release checklist

- [ ] `./scripts/build-release.sh` succeeds
- [ ] `PATCH_VERSION` bumped in `VERSION` and synced to `StepDaddyConstants.java`
- [ ] `CHANGELOG.md` updated
- [ ] `curl :4617/status` reports expected `patchVersion` on test stick
- [ ] Gateway auto-setup + tune smoke test with StepDaddy Gateway on `:3000`
- [ ] `install_apps_catalog.json` version / URL updated in gateway repo
- [ ] Same `stepdaddy.keystore` used (or fleet must uninstall before install)

## CI workflow

[`.github/workflows/tivimate-daddy-release.yml`](../.github/workflows/tivimate-daddy-release.yml)

- **workflow_dispatch** — build APK, upload Actions artifact (no publish by default)
- **Tag `tivimate-daddy-v*`** — same build + artifact; draft release when `publish_release` or tag push + `GH_RELEASE_TOKEN`
- **Secrets (optional):** `TIVIMATE_DECODED_CACHE_B64`, `STEPDADDY_KEYSTORE_B64` for CI builds without local decoded tree

## Install tiers (documentation)

| Tier | APK | Catalog id |
|------|-----|------------|
| TiviMate Daddy | `TiviMate-4.6.1-StepDaddy.apk` | `stepdaddy-TiviMate-4.6.1-StepDaddy` |
| 4.6.1 mod | TV2024 / `tivimate-usb.apk` | `tv2024-TiviMate-v4.6.1-Premium-Mod` |
| Official | tivimate.com | (browser — no catalog APK) |

## Rollback

Ship previous GitHub Release asset from this repo; fleet reinstall via Gateway **Install Apps** or `adb install -r` with the older versioned APK. Patch prefs (`stepdaddy_bridge`) survive unless app data is cleared.
