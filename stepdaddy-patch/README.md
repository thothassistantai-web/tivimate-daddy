# TiViMate StepDaddy Patch (TiviMate Daddy)

Smali patch for **TiViMate 4.6.1** (`ar.tvplayer.tv`) that adds programmatic control for StepDaddy Gateway: auto playlist setup, channel tune, stream open, EPG overlay, bidirectional telemetry, and a loopback HTTP API on port **4617**.

Works **with StepDaddy Gateway** (recommended) or is **non-functional alone** (no playlist without `:3000`). Gateway works **without** this patch using any IPTV client. See [stepdaddy-android/docs/TWO-APP.md](../../../stepdaddy-android/docs/TWO-APP.md).

| | |
|---|---|
| **Output APK** | `research/tivimate-apk/TiviMate-4.6.1-StepDaddy.apk` |
| **Patch version** | `2.0.0` — canonical in [`STEPDADDY_VERSION`](../STEPDADDY_VERSION), [`VERSION`](../VERSION), or monorepo `../STEPDADDY_VERSION`; synced to `StepDaddyConstants.PATCH_VERSION` |
| **TiViMate base** | ONN USB mod (`tivimate-usb.apk`, versionCode 4610) |
| **Gateway pairing** | `2.0.0` recommended (`GET /tivimate-handshake`) |
| **Log tag** | `StepDaddyBridge` |

## Patch version history

| `patchVersion` | Changes |
|----------------|---------|
| **`2.0.0`** | **Current** — suite alignment with Gateway; post-upgrade setup suppression; boot-tune only when explicitly saved |
| `1.3.2-update-playlist-state` | Playlist state API on `/state` and `/status` |
| `1.3.0-about-update` | In-app update checker in Settings → About (GitHub releases for `thothassistantai-web/tivimate-daddy`, self-signed APK install via FileProvider) |
| `1.2.1-boot-tune-safe` | 5 s defer before boot-tune so Room/SQLite WAL recovery finishes (fixes cold-boot crash when gateway auto-launches TiviMate) |
| `1.2.0-boot-fast` | Shorter boot-tune defer; faster first channel but crash risk on parallel DB access |
| `1.1.0-bidir` | Bidirectional API: `GET /state`, `GET /channels`, `POST` events to gateway `/tivimate-events`, `wizardPhase`, `patchVersion` in telemetry |
| (earlier) | Auto-setup, `:4617` HTTP, deep links, broadcasts |

Release process: [`../docs/RELEASE.md`](../docs/RELEASE.md) · Changelog: [`../CHANGELOG.md`](../CHANGELOG.md)

## Prerequisites

| Requirement | Notes |
|-------------|-------|
| Android SDK | `ANDROID_HOME` or `~/Android/Sdk` with `platforms/android-29+` and latest `build-tools` |
| JDK 8+ | `javac` for patch sources |
| Python 3 | `apply_hooks.py` |
| apktool | `research/tivimate-apk/tools/apktool.jar` |
| Decoded base | `stepdaddy-patch/decoded/` (apktool output of 4.6.1 mod) |

---

## Build

```bash
cd research/tivimate-apk/stepdaddy-patch
./build.sh
```

Release staging (sync version, versioned APK, manifest):

```bash
cd research/tivimate-apk
./scripts/build-release.sh
```

`build.sh` compiles `src/`, dexes with `d8`, disassembles to smali, merges into `decoded/`, runs `apply_hooks.py`, rebuilds with apktool, signs with `out/stepdaddy.keystore`, and copies the signed APK to `../TiviMate-4.6.1-StepDaddy.apk`.

---

## Install options

| Option | APK | Use with Gateway |
|--------|-----|------------------|
| **Daddy** (this patch) | `TiviMate-4.6.1-StepDaddy.apk` | Full — auto-setup, tune, events |
| **Mod** (4.6.1 ONN) | `tivimate-usb.apk` | Manual playlist URLs only |
| **Official** (5.3.x) | `files.tivimate.com/tivimate.apk` | Manual URLs; no `:4617` |

```bash
DEV=<serial>   # e.g. FUSA2541006925
APK=research/tivimate-apk/TiviMate-4.6.1-StepDaddy.apk

# Fresh install (recommended on fleet sticks)
adb -s $DEV uninstall ar.tvplayer.tv || true
adb -s $DEV install "$APK"

# Or replace in place (same signing key only)
adb -s $DEV install -r "$APK"
```

