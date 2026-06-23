#!/usr/bin/env python3
"""Inject StepDaddy manifest entries and smali hooks into decoded TiViMate APK."""

from __future__ import annotations

import re
import sys
from pathlib import Path


def read_text(path: Path) -> str:
    return path.read_text(encoding="utf-8")


def write_text(path: Path, content: str) -> None:
    path.write_text(content, encoding="utf-8")


def patch_manifest(manifest: str) -> str:
    bridge_activity = """
        <activity android:exported="true" android:name="ar.tvplayer.tv.stepdaddy.StepDaddyBridgeActivity" android:theme="@android:style/Theme.Translucent.NoTitleBar">
            <intent-filter>
                <action android:name="android.intent.action.VIEW"/>
                <category android:name="android.intent.category.DEFAULT"/>
                <category android:name="android.intent.category.BROWSABLE"/>
                <data android:scheme="stepdaddy" android:host="setup"/>
                <data android:scheme="stepdaddy" android:host="channel"/>
                <data android:scheme="stepdaddy" android:host="stream"/>
                <data android:scheme="stepdaddy" android:host="status"/>
            </intent-filter>
            <intent-filter>
                <action android:name="ar.tvplayer.tv.action.STEPDADDY_SETUP"/>
                <action android:name="ar.tvplayer.tv.action.STEPDADDY_TUNE"/>
                <action android:name="ar.tvplayer.tv.action.STEPDADDY_STREAM"/>
                <action android:name="ar.tvplayer.tv.action.STEPDADDY_EPG"/>
                <category android:name="android.intent.category.DEFAULT"/>
            </intent-filter>
        </activity>
        <receiver android:exported="true" android:name="ar.tvplayer.tv.stepdaddy.StepDaddyCommandReceiver">
            <intent-filter>
                <action android:name="ar.tvplayer.tv.action.STEPDADDY_SETUP"/>
                <action android:name="ar.tvplayer.tv.action.STEPDADDY_TUNE"/>
                <action android:name="ar.tvplayer.tv.action.STEPDADDY_STREAM"/>
                <action android:name="ar.tvplayer.tv.action.STEPDADDY_EPG"/>
                <action android:name="ar.tvplayer.tv.action.STEPDADDY_HTTP_START"/>
                <action android:name="ar.tvplayer.tv.action.STEPDADDY_HTTP_STOP"/>
            </intent-filter>
        </receiver>
        <receiver android:exported="true" android:name="ar.tvplayer.tv.stepdaddy.StepDaddyPackageReceiver">
            <intent-filter>
                <action android:name="android.intent.action.MY_PACKAGE_REPLACED"/>
                <data android:scheme="package"/>
            </intent-filter>
        </receiver>
        <service android:exported="false" android:name="ar.tvplayer.tv.stepdaddy.StepDaddyHttpService"/>"""

    package_receiver = """
        <receiver android:exported="true" android:name="ar.tvplayer.tv.stepdaddy.StepDaddyPackageReceiver">
            <intent-filter>
                <action android:name="android.intent.action.MY_PACKAGE_REPLACED"/>
                <data android:scheme="package"/>
            </intent-filter>
        </receiver>"""

    main_intent_filter = """
            <intent-filter>
                <action android:name="android.intent.action.VIEW"/>
                <category android:name="android.intent.category.DEFAULT"/>
                <category android:name="android.intent.category.BROWSABLE"/>
                <data android:scheme="http"/>
                <data android:scheme="https"/>
                <data android:host="127.0.0.1"/>
                <data android:pathPattern=".*\\.m3u8"/>
            </intent-filter>"""

    manifest = manifest.replace(
        'android:label="Tivimate "',
        'android:label="TiviMate StepDaddy"',
        1,
    )

    if 'pathPattern=".*\\.m3u8"' not in manifest:
        manifest = manifest.replace(
            "</activity>\n        <provider android:authorities=\"ar.tvplayer.tv.fileprovider\"",
            main_intent_filter + "\n        </activity>\n        <provider android:authorities=\"ar.tvplayer.tv.fileprovider\"",
            1,
        )

    if "StepDaddyBridgeActivity" not in manifest:
        manifest = manifest.replace(
            "<service android:name=\"ar.tvplayer.tv.commons.RestartAppService\"/>",
            bridge_activity + "\n        <service android:name=\"ar.tvplayer.tv.commons.RestartAppService\"/>",
            1,
        )
    elif "StepDaddyPackageReceiver" not in manifest:
        manifest = manifest.replace(
            "<service android:name=\"ar.tvplayer.tv.commons.RestartAppService\"/>",
            package_receiver + "\n        <service android:name=\"ar.tvplayer.tv.commons.RestartAppService\"/>",
            1,
        )

    settings_export = [
        "ar.tvplayer.tv.settings.ui.SettingsActivity",
        "ar.tvplayer.tv.settings.ui.playlists.PlaylistActivity",
    ]
    for activity in settings_export:
        if f'android:name="{activity}"' not in manifest:
            continue
        if re.search(
            rf'android:exported="[^"]+"\s+android:name="{re.escape(activity)}"',
            manifest,
        ):
            continue
        manifest = manifest.replace(
            f'android:name="{activity}"',
            f'android:exported="true" android:name="{activity}"',
            1,
        )

    return manifest


