package ar.tvplayer.tv.stepdaddy;

import android.content.Context;
import android.content.SharedPreferences;

final class StepDaddyPrefs {
    private StepDaddyPrefs() {
    }

    static SharedPreferences prefs(Context context) {
        return context.getApplicationContext()
            .getSharedPreferences(StepDaddyConstants.PREFS, Context.MODE_PRIVATE);
    }

    static boolean isSetupDone(Context context) {
        return prefs(context).getBoolean(StepDaddyConstants.KEY_SETUP_DONE, false);
    }

    static void setSetupDone(Context context, boolean done) {
        prefs(context).edit().putBoolean(StepDaddyConstants.KEY_SETUP_DONE, done).apply();
    }

    static boolean isAutoSetupEnabled(Context context) {
        return prefs(context).getBoolean(StepDaddyConstants.KEY_AUTO_SETUP, true);
    }

    static String gatewayBase(Context context) {
        return prefs(context).getString(
            StepDaddyConstants.KEY_GATEWAY_BASE,
            StepDaddyConstants.DEFAULT_GATEWAY
        );
    }

    static void setGatewayBase(Context context, String base) {
        if (base == null || base.trim().isEmpty()) {
            return;
        }
        prefs(context).edit().putString(StepDaddyConstants.KEY_GATEWAY_BASE, base.trim()).apply();
    }

    static boolean isWizardPending(Context context) {
        return prefs(context).getBoolean(StepDaddyConstants.KEY_WIZARD_PENDING, false);
    }

    static void setWizardPending(Context context, boolean pending) {
        prefs(context).edit().putBoolean(StepDaddyConstants.KEY_WIZARD_PENDING, pending).apply();
    }

    static String pendingEpgUrl(Context context) {
        return prefs(context).getString(StepDaddyConstants.KEY_PENDING_EPG_URL, "");
    }

    static void setPendingEpgUrl(Context context, String url) {
        prefs(context).edit().putString(StepDaddyConstants.KEY_PENDING_EPG_URL, url == null ? "" : url).apply();
    }

    static int bootTuneChannel(Context context) {
        return prefs(context).getInt(StepDaddyConstants.KEY_BOOT_TUNE, -1);
    }

    static void setBootTuneChannel(Context context, int channel) {
        prefs(context).edit().putInt(StepDaddyConstants.KEY_BOOT_TUNE, channel).apply();
    }

    static void clearBootTuneChannel(Context context) {
        prefs(context).edit().remove(StepDaddyConstants.KEY_BOOT_TUNE).apply();
    }

    static void setUpdateAvailable(Context context, String patchVersion, int versionCode) {
        prefs(context).edit()
            .putString(StepDaddyConstants.KEY_UPDATE_AVAILABLE, patchVersion == null ? "" : patchVersion)
            .putInt(StepDaddyConstants.KEY_UPDATE_AVAILABLE_CODE, versionCode)
            .apply();
    }

    static String updateAvailableVersion(Context context) {
        return prefs(context).getString(StepDaddyConstants.KEY_UPDATE_AVAILABLE, "");
    }

    static int updateAvailableCode(Context context) {
        return prefs(context).getInt(StepDaddyConstants.KEY_UPDATE_AVAILABLE_CODE, 0);
    }

    static void clearUpdateAvailable(Context context) {
        prefs(context).edit()
            .remove(StepDaddyConstants.KEY_UPDATE_AVAILABLE)
            .remove(StepDaddyConstants.KEY_UPDATE_AVAILABLE_CODE)
            .apply();
    }

    static long lastUpdateCheckMs(Context context) {
        return prefs(context).getLong(StepDaddyConstants.KEY_LAST_UPDATE_CHECK_MS, 0L);
    }

    static void setLastUpdateCheckMs(Context context, long whenMs) {
        prefs(context).edit().putLong(StepDaddyConstants.KEY_LAST_UPDATE_CHECK_MS, whenMs).apply();
    }
}