Start StepDaddy Gateway on the device (`127.0.0.1:3000`) **before** first launch so auto-setup can fetch the playlist.

---

## In-app updates (Settings → About)

The patch adds a **Check for StepDaddy update** entry on the TiViMate About screen (hooks `AboutSettingsFragment.onViewCreated`). It replaces the stock Parse Cloud version check for that preference via an `OnPreferenceClickListener`.

| Behavior | Detail |
|----------|--------|
| **Manual check** | Settings → About → *Check for StepDaddy update* |
| **Background check** | ~8 s after `MainActivity` launch (6 h cooldown) |
| **Source** | GitHub API `GET /repos/thothassistantai-web/tivimate-daddy/releases/latest` (fallback: scan tags `tivimate-daddy-v*`) |
| **Version compare** | `VERSION_CODE` integer from release body (`VERSION_CODE=…`) or semver prefix of `PATCH_VERSION`; UI shows human `PATCH_VERSION` string |
| **APK asset** | `TiviMate-4.6.1-StepDaddy-{PATCH_VERSION}.apk`, else first `.apk` on the release |
| **Download** | App cache `cache/apk/` via `StepDaddyUpdateDownloader` with progress dialog |
| **Install** | `FileProvider` authority `ar.tvplayer.tv.fileprovider` + `REQUEST_INSTALL_PACKAGES` (already in manifest) |
| **Self-signed** | Dialog notes that the APK is self-signed; prompts for *Install unknown apps* when needed |

Constants live in `StepDaddyConstants` (`GITHUB_RELEASE_REPO`, `GITHUB_USER_AGENT`, `VERSION_CODE`, `PATCH_VERSION`). Release repo is also declared in [`../VERSION`](../VERSION) as `GITHUB_RELEASE_REPO`.

**ONN quick start:** [stepdaddy-android/docs/ONN-QUICK-START.md](../../../stepdaddy-android/docs/ONN-QUICK-START.md)

```bash
adb -s $DEV shell am start -n ar.tvplayer.tv/.ui.MainActivity
```

---

## Auto-setup (`/tivimate-setup`)

On first `MainActivity` launch the patch:

1. Starts the HTTP control service (port 4617).
2. If auto-setup is enabled (default) and no gateway playlist exists, fetches  
   `{gateway_base}/tivimate-setup` (default gateway: `http://127.0.0.1:3000`).
3. Parses JSON and launches the playlist wizard with the returned URL.
4. Auto-advances the playlist URL wizard step for gateway URLs (`127.0.0.1` / `tivimate-playlist`).

**Expected setup JSON** (from StepDaddy Gateway):

```json
{
  "playlist": "http://127.0.0.1:3000/tivimate-playlist.m3u8",
  "epg": "http://127.0.0.1:3000/tivimate-epg.xml"
}
```

`playlist` is required; `epg` is parsed but not auto-applied in this build.

**Manual / forced setup:**

```bash
# Deep link with optional gateway override
adb shell am start -a android.intent.action.VIEW \
  -d 'stepdaddy://setup?base=http://127.0.0.1:3000'

# Broadcast
adb shell am broadcast -a ar.tvplayer.tv.action.STEPDADDY_SETUP \
  --es gateway_base 'http://127.0.0.1:3000'

# HTTP (device must have app running / service started)
adb shell 'curl -s http://127.0.0.1:4617/setup'
```

---

## HTTP control API (port 4617)

Binds to all interfaces on the device. Started automatically when `MainActivity` opens; can also be started via `stepdaddy://status` or broadcast `STEPDADDY_HTTP_START`.

For host-side access, forward the port:

```bash
adb forward tcp:4617 tcp:4617
curl -s http://127.0.0.1:4617/status | jq .
```

