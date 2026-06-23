package ar.tvplayer.tv.stepdaddy;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class StepDaddyUpdateChecker {
    private static final Pattern SEMVER_PREFIX =
        Pattern.compile("^(\\d+)\\.(\\d+)\\.(\\d+)");
    private static final Pattern VERSION_CODE_IN_BODY =
        Pattern.compile("VERSION_CODE\\s*=\\s*(\\d+)", Pattern.CASE_INSENSITIVE);

    public static final class ReleaseInfo {
        public final String patchVersion;
        public final int versionCode;
        public final String tagName;
        public final String releaseNotes;
        public final String apkUrl;
        public final long apkSizeBytes;

        ReleaseInfo(
            String patchVersion,
            int versionCode,
            String tagName,
            String releaseNotes,
            String apkUrl,
            long apkSizeBytes
        ) {
            this.patchVersion = patchVersion;
            this.versionCode = versionCode;
            this.tagName = tagName;
            this.releaseNotes = releaseNotes == null ? "" : releaseNotes;
            this.apkUrl = apkUrl;
            this.apkSizeBytes = apkSizeBytes;
        }

        public boolean isNewerThanLocal() {
            return versionCode > StepDaddyConstants.VERSION_CODE;
        }
    }

    private StepDaddyUpdateChecker() {
    }

    public static ReleaseInfo fetchLatest(Context context) {
        String body = githubGet(StepDaddyConstants.githubLatestReleaseApiUrl());
        ReleaseInfo release = parseRelease(body);
        if (release != null && matchesStepDaddyTag(release.tagName)) {
            return release;
        }
        return fetchLatestTaggedRelease();
    }

    private static ReleaseInfo fetchLatestTaggedRelease() {
        String body = githubGet(StepDaddyConstants.githubReleasesApiUrl() + "?per_page=20");
        if (body == null || body.trim().isEmpty()) {
            return null;
        }
        try {
            JSONArray releases = new JSONArray(body);
            for (int i = 0; i < releases.length(); i++) {
                JSONObject item = releases.getJSONObject(i);
                String tag = item.optString("tag_name", "");
                if (!matchesStepDaddyTag(tag)) {
                    continue;
                }
                ReleaseInfo info = parseRelease(item.toString());
                if (info != null) {
                    return info;
                }
            }
        } catch (Exception error) {
            StepDaddyLog.w("Failed to scan GitHub releases", error);
        }
        return null;
    }

    static boolean matchesStepDaddyTag(String tag) {
        return tag != null
            && tag.startsWith(StepDaddyConstants.GITHUB_RELEASE_TAG_PREFIX);
    }

    static ReleaseInfo parseRelease(String body) {
        if (body == null || body.trim().isEmpty()) {
            return null;
        }
        try {
            JSONObject json = new JSONObject(body);
            String tagName = json.optString("tag_name", "");
            String name = json.optString("name", "");
            String releaseBody = json.optString("body", "");
            String patchVersion = patchVersionFromTag(tagName);
            if (patchVersion.isEmpty()) {
                patchVersion = patchVersionFromTag(name);
            }
            if (patchVersion.isEmpty()) {
                StepDaddyLog.w("Release tag has no patch version: " + tagName);
                return null;
            }
            int versionCode = versionCodeFromBody(releaseBody);
            if (versionCode <= 0) {
                versionCode = versionCodeFromPatchVersion(patchVersion);
            }
            AssetMatch asset = pickApkAsset(json.optJSONArray("assets"), patchVersion);
            if (asset == null || asset.url.isEmpty()) {
                StepDaddyLog.w("No APK asset in release " + tagName);
                return null;
            }
            return new ReleaseInfo(
                patchVersion,
                versionCode,
                tagName,
                snippet(releaseBody),
                asset.url,
                asset.sizeBytes
            );
        } catch (Exception error) {
            StepDaddyLog.w("Failed to parse GitHub release JSON", error);
            return null;
        }
    }

    static String patchVersionFromTag(String tagOrName) {
        if (tagOrName == null || tagOrName.isEmpty()) {
            return "";
        }
        String value = tagOrName.trim();
        String prefix = StepDaddyConstants.GITHUB_RELEASE_TAG_PREFIX;
        if (value.startsWith(prefix)) {
            return value.substring(prefix.length());
        }
        if (value.startsWith("v") && value.length() > 1) {
            return value.substring(1);
        }
        return value;
    }

    static int versionCodeFromBody(String body) {
        if (body == null) {
            return 0;
        }
        Matcher matcher = VERSION_CODE_IN_BODY.matcher(body);
        if (matcher.find()) {
            try {
                return Integer.parseInt(matcher.group(1));
            } catch (NumberFormatException ignored) {
                return 0;
            }
        }
        return 0;
    }

    public static int versionCodeFromPatchVersion(String patchVersion) {
        if (patchVersion == null) {
            return 0;
        }
        Matcher matcher = SEMVER_PREFIX.matcher(patchVersion.trim());
        if (!matcher.find()) {
            return 0;
        }
        int major = Integer.parseInt(matcher.group(1));
        int minor = Integer.parseInt(matcher.group(2));
        int patch = Integer.parseInt(matcher.group(3));
        return major * 10_000 + minor * 100 + patch;
    }

    private static final class AssetMatch {
        final String url;
        final long sizeBytes;

        AssetMatch(String url, long sizeBytes) {
            this.url = url;
            this.sizeBytes = sizeBytes;
        }
    }

    private static AssetMatch pickApkAsset(JSONArray assets, String patchVersion) {
        if (assets == null || assets.length() == 0) {
            return null;
        }
        String preferredName = StepDaddyConstants.APK_ASSET_PREFIX + patchVersion + ".apk";
        AssetMatch fallback = null;
        for (int i = 0; i < assets.length(); i++) {
            JSONObject asset = assets.optJSONObject(i);
            if (asset == null) {
                continue;
            }
            String name = asset.optString("name", "");
            if (!name.endsWith(".apk")) {
                continue;
            }
            String url = asset.optString("browser_download_url", "");
            long size = asset.optLong("size", 0L);
            if (preferredName.equals(name)) {
                return new AssetMatch(url, size);
            }
            if (fallback == null) {
                fallback = new AssetMatch(url, size);
            }
        }
        return fallback;
    }

    private static String snippet(String body) {
        if (body == null) {
            return "";
        }
        String trimmed = body.trim();
        if (trimmed.length() <= 280) {
            return trimmed;
        }
        return trimmed.substring(0, 277) + "...";
    }

    private static String githubGet(String url) {
        Map<String, String> headers = new HashMap<>();
        headers.put("Accept", "application/vnd.github+json");
        headers.put("User-Agent", StepDaddyConstants.GITHUB_USER_AGENT);
        return StepDaddyHttp.get(url, headers);
    }

    public static String formatSize(long bytes) {
        if (bytes <= 0L) {
            return "unknown size";
        }
        if (bytes < 1024L) {
            return bytes + " B";
        }
        if (bytes < 1024L * 1024L) {
            return String.format("%.1f KB", bytes / 1024.0);
        }
        return String.format("%.1f MB", bytes / (1024.0 * 1024.0));
    }
}
