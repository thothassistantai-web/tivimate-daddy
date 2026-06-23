# TiviMate Daddy 2.0.0

- **Base TiViMate:** 4.6.1 (ONN USB premium mod, `ar.tvplayer.tv`)
- **Patch:** 2.0.0
- **APK:** `TiviMate-4.6.1-StepDaddy-2.0.0.apk`
- **Git tag:** `tivimate-daddy-v2.0.0`

## Verify after install

```bash
adb forward tcp:4617 tcp:4617
curl -s http://127.0.0.1:4617/status | jq .patchVersion
# → "2.0.0"
```

See [CHANGELOG.md](../CHANGELOG.md) for full notes.