| Method | Path | Response `status` / body |
|--------|------|--------------------------|
| GET | `/status` | JSON: `ok`, `package`, `gateway`, `setupDone`, `port`, `patchVersion` |
| GET | `/state` | Rich JSON state — see **Bidirectional schemas** below |
| GET | `/setup` | `setup_started` — runs same flow as auto-setup |
| GET | `/tune/{channel}` | `tuned` or `tune_failed` — channel is playlist number |
| GET | `/boot-tune/{n}` | `boot_tune_saved` — saves channel; tunes on next `MainActivity` resume |
| GET | `/channel/up` | `key_sent` — CHANNEL_UP keyevent |
| GET | `/channel/down` | `key_sent` — CHANNEL_DOWN keyevent |
| GET | `/pause` | `key_sent` — MEDIA_PAUSE keyevent |
| GET | `/play` | `key_sent` — MEDIA_PLAY keyevent |
| GET | `/search?q={name}` | `search_opened` or `search_failed` — DB name lookup + `openPlayerFromSearch` |
| GET | `/channels?limit=50` | JSON `{ ok, channels: [{ id, tvg_ch_no?, name }] }` |
| GET | `/stream/{channel}` | `stream_opened` or `stream_failed` — opens `{gateway}/tivimate-stream/{channel}.m3u8` |
| GET | `/epg` | `epg_opened` or `epg_failed` — dispatches MENU key |
| GET | `/launch` | `launched` — brings up `MainActivity` |
| * | other | `404` `{"error":"not_found"}` |

Success responses: `{"ok":true,"status":"<value>"}`.

**Tune logic:** looks up `channels.tvg_ch_no`, then `channels.position_in_playlist` (channel − 1). Prefers in-app TV guide navigation via reflection; falls back to `ACTION_VIEW` stream URL on `MainActivity`.

---

## Bidirectional schemas (Gateway ↔ Patch)

Patch version is reported as `patchVersion` (currently **`1.2.1-boot-tune-safe`**). Gateway consumes these without modifying the APK.

### GET `/state` response

```json
{
  "ok": true,
  "setupDone": true,
  "wizardPending": false,
  "wizardPhase": "idle",
  "currentChannelId": 12345,
  "currentChannelNo": 51,
  "currentChannelName": "CNN HD",
  "isPlaying": true,
  "playerMode": "fullscreen",
  "playlistCount": 1,
  "channelCount": 842,
  "gatewayBase": "http://127.0.0.1:3000",
  "patchVersion": "1.2.1-boot-tune-safe"
}
```

| Field | Type | Values / notes |
|-------|------|----------------|
| `wizardPhase` | string | `idle`, `url`, `status`, `epg`, `importing`, `done` |
| `playerMode` | string | `fullscreen`, `guide`, `pip`, `unknown` |
| `currentChannelName` | string | Omitted when unknown |
| `playlistCount`, `channelCount` | int | From `TvPlayer.db` read-only; `0` if DB missing |

### POST `{gatewayBase}/tivimate-events` (patch → gateway)

Fire-and-forget on a background thread. Gateway should accept `POST` with `Content-Type: application/json`.

```json
{
  "type": "CHANNEL_CHANGED",
  "timestamp": 1719158400123,
  "patchVersion": "1.2.1-boot-tune-safe",
  "channelId": 12345,
  "channelNo": 51,
  "channelName": "CNN HD",
  "wizardPhase": "idle",
  "setupDone": true,
  "detail": "optional string"
}
```

| `type` | When fired |
|--------|------------|
| `PLAYBACK_STARTED` | Successful tune / search / media play |
| `PLAYBACK_ERROR` | ExoPlayer `onPlayerError` hook |
| `CHANNEL_CHANGED` | Successful channel tune |
| `WIZARD_STEP` | Wizard fragment entered; `detail` = phase |
| `SETUP_COMPLETE` | Gateway playlist confirmed in DB |

### Gateway proxy routes (port 3000)

The gateway exposes patch telemetry and state without modifying the APK:

| Method | Path | Role |
|--------|------|------|
| POST | `/tivimate-events` | Ingest patch events (`type` or `event`, `detail` or `message`) |
| GET | `/tivimate-events` | Ring buffer (`?since=` ms timestamp optional) |
| GET | `/tivimate-state` | Proxies patch `GET :4617/state` (TiViMate must be foreground) |
| GET | `/tivimate-handshake` | Device id, gateway version, feature URLs |

**Schema alias:** patch POST uses `type` + `detail`; gateway normalizes to `event` + `message` in the buffer.

---

## Deep links (`stepdaddy://`)

Handled by exported `StepDaddyBridgeActivity` (translucent, finishes immediately).

