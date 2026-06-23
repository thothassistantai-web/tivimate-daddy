# TiViMate Daddy

**TiviMate Daddy** — TiViMate **4.6.1** premium mod with the StepDaddy smali patch for [StepDaddy Gateway](https://github.com/thothassistantai-web/stepdaddy-gateway-android) control on ONN / Android TV sticks.

| | |
|---|---|
| **Package** | `ar.tvplayer.tv` |
| **Patch version** | See [`VERSION`](VERSION) (`PATCH_VERSION`) |
| **Gateway pairing** | HTTP `:4617` on device ↔ Gateway `:3000` |
| **Releases** | [GitHub Releases](https://github.com/thothassistantai-web/tivimate-daddy/releases) (self-signed APK) |

Works **with StepDaddy Gateway** (recommended) or as a standalone patched player (setup still expects a gateway at `http://127.0.0.1:3000`).

---

## Install

Download the latest APK from [Releases](https://github.com/thothassistantai-web/tivimate-daddy/releases/latest):

```bash
adb uninstall ar.tvplayer.tv || true
adb install -r TiviMate-4.6.1-StepDaddy-<patch>.apk
```

Or use **Gateway → Install Apps → TiviMate Daddy** (catalog points at this repo).

**Verify:**

```bash
adb forward tcp:4617 tcp:4617
curl -s http://127.0.0.1:4617/status | jq '{patchVersion, setupDone}'
```

---

## Quick build

```bash
export ANDROID_HOME=~/Android/Sdk
./scripts/build-release.sh
```

| Output | Path |
|--------|------|
| Latest stable APK | `TiviMate-4.6.1-StepDaddy.apk` |
| Versioned release APK | `release/TiviMate-4.6.1-StepDaddy-<patch>.apk` |
| Update manifest | `release/update-manifest.json` |

Patch-only build: `cd stepdaddy-patch && ./build.sh`

See [`docs/RELEASE.md`](docs/RELEASE.md) for version bumps, signing (`stepdaddy.keystore`), and GitHub publish.

---

## Patch layout

| Path | Role |
|------|------|
| [`stepdaddy-patch/src/`](stepdaddy-patch/src/) | Java sources → smali |
| [`stepdaddy-patch/apply_hooks.py`](stepdaddy-patch/apply_hooks.py) | Manifest + hook injection |
| [`stepdaddy-patch/build.sh`](stepdaddy-patch/build.sh) | Compile, merge, sign |
| [`stepdaddy-patch/README.md`](stepdaddy-patch/README.md) | Hook map, version history, update checker |

---

## Related

| Project | Repo |
|---------|------|
| StepDaddy Gateway (Android) | [stepdaddy-gateway-android](https://github.com/thothassistantai-web/stepdaddy-gateway-android) |
| Linux / Docker gateway | [stepdaddy-livehd](https://github.com/thothassistantai-web/stepdaddy-livehd) |

**Note:** TiViMate Daddy releases were previously published on the gateway repo (`tivimate-daddy-v*` tags). New releases are published here only; old gateway tags remain archived.