def patch_main_activity(smali: str) -> str:
    hook_create = """
    invoke-static {p0}, Lar/tvplayer/tv/stepdaddy/StepDaddyHooks;->onMainActivityCreate(Landroid/app/Activity;)V"""

    if "StepDaddyHooks;->onMainActivityCreate" not in smali:
        marker = ".method public onCreate(Landroid/os/Bundle;)V"
        idx = smali.find(marker)
        if idx == -1:
            raise RuntimeError("MainActivity.onCreate not found")
        super_match = re.search(
            r"invoke-super \{p0, p1\}, (L[^;]+;)->onCreate\(Landroid/os/Bundle;\)V",
            smali[idx:],
        )
        if not super_match:
            raise RuntimeError("MainActivity.onCreate super call not found")
        super_line = super_match.group(0)
        super_idx = smali.find(super_line, idx)
        line_end = smali.find("\n", super_idx)
        smali = smali[: line_end + 1] + hook_create + smali[line_end + 1 :]

    if "onMainActivityNewIntent" not in smali:
        super_match = re.search(
            r"invoke-super \{p0, p1\}, (L[^;]+;)->onCreate\(Landroid/os/Bundle;\)V",
            smali,
        )
        if not super_match:
            raise RuntimeError("Could not infer MainActivity superclass")
        parent = super_match.group(1)
        new_intent_method = f"""

.method public onNewIntent(Landroid/content/Intent;)V
    .locals 0

    invoke-super {{p0, p1}}, {parent}->onNewIntent(Landroid/content/Intent;)V

    invoke-virtual {{p0, p1}}, Landroid/app/Activity;->setIntent(Landroid/content/Intent;)V

    invoke-static {{p0, p1}}, Lar/tvplayer/tv/stepdaddy/StepDaddyHooks;->onMainActivityNewIntent(Landroid/app/Activity;Landroid/content/Intent;)V

    return-void
.end method
"""
        smali = smali.rstrip() + new_intent_method

    return smali


def patch_playlist_url_fragment(smali: str) -> str:
    hook = """
    invoke-static {p0}, Lar/tvplayer/tv/stepdaddy/StepDaddySetup;->maybeAutoAdvancePlaylistUrl(Ljava/lang/Object;)V"""

    if "maybeAutoAdvancePlaylistUrl" in smali:
        return smali

    marker = "invoke-super {p0, p1, p2}, Lar/tvplayer/tv/commons/ui/BaseGuidedStepFragment;->onViewCreated(Landroid/view/View;Landroid/os/Bundle;)V"
    if marker not in smali:
        raise RuntimeError("PlaylistUrlFragment.onViewCreated marker not found")
    return smali.replace(marker, marker + hook, 1)


def patch_guided_fragment(smali: str, hook_method: str) -> str:
    hook = f"""
    invoke-static {{p0}}, Lar/tvplayer/tv/stepdaddy/StepDaddySetup;->{hook_method}(Ljava/lang/Object;)V"""
    if hook_method in smali:
        return smali
    marker = "invoke-super {p0, p1, p2}, Lar/tvplayer/tv/commons/ui/BaseGuidedStepFragment;->onViewCreated(Landroid/view/View;Landroid/os/Bundle;)V"
    if marker not in smali:
        raise RuntimeError(f"{hook_method} onViewCreated marker not found")
    return smali.replace(marker, marker + hook, 1)


