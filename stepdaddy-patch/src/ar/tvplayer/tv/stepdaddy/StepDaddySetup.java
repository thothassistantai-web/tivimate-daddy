package ar.tvplayer.tv.stepdaddy;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Handler;
import android.os.Looper;
import android.os.Parcelable;
import android.os.SystemClock;

import java.lang.reflect.Constructor;
import java.io.File;

public final class StepDaddySetup {
    private static final String PLAYLIST_ACTIVITY =
        "ar.tvplayer.tv.settings.ui.playlists.PlaylistActivity";
    private static final String PLAYLIST_ARGS =
        "ar.tvplayer.tv.settings.ui.playlists.PlaylistActivity$\u058f";

    private static final long POLL_MS = 500L;
    private static final long MAX_WAIT_MS = 180_000L;
    private static final long PLAYLIST_DB_POLL_MS = 2_000L;
    private static final long PLAYLIST_DB_MAX_WAIT_MS = 90_000L;

    private StepDaddySetup() {
    }

    public static void runAutoSetupIfNeeded(Context context) {
        if (!StepDaddyPrefs.isAutoSetupEnabled(context)) {
            return;
        }
        refreshSetupState(context);
        if (StepDaddyPrefs.isSetupDone(context) && hasGatewayPlaylist(context)) {
            StepDaddyLog.i("Setup done + playlist in DB; skipping auto-setup");
            return;
        }
        if (hasGatewayPlaylist(context)) {
            return;
        }
        if (StepDaddyPrefs.isWizardPending(context)) {
            StepDaddyLog.i("Wizard already pending; skipping auto-setup relaunch");
            return;
        }
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    runSetup(context.getApplicationContext(), null);
                } catch (Exception error) {
                    StepDaddyLog.w("Auto-setup failed", error);
                }
            }
        }, "stepdaddy-setup").start();
    }

    public static void runSetup(Context context, String gatewayBaseOverride) {
        Context app = context.getApplicationContext();
        if (StepDaddyPrefs.isWizardPending(app)) {
            StepDaddyLog.i("Wizard already in progress; skipping setup launch");
            return;
        }
        String base = gatewayBaseOverride;
        if (base == null || base.trim().isEmpty()) {
            base = StepDaddyPrefs.gatewayBase(app);
        } else {
            StepDaddyPrefs.setGatewayBase(app, base);
        }
        if (hasGatewayPlaylist(app)) {
            confirmSetupComplete(app);
            StepDaddyLog.i("Gateway playlist already present");
            return;
        }
        StepDaddyPrefs.setSetupDone(app, false);
        String setupUrl = base.replaceAll("/$", "") + "/tivimate-setup";
        String body = StepDaddyHttp.get(setupUrl);
        StepDaddyJson.SetupPayload payload = StepDaddyJson.parseSetup(body);
        if (payload == null) {
            StepDaddyLog.w("Setup payload missing from " + setupUrl);
            return;
        }
        if (hasPlaylistUrl(app, payload.playlistUrl)) {
            confirmSetupComplete(app);
            StepDaddyLog.i("Playlist URL already registered");
            return;
        }
        StepDaddyPrefs.setPendingEpgUrl(app, payload.epgUrls);
        StepDaddyState.setWizardPhase(StepDaddyState.PHASE_URL);
        StepDaddyEvents.emit(app, StepDaddyEvents.WIZARD_STEP, StepDaddyState.PHASE_URL);
        launchPlaylistWizard(app, payload.playlistUrl);
        StepDaddyLog.i("Launched playlist wizard for " + payload.playlistUrl);
    }

    public static void maybeAutoAdvancePlaylistUrl(Object fragment) {
        if (!isStepDaddyWizard(fragment)) {
            return;
        }
        StepDaddyState.setWizardPhase(StepDaddyState.PHASE_URL);
        emitWizardStep(fragment, StepDaddyState.PHASE_URL);
        try {
            Activity activity = (Activity) fragment.getClass().getMethod("getActivity").invoke(fragment);
            if (activity == null) {
                return;
            }
            Class<?> playlistCls = Class.forName(PLAYLIST_ACTIVITY);
            java.lang.reflect.Method getArgs = playlistCls.getDeclaredMethod("\u0780");
            getArgs.setAccessible(true);
            Object args = getArgs.invoke(activity);
            if (args == null) {
                return;
            }
            java.lang.reflect.Field urlField = args.getClass().getDeclaredField("\u0784");
            urlField.setAccessible(true);
            String playlistUrl = (String) urlField.get(args);
            if (playlistUrl == null || playlistUrl.trim().isEmpty()) {
                return;
            }
            java.lang.reflect.Method setUrl = fragment.getClass().getDeclaredMethod(
                "\u058f",
                String.class
            );
            setUrl.setAccessible(true);
            setUrl.invoke(fragment, playlistUrl.trim());
            StepDaddyLog.i("Set playlist URL on wizard");
        } catch (Exception error) {
            StepDaddyLog.w("Set playlist URL failed", error);
        }
        StepDaddyGuidedActions.scheduleAction(
            fragment,
            StepDaddyGuidedActions.ACTION_NEXT,
            350L
        );
    }

    public static void maybeAutoAdvancePlaylistStatus(Object fragment) {
        if (!isStepDaddyWizard(fragment)) {
            return;
        }
        StepDaddyState.setWizardPhase(StepDaddyState.PHASE_STATUS);
        emitWizardStep(fragment, StepDaddyState.PHASE_STATUS);
        StepDaddyGuidedActions.setActionDescription(
            fragment,
            StepDaddyGuidedActions.ACTION_ENTER_NAME,
            "StepDaddy"
        );
        StepDaddyGuidedActions.scheduleActionWhenReady(
            fragment,
            StepDaddyGuidedActions.ACTION_NEXT,
            400L,
            POLL_MS,
            MAX_WAIT_MS,
            1
        );
    }

    public static void maybeAutoAdvancePlaylistTvgUrl(Object fragment) {
        if (!isStepDaddyWizard(fragment)) {
            return;
        }
        StepDaddyState.setWizardPhase(StepDaddyState.PHASE_EPG);
        emitWizardStep(fragment, StepDaddyState.PHASE_EPG);
        try {
            Context context = (Context) fragment.getClass().getMethod("getContext").invoke(fragment);
            if (context != null) {
                String epg = StepDaddyPrefs.pendingEpgUrl(context);
                if (epg != null && !epg.trim().isEmpty()) {
                    java.lang.reflect.Method setUrl = fragment.getClass().getDeclaredMethod(
                        "\u058f",
                        String.class
                    );
                    setUrl.setAccessible(true);
                    setUrl.invoke(fragment, epg.trim());
                    StepDaddyLog.i("Set EPG URL on wizard");
                }
            }
        } catch (Exception error) {
            StepDaddyLog.w("Set EPG URL failed", error);
        }
        StepDaddyGuidedActions.scheduleActionWhenReady(
            fragment,
            StepDaddyGuidedActions.ACTION_DONE,
            500L,
            POLL_MS,
            MAX_WAIT_MS,
            1
        );
    }

    public static void onWizardFinished(Activity activity) {
        if (activity == null) {
            return;
        }
        StepDaddyLog.i("Playlist wizard finished");
        StepDaddyState.setWizardPhase(StepDaddyState.PHASE_IMPORTING);
        StepDaddyEvents.emit(activity, StepDaddyEvents.WIZARD_STEP, StepDaddyState.PHASE_IMPORTING);
        Activity main = StepDaddyActivityHolder.getMainActivity();
        if (main != null) {
            waitForGatewayPlaylistThenContinue(main);
        } else {
            waitForGatewayPlaylistThenContinue(activity.getApplicationContext());
        }
    }

    static void refreshSetupState(Context context) {
        Context app = context.getApplicationContext();
        if (hasGatewayPlaylist(app)) {
            confirmSetupComplete(app);
            return;
        }
        if (StepDaddyPrefs.isSetupDone(app) && !StepDaddyPrefs.isWizardPending(app)) {
            StepDaddyPrefs.setSetupDone(app, false);
            StepDaddyLog.w("setupDone was true but gateway playlist missing; reset");
        }
    }

    static boolean hasGatewayPlaylist(Context context) {
        return hasPlaylistUrl(context, "127.0.0.1:3000")
            || hasPlaylistUrl(context, "tivimate-playlist");
    }

    static void confirmSetupComplete(Context context) {
        if (!hasGatewayPlaylist(context)) {
            StepDaddyLog.w("Refusing setupDone: gateway playlist not in DB");
            return;
        }
        StepDaddyPrefs.setSetupDone(context, true);
        StepDaddyPrefs.setWizardPending(context, false);
        StepDaddyState.setWizardPhase(StepDaddyState.PHASE_DONE);
        StepDaddyEvents.emit(context, StepDaddyEvents.SETUP_COMPLETE);
        StepDaddyEvents.emit(context, StepDaddyEvents.WIZARD_STEP, StepDaddyState.PHASE_DONE);
        StepDaddyLog.i("Setup complete; gateway playlist confirmed in DB");
    }

    private static void waitForGatewayPlaylistThenContinue(final Context context) {
        StepDaddyPrefs.setWizardPending(context, true);
        final Handler handler = new Handler(Looper.getMainLooper());
        final long startedAt = SystemClock.uptimeMillis();
        Runnable poller = new Runnable() {
            @Override
            public void run() {
                refreshSetupState(context);
                if (hasGatewayPlaylist(context)) {
                    Activity main = StepDaddyActivityHolder.getMainActivity();
                    if (main != null) {
                        StepDaddyUi.scheduleRemoteHintDismiss(main);
                    }
                    confirmSetupComplete(context);
                    return;
                }
                if (SystemClock.uptimeMillis() - startedAt < PLAYLIST_DB_MAX_WAIT_MS) {
                    handler.postDelayed(this, PLAYLIST_DB_POLL_MS);
                    return;
                }
                StepDaddyPrefs.setWizardPending(context, false);
                StepDaddyPrefs.setSetupDone(context, false);
                StepDaddyState.setWizardPhase(StepDaddyState.PHASE_IDLE);
                StepDaddyLog.w("Timed out waiting for gateway playlist in DB");
            }
        };
        handler.postDelayed(poller, PLAYLIST_DB_POLL_MS);
    }

    private static void emitWizardStep(Object fragment, String phase) {
        try {
            Context context = (Context) fragment.getClass().getMethod("getContext").invoke(fragment);
            if (context != null) {
                StepDaddyEvents.emit(context, StepDaddyEvents.WIZARD_STEP, phase);
            }
        } catch (Exception ignored) {
        }
    }

    private static boolean isStepDaddyWizard(Object fragment) {
        if (fragment == null) {
            return false;
        }
        try {
            Context context = (Context) fragment.getClass().getMethod("getContext").invoke(fragment);
            if (context == null || !StepDaddyPrefs.isWizardPending(context)) {
                return false;
            }
            Activity activity = (Activity) fragment.getClass().getMethod("getActivity").invoke(fragment);
            return activity != null && PLAYLIST_ACTIVITY.equals(activity.getClass().getName());
        } catch (Exception error) {
            StepDaddyLog.w("Wizard fragment check failed", error);
            return false;
        }
    }

    private static SQLiteDatabase openTvPlayerDbReadOnly(Context context) {
        File dbFile = context.getDatabasePath("TvPlayer.db");
        if (!dbFile.exists() || dbFile.length() == 0L) {
            return null;
        }
        return SQLiteDatabase.openDatabase(
            dbFile.getPath(),
            null,
            SQLiteDatabase.OPEN_READONLY
        );
    }

    private static boolean hasPlaylistUrl(Context context, String needle) {
        SQLiteDatabase database = null;
        Cursor cursor = null;
        try {
            database = openTvPlayerDbReadOnly(context);
            if (database == null) {
                return false;
            }
            cursor = database.rawQuery(
                "SELECT id FROM playlists WHERE url LIKE ? LIMIT 1",
                new String[]{"%" + needle + "%"}
            );
            return cursor != null && cursor.moveToFirst();
        } catch (Exception error) {
            String message = error.getMessage();
            if (message != null
                && (message.contains("no such table") || message.contains("SQLITE_READONLY"))) {
                return false;
            }
            StepDaddyLog.w("Playlist lookup failed", error);
            return false;
        } finally {
            if (cursor != null) {
                cursor.close();
            }
            if (database != null) {
                database.close();
            }
        }
    }

    private static void launchPlaylistWizard(Context context, String playlistUrl) {
        try {
            Class<?> argsClass = Class.forName(PLAYLIST_ARGS);
            Constructor<?> constructor = argsClass.getDeclaredConstructor(
                long.class,
                String.class,
                boolean.class
            );
            constructor.setAccessible(true);
            Parcelable args = (Parcelable) constructor.newInstance(0L, playlistUrl, false);

            Intent intent = new Intent();
            intent.setComponent(new ComponentName(
                context.getPackageName(),
                PLAYLIST_ACTIVITY
            ));
            intent.putExtra(StepDaddyConstants.PLAYLIST_ARGS_KEY, args);
            StepDaddyPrefs.setWizardPending(context, true);
            StepDaddyPrefs.setSetupDone(context, false);
            StepDaddyState.setWizardPhase(StepDaddyState.PHASE_URL);
            Activity host = StepDaddyActivityHolder.getMainActivity();
            if (host != null) {
                host.startActivity(intent);
            } else {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(intent);
            }
        } catch (Exception error) {
            StepDaddyLog.w("Failed to launch playlist wizard", error);
        }
    }
}