| URI | Action |
|-----|--------|
| `stepdaddy://setup` | Run setup with saved gateway base |
| `stepdaddy://setup?base=http://host:port` | Run setup; saves `base` to prefs |
| `stepdaddy://channel/{n}` | Tune channel *n* |
| `stepdaddy://stream?url={encoded_url}` | Open stream URL via `MainActivity` |
| `stepdaddy://status` | Ensure HTTP service is running |

`MainActivity` also accepts `http(s)://…/tivimate-stream/…` VIEW intents (`.m3u8` on `127.0.0.1` registered in manifest).

---

## Broadcast intents

Receiver: `ar.tvplayer.tv.stepdaddy.StepDaddyCommandReceiver` (exported).

| Action | Extras | Effect |
|--------|--------|--------|
| `ar.tvplayer.tv.action.STEPDADDY_SETUP` | `gateway_base` (optional string) | Fetch `/tivimate-setup`, launch playlist wizard |
| `ar.tvplayer.tv.action.STEPDADDY_TUNE` | `channel` (int) **or** `channel_id` (long) | Tune by number or DB id |
| `ar.tvplayer.tv.action.STEPDADDY_STREAM` | `stream_url` (string) | Open stream |
| `ar.tvplayer.tv.action.STEPDADDY_EPG` | — | Open EPG overlay (MENU) |
| `ar.tvplayer.tv.action.STEPDADDY_HTTP_START` | — | Start HTTP service |
| `ar.tvplayer.tv.action.STEPDADDY_HTTP_STOP` | — | Stop HTTP service |

Same actions can be sent to `StepDaddyBridgeActivity` via `am start -a …` (converted to internal URIs).

---

## Test commands

```bash
DEV=<serial>
PKG=ar.tvplayer.tv

# Status
adb -s $DEV forward tcp:4617 tcp:4617
curl -s http://127.0.0.1:4617/status

# Setup (gateway must be up)
adb -s $DEV shell am broadcast -a ar.tvplayer.tv.action.STEPDADDY_SETUP

# Tune channel 51
adb -s $DEV shell am broadcast -a ar.tvplayer.tv.action.STEPDADDY_TUNE --ei channel 51
# or
adb -s $DEV shell 'curl -s http://127.0.0.1:4617/tune/51'
# or
adb -s $DEV shell am start -a android.intent.action.VIEW -d 'stepdaddy://channel/51'

# Stream by channel (gateway HLS)
adb -s $DEV shell 'curl -s http://127.0.0.1:4617/stream/51'

# Stream by explicit URL
adb -s $DEV shell am broadcast -a ar.tvplayer.tv.action.STEPDADDY_STREAM \
  --es stream_url 'http://127.0.0.1:3000/tivimate-stream/51.m3u8'

# EPG overlay
adb -s $DEV shell am broadcast -a ar.tvplayer.tv.action.STEPDADDY_EPG
adb -s $DEV shell 'curl -s http://127.0.0.1:4617/epg'

# Bidirectional (gateway on :3000, patch on :4617)
adb -s $DEV forward tcp:3000 tcp:3000
adb -s $DEV forward tcp:4617 tcp:4617
curl -s http://127.0.0.1:4617/state | jq .
curl -s 'http://127.0.0.1:4617/channels?limit=5' | jq .
curl -s http://127.0.0.1:4617/tune/36
sleep 3
curl -s http://127.0.0.1:3000/tivimate-events | jq .
curl -s http://127.0.0.1:3000/tivimate-state | jq .

# Logs
adb -s $DEV logcat -s StepDaddyBridge:* TiviMateEventStore:*

# Listening check (after app foreground)
adb -s $DEV shell 'ss -lntp | grep 4617'
```

---

## Fleet rollout notes

### APK signing key (`out/stepdaddy.keystore`)

The patch APK is signed with `stepdaddy-patch/out/stepdaddy.keystore` (not the ONN mod cert). The file is **gitignored** and must be **backed up** outside the repo.

- **2.0.0** introduced a **new** keystore generated on first build after the suite version refresh. Devices on **older StepDaddy** builds signed with a different key must **uninstall** `ar.tvplayer.tv` before installing 2.0.0+.
- **Future releases** must **reuse** the same keystore so `adb install -r` and the in-app updater work without uninstall.
- If the keystore is deleted, `build.sh` auto-generates a new one — treat that as a key rotation; document it in release notes and expect fleet uninstalls.
- CI can inject the keystore via secret `STEPDADDY_KEYSTORE_B64` (see `docs/RELEASE.md`).

