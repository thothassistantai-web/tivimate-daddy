package ar.tvplayer.tv.stepdaddy;

public final class StepDaddyConstants {
    public static final String PATCH_VERSION = "1.3.0-about-update";
    public static final int VERSION_CODE = 10300;
    public static final String GITHUB_RELEASE_REPO = "thothassistantai-web/tivimate-daddy";
    public static final String GITHUB_RELEASE_TAG_PREFIX = "tivimate-daddy-v";
    public static final String GITHUB_USER_AGENT = "TiviMate-StepDaddy-Updater/1.3";
    public static final String APK_ASSET_PREFIX = "TiviMate-4.6.1-StepDaddy-";
    public static final String EVENTS_PATH = "/tivimate-events";

    public static final String PREFS = "stepdaddy_bridge";
    public static final String KEY_SETUP_DONE = "setup_done";
    public static final String KEY_AUTO_SETUP = "auto_setup_enabled";
    public static final String KEY_GATEWAY_BASE = "gateway_base";
    public static final String KEY_WIZARD_PENDING = "wizard_pending";
    public static final String KEY_PENDING_EPG_URL = "pending_epg_url";
    public static final String KEY_BOOT_TUNE = "boot_tune_channel";
    public static final String KEY_UPDATE_AVAILABLE = "update_available_version";
    public static final String KEY_UPDATE_AVAILABLE_CODE = "update_available_code";
    public static final String KEY_LAST_UPDATE_CHECK_MS = "last_update_check_ms";

    public static final String ACTION_SETUP = "ar.tvplayer.tv.action.STEPDADDY_SETUP";
    public static final String ACTION_TUNE = "ar.tvplayer.tv.action.STEPDADDY_TUNE";
    public static final String ACTION_STREAM = "ar.tvplayer.tv.action.STEPDADDY_STREAM";
    public static final String ACTION_OPEN_EPG = "ar.tvplayer.tv.action.STEPDADDY_EPG";
    public static final String ACTION_START_HTTP = "ar.tvplayer.tv.action.STEPDADDY_HTTP_START";
    public static final String ACTION_STOP_HTTP = "ar.tvplayer.tv.action.STEPDADDY_HTTP_STOP";

    public static final String EXTRA_CHANNEL = "channel";
    public static final String EXTRA_CHANNEL_ID = "channel_id";
    public static final String EXTRA_STREAM_URL = "stream_url";
    public static final String EXTRA_GATEWAY_BASE = "gateway_base";
    /** Set on intents we generate so MainActivity hooks do not re-handle them. */
    public static final String EXTRA_INTERNAL = "stepdaddy_internal";

    public static final String SCHEME = "stepdaddy";
    public static final String HOST_SETUP = "setup";
    public static final String HOST_CHANNEL = "channel";
    public static final String HOST_STREAM = "stream";
    public static final String HOST_STATUS = "status";

    public static final String DEFAULT_GATEWAY = "http://127.0.0.1:3000";
    public static final int DEFAULT_BOOT_TUNE_CHANNEL = 51;
    public static final int HTTP_PORT = 4617;

    public static final String PLAYLIST_ARGS_KEY = "ar.tvplayer.tv.settings.Args";

    private StepDaddyConstants() {
    }

    public static String githubLatestReleaseApiUrl() {
        return "https://api.github.com/repos/" + GITHUB_RELEASE_REPO + "/releases/latest";
    }

    public static String githubReleasesApiUrl() {
        return "https://api.github.com/repos/" + GITHUB_RELEASE_REPO + "/releases";
    }
}