def patch_playlist_activity_destroy(smali: str) -> str:
    if "onWizardFinished" in smali:
        return smali
    destroy_method = """

.method public onDestroy()V
    .locals 0

    invoke-static {p0}, Lar/tvplayer/tv/stepdaddy/StepDaddySetup;->onWizardFinished(Landroid/app/Activity;)V

    invoke-super {p0}, Lம;->onDestroy()V

    return-void
.end method
"""
    return smali.rstrip() + destroy_method


def patch_player_fragment_error(smali: str) -> str:
    hook = """
    invoke-static {v0, p1}, Lar/tvplayer/tv/stepdaddy/StepDaddyPlayer;->onPlaybackError(Ljava/lang/Object;Ljava/lang/Object;)V"""
    marker = "onPlayerError, e = "
    if "StepDaddyPlayer;->onPlaybackError" in smali:
        return smali
    idx = smali.find(marker)
    if idx == -1:
        return smali
    iget_idx = smali.find(
        "iget-object v0, p0, Lar/tvplayer/tv/player/ui/PlayerFragment$",
        idx,
    )
    if iget_idx == -1:
        return smali
    line_end = smali.find("\n", iget_idx)
    return smali[: line_end + 1] + hook + smali[line_end + 1 :]


def patch_main_activity_destroy(smali: str) -> str:
    if "onMainActivityDestroy" in smali:
        return smali
    marker = "invoke-super {p0}, Lߣ;->onDestroy()V"
    if marker not in smali:
        marker_match = re.search(
            r"invoke-super \{p0\}, (L[^;]+;)->onDestroy\(\)V",
            smali,
        )
        if not marker_match:
            raise RuntimeError("MainActivity onDestroy marker not found")
        marker = marker_match.group(0)
    hook = f"""
    invoke-static {{p0}}, Lar/tvplayer/tv/stepdaddy/StepDaddyHooks;->onMainActivityDestroy(Landroid/app/Activity;)V"""
    return smali.replace(marker, marker + hook, 1)


def patch_main_activity_resume(smali: str) -> str:
    if "onMainActivityResume" in smali:
        return smali
    super_match = re.search(
        r"invoke-super \{p0, p1\}, (L[^;]+;)->onCreate\(Landroid/os/Bundle;\)V",
        smali,
    )
    if not super_match:
        raise RuntimeError("Could not infer MainActivity superclass")
    parent = super_match.group(1)
    resume_method = f"""

.method public onResume()V
    .locals 0

    invoke-super {{p0}}, {parent}->onResume()V

    invoke-static {{p0}}, Lar/tvplayer/tv/stepdaddy/StepDaddyHooks;->onMainActivityResume(Landroid/app/Activity;)V

    return-void
.end method
"""
    return smali.rstrip() + resume_method


def patch_about_settings_fragment(smali: str) -> str:
    hook = """
    invoke-static {p0}, Lar/tvplayer/tv/stepdaddy/StepDaddyUpdateUi;->attachAbout(Ljava/lang/Object;)V"""

    if "StepDaddyUpdateUi;->attachAbout" not in smali:
        marker = (
            "invoke-super {p0, p1, p2}, "
            "Landroidx/leanback/preference/LeanbackPreferenceFragmentCompat;->onViewCreated"
            "(Landroid/view/View;Landroid/os/Bundle;)V"
        )
        if marker not in smali:
            raise RuntimeError("AboutSettingsFragment.onViewCreated marker not found")
        smali = smali.replace(marker, marker + hook, 1)

    return patch_about_check_for_update_click(smali)