Default self-signed credentials: store/key password `stepdaddy`, alias `stepdaddy`.

1. **Signature** — Patch is signed with `out/stepdaddy.keystore` (not the ONN mod cert). Uninstall stock/mod TiViMate before install, or use `adb install -r` if replacing an older StepDaddy build **signed with the same key**.
2. **Gateway first** — Ensure StepDaddy Gateway listens on `127.0.0.1:3000` before first launch; auto-setup runs once per device unless playlist is cleared.
3. **Playlist groups** — After wizard completes, confirm **Manage Groups → sort by order in playlist** (same as manual setup in RE-DEEP-DIVE).
4. **HTTP 4617** — Not exposed off-device by default; use `adb forward` or a fleet agent on-device. No auth on the control port — bind is device-local only in practice.
5. **Upgrade path** — Re-run `./build.sh`, push APK, `adb install -r`. Prefs (`stepdaddy_bridge`) survive unless app data cleared.
6. **5.x official** — This patch targets **4.6.1** only (no DexProtector). Do not mix with official 5.3.3 signature checks.
7. **Rollout script sketch:**

```bash
for DEV in $(adb devices | awk '/device$/{print $1}'); do
  adb -s "$DEV" install -r research/tivimate-apk/TiviMate-4.6.1-StepDaddy.apk
  adb -s "$DEV" shell am start -n ar.tvplayer.tv/.ui.MainActivity
  sleep 5
  adb -s "$DEV" shell 'curl -sf http://127.0.0.1:4617/status' || echo "WARN: $DEV HTTP not up"
done
```

---

## Patch layout

| Path | Role |
|------|------|
| `src/ar/tvplayer/tv/stepdaddy/` | Patch Java sources (`StepDaddyUpdateChecker`, `StepDaddyUpdateDownloader`, `StepDaddyUpdateUi`, …) |
| `apply_hooks.py` | Manifest entries, `MainActivity` / `PlaylistUrlFragment` smali hooks |
| `decoded/` | Apktool decode of base APK |
| `build.sh` | Compile → smali → apktool → sign |
| `out/` | Build artifacts, keystore |

**Smali hooks:**

- `MainActivity.onCreate` → `StepDaddyHooks.onMainActivityCreate`
- `MainActivity.onNewIntent` (added) → `StepDaddyHooks.onMainActivityNewIntent`
- `PlaylistUrlFragment.onViewCreated` → `StepDaddySetup.maybeAutoAdvancePlaylistUrl`
- `PlaylistStatusFragment.onViewCreated` → `StepDaddySetup.maybeAutoAdvancePlaylistStatus`
- `PlaylistTvgUrlFragment.onViewCreated` → `StepDaddySetup.maybeAutoAdvancePlaylistTvgUrl`
- `PlaylistActivity.onDestroy` → `StepDaddySetup.onWizardFinished`
- `MainActivity.onResume` → `StepDaddyHooks.onMainActivityResume` → `StepDaddyPlayer.applyBootTune` (**5 s defer**, `BOOT_TUNE_DELAY_MS`)
- `AboutSettingsFragment.onViewCreated` → `StepDaddyUpdateUi.attachAbout` (update preference + version summary)
- `PlayerFragment` listener `onPlayerError` → `StepDaddyPlayer.onPlaybackError`

### Boot-tune timing (with gateway auto-launch)

| Step | Delay | Owner |
|------|-------|-------|
| Gateway catalog ready | 0 | Gateway HUD |
| Launch TiviMate | +2.5 s | `GatewayHud.LAUNCHER_SETTLE_MS` |
| Save `/boot-tune/{n}` via HTTP | poll 500 ms × 24 | `GatewayHud.scheduleBootTuneWhenPatchReady` |
| Apply tune on resume | +5 s | `StepDaddyPlayer.BOOT_TUNE_DELAY_MS` |

Default boot channel: **51** (gateway `tivimateBootTuneChannel` pref).

**Exported components added:** `StepDaddyBridgeActivity`, `StepDaddyCommandReceiver`.  
**Also exports:** `SettingsActivity`, `PlaylistActivity` (for wizard launch).

See also: `../RE-DEEP-DIVE.md` → **StepDaddy Patched Mod**.
