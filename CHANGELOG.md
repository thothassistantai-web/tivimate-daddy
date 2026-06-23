# Changelog

All notable changes to **TiviMate Daddy** (StepDaddy patch on TiViMate 4.6.1) are documented here.

Format based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).  
**Patch version** (`PATCH_VERSION` in [VERSION](VERSION)) is the release identifier reported at `GET :4617/status` → `patchVersion`.  
**Base TiViMate** stays at **4.6.1** (`versionCode` 4610) unless the ONN USB mod base is rebased.

## Version scheme

| Field | Example | Meaning |
|-------|---------|---------|
| `BASE_TIVIMATE_VERSION` | `4.6.1` | Underlying mod APK from `tivimate-usb.apk` |
| `PATCH_VERSION` | `2.0.0` | StepDaddy smali patch semver (aligned with Gateway) |
| `VERSION_CODE` | `20000` | Monotonic int for update checks (`major*10000 + minor*100 + patch`) |
| Git tag | `tivimate-daddy-v2.0.0` | Release tag in monorepo or sibling repo |
| Stable APK path | `TiviMate-4.6.1-StepDaddy.apk` | Latest local build output |
| Versioned asset | `TiviMate-4.6.1-StepDaddy-1.2.1-boot-tune-safe.apk` | GitHub Release attachment |

## [Unreleased]

## [2.0.0] - 2026-06-23

### Changed

- **Suite alignment** — unified semver `2.0.0` / `versionCode` 20000 with StepDaddy Gateway (replaces codename patch versions)
- **About display** — shows `StepDaddy 2.0.0` instead of `StepDaddy patch …`
- **Canonical version** — monorepo `STEPDADDY_VERSION` file is source of truth when present

### Fixed

- **Post-upgrade auto-setup** — `MY_PACKAGE_REPLACED` receiver suppresses setup churn on the upgrade session
- **Boot-tune** — only applies when explicitly saved via `GET /boot-tune/{n}` (no default channel fallback)

## [1.3.2-update-playlist-state] - 2026-06-23

### Added

- **Playlist state API** — `GET :4617/state` and `/status` expose `hasPlaylist`, `stateReason`, `playlistName`, redacted `playlistUrl`
- **State reasons** — `no_playlist`, `wizard_incomplete`, `gateway_test_url`, `ready`

### Fixed

- **About update button** — smali hook on `checkForNewVersion` + correct obfuscated preference APIs for Settings → About
- **Boot-tune** — gateway defers boot-tune until channels are loaded

## [1.2.1-boot-tune-safe] - 2026-06-23

### Fixed

- **Boot-tune safety** — defer channel tune until Room/SQLite WAL recovery completes (avoids parallel DB access crashes on cold start)
- **Boot-tune scheduling** — logs and applies saved boot channel on `MainActivity` resume after DB ready

### Added

- **`GET /boot-tune/{n}`** — persist default channel; applied on next foreground resume
- **`DEFAULT_BOOT_TUNE_CHANNEL`** — fallback channel 51 when unset

## [1.2.0-bidir] - 2026-06-22

### Added

- **`GET /state`** — rich bidirectional state (`wizardPhase`, `playerMode`, channel counts)
- **`GET /channels`** — read-only channel list from `TvPlayer.db`
- **`POST /tivimate-events`** — patch → gateway event fire-and-forget (`CHANNEL_CHANGED`, `WIZARD_STEP`, etc.)
- Gateway proxy routes: `/tivimate-state`, `/tivimate-events`, `/tivimate-handshake`

## [1.1.0-bidir] - 2026-06-22

### Added

- Initial bidirectional telemetry schemas (`patchVersion` on `/status` and events)
- Wizard auto-advance hooks for playlist URL / status / TVG URL steps

## [1.0.0] - 2026-06-22

### Added

- StepDaddy smali patch on TiViMate **4.6.1** ONN mod
- Loopback HTTP control API on port **4617**
- Auto-setup from gateway `GET /tivimate-setup`
- Deep links (`stepdaddy://`), broadcasts, tune/stream/EPG commands
- Signed output `TiviMate-4.6.1-StepDaddy.apk`

[Unreleased]: https://github.com/thothassistantai-web/tivimate-daddy/compare/tivimate-daddy-v2.0.0...HEAD
[2.0.0]: https://github.com/thothassistantai-web/tivimate-daddy/releases/tag/tivimate-daddy-v2.0.0
[1.3.2-update-playlist-state]: https://github.com/thothassistantai-web/tivimate-daddy/releases/tag/tivimate-daddy-v1.3.2-update-playlist-state
[1.3.0-about-update]: https://github.com/thothassistantai-web/tivimate-daddy/releases/tag/tivimate-daddy-v1.3.0-about-update
[1.2.1-boot-tune-safe]: https://github.com/thothassistantai-web/tivimate-daddy/releases/tag/tivimate-daddy-v1.2.1-boot-tune-safe
[1.2.0-bidir]: https://github.com/thothassistantai-web/tivimate-daddy/releases/tag/tivimate-daddy-v1.2.0-bidir
[1.1.0-bidir]: https://github.com/thothassistantai-web/tivimate-daddy/releases/tag/tivimate-daddy-v1.1.0-bidir
[1.0.0]: https://github.com/thothassistantai-web/tivimate-daddy/releases/tag/tivimate-daddy-v1.0.0