def patch_about_check_for_update_click(smali: str) -> str:
    """Route stock checkForNewVersion preference to StepDaddy GitHub updater."""
    if "StepDaddyUpdateUi;->onManualCheck" in smali:
        return smali

    native_check = """    invoke-virtual {p0, v2}, Lar/tvplayer/tv/settings/ui/about/AboutSettingsFragment;->ؠ(Z)V

    invoke-virtual {p0}, Lar/tvplayer/tv/settings/ui/about/AboutSettingsFragment;->މ()Lଷ;

    move-result-object p1

    invoke-static {p0}, Lથ;->ؠ(Landroidx/fragment/app/Fragment;)I

    move-result v1

    if-eqz p1, :cond_2

    new-instance v0, Lঈ;

    invoke-direct {v0, p1}, Lঈ;-><init>(Lଷ;)V

    new-array p1, v3, [Lm70;

    invoke-static {v1}, Ljava/lang/Integer;->valueOf(I)Ljava/lang/Integer;

    move-result-object v1

    new-instance v3, Lm70;

    const-string v5, "versionCode"

    invoke-direct {v3, v5, v1}, Lm70;-><init>(Ljava/lang/Object;Ljava/lang/Object;)V

    aput-object v3, p1, v4

    new-instance v1, Lm70;

    const-string v3, ""

    const-string v4, "deviceObjectId"

    invoke-direct {v1, v4, v3}, Lm70;-><init>(Ljava/lang/Object;Ljava/lang/Object;)V

    aput-object v1, p1, v2

    invoke-static {p1}, Lx70;->֏([Lm70;)Ljava/util/Map;

    move-result-object p1

    new-instance v1, Lચ;

    invoke-direct {v1, v0}, Lચ;-><init>(Ll90;)V

    const-string v0, "checkAppInfo_v2"

    invoke-static {v0, p1, v1}, Lcom/parse/ParseCloud;->callFunctionInBackground(Ljava/lang/String;Ljava/util/Map;Lcom/parse/FunctionCallback;)V

    goto/16 :goto_15"""

    stepdaddy_check = """    invoke-static {p0}, Lar/tvplayer/tv/stepdaddy/StepDaddyUpdateUi;->onManualCheck(Ljava/lang/Object;)V

    goto/16 :goto_15"""

    if native_check not in smali:
        raise RuntimeError("AboutSettingsFragment checkForNewVersion handler not found")
    return smali.replace(native_check, stepdaddy_check, 1)


def main() -> None:
    if len(sys.argv) != 2:
        print(f"usage: {sys.argv[0]} <decoded-apk-dir>", file=sys.stderr)
        sys.exit(1)

    decoded = Path(sys.argv[1])
    manifest_path = decoded / "AndroidManifest.xml"
    main_activity_path = decoded / "smali/ar/tvplayer/tv/ui/MainActivity.smali"
    playlist_fragment_path = (
        decoded / "smali/ar/tvplayer/tv/settings/ui/playlists/PlaylistUrlFragment.smali"
    )
    playlist_activity_path = decoded / "smali/ar/tvplayer/tv/settings/ui/playlists/PlaylistActivity.smali"
    status_fragment_path = (
        decoded / "smali/ar/tvplayer/tv/settings/ui/playlists/PlaylistStatusFragment.smali"
    )
    tvg_fragment_path = (
        decoded / "smali/ar/tvplayer/tv/settings/ui/playlists/PlaylistTvgUrlFragment.smali"
    )
    player_error_path = (
        decoded / "smali/ar/tvplayer/tv/player/ui/PlayerFragment$ޡ.smali"
    )
    about_fragment_path = (
        decoded / "smali/ar/tvplayer/tv/settings/ui/about/AboutSettingsFragment.smali"
    )

    manifest = patch_manifest(read_text(manifest_path))
    write_text(manifest_path, manifest)

    main_activity = patch_main_activity(read_text(main_activity_path))
    main_activity = patch_main_activity_resume(main_activity)
    main_activity = patch_main_activity_destroy(main_activity)
    write_text(main_activity_path, main_activity)

    playlist_fragment = patch_playlist_url_fragment(read_text(playlist_fragment_path))
    write_text(playlist_fragment_path, playlist_fragment)

    status_fragment = patch_guided_fragment(
        read_text(status_fragment_path),
        "maybeAutoAdvancePlaylistStatus",
    )
    write_text(status_fragment_path, status_fragment)

    tvg_fragment = patch_guided_fragment(
        read_text(tvg_fragment_path),
        "maybeAutoAdvancePlaylistTvgUrl",
    )
    write_text(tvg_fragment_path, tvg_fragment)

    playlist_activity = patch_playlist_activity_destroy(read_text(playlist_activity_path))
    write_text(playlist_activity_path, playlist_activity)

    if player_error_path.exists():
        player_error = patch_player_fragment_error(read_text(player_error_path))
        write_text(player_error_path, player_error)

    if about_fragment_path.exists():
        about_fragment = patch_about_settings_fragment(read_text(about_fragment_path))
        write_text(about_fragment_path, about_fragment)

    print("Applied StepDaddy hooks")


if __name__ == "__main__":
    main()
