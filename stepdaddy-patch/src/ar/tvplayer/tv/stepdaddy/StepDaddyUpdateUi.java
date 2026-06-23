package ar.tvplayer.tv.stepdaddy;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;

import java.io.File;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

public final class StepDaddyUpdateUi {
    private static final long BACKGROUND_CHECK_INTERVAL_MS = 6L * 60L * 60L * 1000L;
    private static final String PREF_CHECK_KEY = "checkForNewVersion";
    private static final String PREF_VERSION_KEY = "version";
    private static final String PREF_STEPDADDY_CHECK_KEY = "stepdaddyCheckUpdate";
    private static final String PREFERENCE_CLASS = "androidx.preference.Preference";
    private static final String PREF_FRAGMENT_CLASS =
        "androidx.preference.PreferenceFragmentCompat";

    // TiViMate obfuscates androidx.preference method names; map the ones we need.
    private static final String OBF_FIND_PREFERENCE = "\u058f";
    private static final String OBF_GET_PREFERENCE_SCREEN = "\u0788";
    private static final String OBF_SET_SUMMARY = "\u0620";
    private static final String OBF_SET_TITLE = "\u058f";
    private static final String OBF_GET_SUMMARY = "\u0787";
    private static final String OBF_SET_ENABLED = "\u0780";
    private static final String OBF_SET_VISIBLE = "\u0782";
    private static final String OBF_SET_KEY = "\u0780";
    private static final String OBF_ADD_PREFERENCE = "\u058f";

    private static volatile boolean manualCheckRunning;

    private StepDaddyUpdateUi() {
    }

    /** Hooked from AboutSettingsFragment.onViewCreated. */
    public static void attachAbout(Object fragment) {
        if (fragment == null) {
            return;
        }
        Context context = fragmentContext(fragment);
        if (context == null) {
            return;
        }
        decorateAboutPreferences(fragment, context);
        maybeBackgroundCheck(context, false);
    }

    /** Optional silent check on app start (hooked from StepDaddyHooks). */
    public static void checkOnAppStart(Context context) {
        if (context == null) {
            return;
        }
        maybeBackgroundCheck(context.getApplicationContext(), true);
    }

    public static void onManualCheck(Object fragment) {
        if (fragment == null || manualCheckRunning) {
            return;
        }
        Context context = fragmentContext(fragment);
        if (context == null) {
            return;
        }
        manualCheckRunning = true;
        setCheckingIndicator(fragment, true);
        new Thread(new Runnable() {
            @Override
            public void run() {
                StepDaddyUpdateChecker.ReleaseInfo release = null;
                String error = null;
                try {
                    release = StepDaddyUpdateChecker.fetchLatest(context);
                } catch (Exception e) {
                    error = e.getMessage();
                    StepDaddyLog.w("Manual update check failed", e);
                }
                final StepDaddyUpdateChecker.ReleaseInfo result = release;
                final String failure = error;
                runOnMain(new Runnable() {
                    @Override
                    public void run() {
                        manualCheckRunning = false;
                        setCheckingIndicator(fragment, false);
                        if (failure != null) {
                            toastDialog(context, "Update check failed", failure);
                            return;
                        }
                        if (result == null) {
                            toastDialog(
                                context,
                                "Update check failed",
                                "Could not reach GitHub or parse the latest release."
                            );
                            return;
                        }
                        rememberRelease(context, result);
                        refreshUpdatePreferenceSummary(fragment, context);
                        if (!result.isNewerThanLocal()) {
                            showInfoDialog(
                                context,
                                "Up to date",
                                buildStatusMessage(result, false)
                            );
                            return;
                        }
                        showUpdateDialog(context, result);
                    }
                });
            }
        }, "stepdaddy-update-check").start();
    }

