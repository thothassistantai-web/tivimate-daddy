package ar.tvplayer.tv.stepdaddy;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.text.TextUtils;

public final class StepDaddyHooks {
    private static final long AUTO_SETUP_POLL_INITIAL_MS = 500L;
    private static final long AUTO_SETUP_POLL_INTERVAL_MS = 1_000L;
    private static final long AUTO_SETUP_POLL_MAX_MS = 60_000L;

    private StepDaddyHooks() {
    }

    public static void onMainActivityCreate(Activity activity) {
        StepDaddyActivityHolder.setMainActivity(activity);
        handleIntent(activity, activity.getIntent());
        StepDaddyHttpServerHolder.ensureStarted(activity);
        new Handler(Looper.getMainLooper()).postDelayed(
            new Runnable() {
                @Override
                public void run() {
                    StepDaddyUpdateUi.checkOnAppStart(activity);
                }
            },
            8_000L
        );
        scheduleAutoSetupWhenDbReady(activity);
    }

    public static void onMainActivityNewIntent(Activity activity, Intent intent) {
        handleIntent(activity, intent);
    }

    public static void onMainActivityResume(Activity activity) {
        StepDaddyActivityHolder.setMainActivity(activity);
        StepDaddyUi.scheduleRemoteHintDismiss(activity);
        StepDaddyPlayer.applyBootTune(activity);
    }

    public static void onMainActivityDestroy(Activity activity) {
        StepDaddyActivityHolder.clearMainActivity(activity);
        StepDaddySetup.clearUpgradeSession(activity);
    }

    public static void handleUri(Context context, Uri uri) {
        if (uri == null) {
            return;
        }
        String scheme = uri.getScheme();
        if (StepDaddyConstants.SCHEME.equalsIgnoreCase(scheme)) {
            handleStepDaddyUri(context, uri);
            return;
        }
        if ("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme)) {
            String path = uri.getPath();
            if (path != null && path.contains("tivimate-stream")) {
                int channel = StepDaddyPlayer.parseTivimateStreamChannel(path);
                if (channel > 0) {
                    StepDaddyPlayer.tuneChannel(context, channel);
                }
            }
        }
    }

    public static void handleIntent(Context context, Intent intent) {
        if (intent == null) {
            return;
        }
        if (intent.getBooleanExtra(StepDaddyConstants.EXTRA_INTERNAL, false)) {
            return;
        }
        Uri data = intent.getData();
        if (data != null) {
            handleUri(context, data);
            return;
        }
        String action = intent.getAction();
        if (StepDaddyConstants.ACTION_TUNE.equals(action)) {
            int channel = intent.getIntExtra(StepDaddyConstants.EXTRA_CHANNEL, -1);
            long channelId = intent.getLongExtra(StepDaddyConstants.EXTRA_CHANNEL_ID, -1L);
            if (channelId > 0L) {
                StepDaddyPlayer.tuneChannelId(context, channelId);
            } else if (channel > 0) {
                StepDaddyPlayer.tuneChannel(context, channel);
            }
            return;
        }
        if (StepDaddyConstants.ACTION_STREAM.equals(action)) {
            String streamUrl = intent.getStringExtra(StepDaddyConstants.EXTRA_STREAM_URL);
            if (!TextUtils.isEmpty(streamUrl)) {
                StepDaddyPlayer.openStream(context, streamUrl);
            }
            return;
        }
        if (StepDaddyConstants.ACTION_SETUP.equals(action)) {
            final String base = intent.getStringExtra(StepDaddyConstants.EXTRA_GATEWAY_BASE);
            final Context app = context.getApplicationContext();
            new Thread(new Runnable() {
                @Override
                public void run() {
                    StepDaddySetup.runSetup(app, base);
                }
            }, "stepdaddy-setup-action").start();
            return;
        }
        if (StepDaddyConstants.ACTION_OPEN_EPG.equals(action)) {
            StepDaddyPlayer.openEpgOverlay(context);
        }
    }

    private static void handleStepDaddyUri(Context context, Uri uri) {
        String host = uri.getHost();
        if (host == null) {
            return;
        }
        switch (host) {
            case StepDaddyConstants.HOST_SETUP:
                final String base = uri.getQueryParameter("base");
                final Context app = context.getApplicationContext();
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        StepDaddySetup.runSetup(app, base);
                    }
                }, "stepdaddy-setup-uri").start();
                break;
            case StepDaddyConstants.HOST_CHANNEL: {
                String segment = uri.getLastPathSegment();
                if (segment != null) {
                    try {
                        int channel = Integer.parseInt(segment);
                        StepDaddyPlayer.tuneChannel(context, channel);
                    } catch (NumberFormatException ignored) {
                        StepDaddyLog.w("Invalid channel segment: " + segment);
                    }
                }
                break;
            }
            case StepDaddyConstants.HOST_STREAM: {
                String url = uri.getQueryParameter("url");
                if (!TextUtils.isEmpty(url)) {
                    StepDaddyPlayer.openStream(context, url);
                }
                break;
            }
            case StepDaddyConstants.HOST_STATUS:
                StepDaddyHttpService.ensureStarted(context);
                break;
            default:
                StepDaddyLog.w("Unknown stepdaddy host: " + host);
                break;
        }
    }

    private static void scheduleAutoSetupWhenDbReady(final Activity activity) {
        final Handler handler = new Handler(Looper.getMainLooper());
        final long startedAt = SystemClock.uptimeMillis();
        final Runnable poller = new Runnable() {
            @Override
            public void run() {
                if (StepDaddyDb.isTvPlayerDbReadable(activity)) {
                    StepDaddySetup.detectUpgrade(activity);
                    StepDaddySetup.runAutoSetupIfNeeded(activity);
                    return;
                }
                if (SystemClock.uptimeMillis() - startedAt < AUTO_SETUP_POLL_MAX_MS) {
                    handler.postDelayed(this, AUTO_SETUP_POLL_INTERVAL_MS);
                    return;
                }
                StepDaddyLog.w("TvPlayer.db not readable after " + AUTO_SETUP_POLL_MAX_MS + "ms; running setup checks anyway");
                StepDaddySetup.detectUpgrade(activity);
                StepDaddySetup.runAutoSetupIfNeeded(activity);
            }
        };
        handler.postDelayed(poller, AUTO_SETUP_POLL_INITIAL_MS);
    }
}
