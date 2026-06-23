# TiviMate Daddy 2.0.0

- **Base TiViMate:** 4.6.1 (ONN USB premium mod, `ar.tvplayer.tv`)
- **Patch:** 2.0.0
- **APK:** `TiviMate-4.6.1-StepDaddy-2.0.0.apk`
- **Git tag:** `tivimate-daddy-v2.0.0`

## Signing key change (read before install)

This release is signed with a **new** `stepdaddy.keystore` (self-signed). If you already have **StepDaddy TiviMate** from a pre-2.0.0 build, **uninstall** the app first (`adb uninstall ar.tvplayer.tv` or Settings → Apps), then install this APK. Android does not allow in-place upgrades when the signing certificate changes.

Future releases from this repo will reuse the same keystore so `adb install -r` upgrades work again.

## Verify after install

```bash
adb forward tcp:4617 tcp:4617
curl -s http://127.0.0.1:4617/status | jq .patchVersion
# → "2.0.0"
```

See [CHANGELOG.md](../CHANGELOG.md) for full notes.