    private static void maybeBackgroundCheck(Context context, boolean respectCooldown) {
        long now = System.currentTimeMillis();
        if (respectCooldown) {
            long last = StepDaddyPrefs.lastUpdateCheckMs(context);
            if (last > 0L && now - last < BACKGROUND_CHECK_INTERVAL_MS) {
                return;
            }
        }
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    StepDaddyUpdateChecker.ReleaseInfo release =
                        StepDaddyUpdateChecker.fetchLatest(context);
                    StepDaddyPrefs.setLastUpdateCheckMs(context, System.currentTimeMillis());
                    if (release == null) {
                        return;
                    }
                    if (release.isNewerThanLocal()) {
                        rememberRelease(context, release);
                    } else {
                        StepDaddyPrefs.clearUpdateAvailable(context);
                    }
                } catch (Exception error) {
                    StepDaddyLog.w("Background update check failed", error);
                }
            }
        }, "stepdaddy-update-bg").start();
    }

    private static void decorateAboutPreferences(Object fragment, Context context) {
        Object check = findPreference(fragment, PREF_CHECK_KEY);
        if (check == null) {
            check = findPreference(fragment, PREF_STEPDADDY_CHECK_KEY);
        }
        if (check == null) {
            check = createCheckPreference(fragment, context);
        }
        if (check != null) {
            invokePreference(check, OBF_SET_ENABLED, boolean.class, true);
            invokePreference(check, OBF_SET_VISIBLE, boolean.class, true);
            invokePreference(
                check,
                OBF_SET_TITLE,
                CharSequence.class,
                "Check for StepDaddy update"
            );
            refreshUpdatePreferenceSummary(fragment, context);
        }
        Object version = findPreference(fragment, PREF_VERSION_KEY);
        if (version != null) {
            CharSequence base = preferenceSummary(version);
            String baseText = base == null ? "" : base.toString();
            String patchLine = "StepDaddy " + StepDaddyConstants.PATCH_VERSION;
            if (!baseText.contains("StepDaddy")) {
                invokePreference(
                    version,
                    OBF_SET_SUMMARY,
                    CharSequence.class,
                    baseText.isEmpty() ? patchLine : baseText + "\n" + patchLine
                );
            }
        }
    }

    private static Object createCheckPreference(Object fragment, Context context) {
        try {
            Class<?> prefClass = Class.forName(PREFERENCE_CLASS);
            Constructor<?> ctor = prefClass.getConstructor(Context.class);
            Object preference = ctor.newInstance(context);
            invokePreference(preference, OBF_SET_KEY, String.class, PREF_STEPDADDY_CHECK_KEY);
            invokePreference(
                preference,
                OBF_SET_TITLE,
                CharSequence.class,
                "Check for StepDaddy update"
            );
            invokePreference(
                preference,
                OBF_SET_SUMMARY,
                CharSequence.class,
                "Self-signed APK from GitHub"
            );
            Method screenMethod = Class.forName(PREF_FRAGMENT_CLASS).getMethod(
                OBF_GET_PREFERENCE_SCREEN
            );
            Object screen = screenMethod.invoke(fragment);
            if (screen == null) {
                return null;
            }
            Method add = screen.getClass().getMethod(OBF_ADD_PREFERENCE, prefClass);
            add.invoke(screen, preference);
            return preference;
        } catch (Exception error) {
            StepDaddyLog.w("Could not add update preference", error);
            return null;
        }
    }

    private static void refreshUpdatePreferenceSummary(Object fragment, Context context) {
        Object check = findPreference(fragment, PREF_CHECK_KEY);
        if (check == null) {
            check = findPreference(fragment, PREF_STEPDADDY_CHECK_KEY);
        }
        if (check == null) {
            return;
        }
        String available = StepDaddyPrefs.updateAvailableVersion(context);
        int availableCode = StepDaddyPrefs.updateAvailableCode(context);
        String summary;
        if (!available.isEmpty() && availableCode > StepDaddyConstants.VERSION_CODE) {
            summary = "Update available: " + available + " — tap to download";
        } else {
            summary = "Current: " + StepDaddyConstants.PATCH_VERSION
                + " — self-signed GitHub release";
        }
        invokePreference(check, OBF_SET_SUMMARY, CharSequence.class, summary);
    }

    private static void setCheckingIndicator(Object fragment, boolean checking) {
        try {
            Method method = fragment.getClass().getMethod("ؠ", boolean.class);
            method.invoke(fragment, checking);
        } catch (Exception ignored) {
            // About spinner is optional when obfuscated method is unavailable.
        }
    }

    private static Object findPreference(Object fragment, String key) {
        try {
            Method method = fragment.getClass().getMethod(
                OBF_FIND_PREFERENCE,
                CharSequence.class
            );
            return method.invoke(fragment, key);
        } catch (Exception error) {
            StepDaddyLog.w("findPreference failed for " + key, error);
            return null;
        }
    }

    private static CharSequence preferenceSummary(Object preference) {
        try {
            Method method = preference.getClass().getMethod(OBF_GET_SUMMARY);
            Object value = method.invoke(preference);
            return value instanceof CharSequence ? (CharSequence) value : null;
        } catch (Exception error) {
            return null;
        }
    }

    private static void invokePreference(
        Object preference,
        String methodName,
        Class<?> argType,
        Object arg
    ) {
        try {
            Method method = preference.getClass().getMethod(methodName, argType);
            method.invoke(preference, arg);
        } catch (Exception error) {
            StepDaddyLog.w("Preference." + methodName + " failed", error);
        }
    }

    private static Context fragmentContext(Object fragment) {
        try {
            Method requireContext = fragment.getClass().getMethod("requireContext");
            return (Context) requireContext.invoke(fragment);
        } catch (Exception error) {
            try {
                Method getContext = fragment.getClass().getMethod("getContext");
                return (Context) getContext.invoke(fragment);
            } catch (Exception ignored) {
                return null;
            }
        }
    }

    private static void rememberRelease(Context context, StepDaddyUpdateChecker.ReleaseInfo release) {
        if (release.isNewerThanLocal()) {
            StepDaddyPrefs.setUpdateAvailable(context, release.patchVersion, release.versionCode);
        } else {
            StepDaddyPrefs.clearUpdateAvailable(context);
        }
    }

    private static void showUpdateDialog(
        Context context,
        StepDaddyUpdateChecker.ReleaseInfo release
    ) {
        Activity activity = activityFromContext(context);
        if (activity == null || activity.isFinishing()) {
            return;
        }
        String message = buildStatusMessage(release, true)
            + "\n\nThis update is self-signed. You may need to allow "
            + "\"Install unknown apps\" for TiviMate before installing.";
        new AlertDialog.Builder(activity)
            .setTitle("StepDaddy update available")
            .setMessage(message)
            .setPositiveButton("Download & install", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    startDownloadAndInstall(activity, release);
                }
            })
            .setNegativeButton("Later", null)
            .show();
    }

    private static String buildStatusMessage(
        StepDaddyUpdateChecker.ReleaseInfo release,
        boolean includeNotes
    ) {
        StringBuilder builder = new StringBuilder();
        builder.append("Installed: ").append(StepDaddyConstants.PATCH_VERSION);
        builder.append(" (code ").append(StepDaddyConstants.VERSION_CODE).append(")\n");
        builder.append("Latest: ").append(release.patchVersion);
        builder.append(" (code ").append(release.versionCode).append(")\n");
        builder.append("Size: ").append(StepDaddyUpdateChecker.formatSize(release.apkSizeBytes));
        if (includeNotes && release.releaseNotes != null && !release.releaseNotes.isEmpty()) {
            builder.append("\n\n").append(release.releaseNotes);
        }
        return builder.toString();
    }

    private static void startDownloadAndInstall(
        Activity activity,
        StepDaddyUpdateChecker.ReleaseInfo release
    ) {
        if (!StepDaddyUpdateDownloader.canInstallPackages(activity)) {
            Intent settings = StepDaddyUpdateDownloader.buildInstallUnknownAppsSettingsIntent(activity);
            new AlertDialog.Builder(activity)
                .setTitle("Allow unknown apps")
                .setMessage(
                    "Enable \"Install unknown apps\" for TiviMate, then return and try again."
                )
                .setPositiveButton("Open settings", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        if (settings != null) {
                            activity.startActivity(settings);
                        }
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
            return;
        }
        ProgressDialog progress = new ProgressDialog(activity);
        progress.setTitle("Downloading update");
        progress.setMessage("Fetching " + release.patchVersion + "...");
        progress.setIndeterminate(true);
        progress.setCancelable(true);
        progress.setOnCancelListener(new DialogInterface.OnCancelListener() {
            @Override
            public void onCancel(DialogInterface dialog) {
                StepDaddyLog.i("Update download cancelled by user");
            }
        });
        progress.show();
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    String fileName = StepDaddyConstants.APK_ASSET_PREFIX
                        + release.patchVersion + ".apk";
                    File apk = StepDaddyUpdateDownloader.download(
                        activity,
                        release.apkUrl,
                        fileName,
                        new StepDaddyUpdateDownloader.ProgressListener() {
                            @Override
                            public void onProgress(int percent, long downloaded, long total) {
                                runOnMain(new Runnable() {
                                    @Override
                                    public void run() {
                                        if (!progress.isShowing()) {
                                            return;
                                        }
                                        if (percent < 0 || total <= 0L) {
                                            progress.setIndeterminate(true);
                                            return;
                                        }
                                        progress.setIndeterminate(false);
                                        progress.setMax(100);
                                        progress.setProgress(percent);
                                        progress.setMessage(
                                            StepDaddyUpdateChecker.formatSize(downloaded)
                                                + " / "
                                                + StepDaddyUpdateChecker.formatSize(total)
                                        );
                                    }
                                });
                            }
                        }
                    );
                    runOnMain(new Runnable() {
                        @Override
                        public void run() {
                            progress.dismiss();
                            try {
                                StepDaddyUpdateDownloader.installApk(activity, apk);
                            } catch (Exception error) {
                                StepDaddyLog.w("Install intent failed", error);
                                toastDialog(
                                    activity,
                                    "Install failed",
                                    error.getMessage() == null ? "Unknown error" : error.getMessage()
                                );
                            }
                        }
                    });
                } catch (Exception error) {
                    StepDaddyLog.w("Update download failed", error);
                    runOnMain(new Runnable() {
                        @Override
                        public void run() {
                            progress.dismiss();
                            toastDialog(
                                activity,
                                "Download failed",
                                error.getMessage() == null ? "Network error" : error.getMessage()
                            );
                        }
                    });
                }
            }
        }, "stepdaddy-update-download").start();
    }

    private static void showInfoDialog(Context context, String title, String message) {
        Activity activity = activityFromContext(context);
        if (activity == null || activity.isFinishing()) {
            return;
        }
        new AlertDialog.Builder(activity)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("OK", null)
            .show();
    }

    private static void toastDialog(Context context, String title, String message) {
        showInfoDialog(context, title, message);
    }

    private static Activity activityFromContext(Context context) {
        if (context instanceof Activity) {
            return (Activity) context;
        }
        return null;
    }

    private static void runOnMain(Runnable runnable) {
        new Handler(Looper.getMainLooper()).post(runnable);
    }
}
