package ar.tvplayer.tv.stepdaddy;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.view.KeyEvent;

import java.lang.reflect.Method;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class StepDaddyPlayer {
    /** MainActivity.ނ(Z) — hide TV guide and expand player to fullscreen. */
    private static final String MAIN_EXPAND_PLAYER = "\u0782";
    private static final long FULLSCREEN_DELAY_MS = 200L;
    /** Defer boot-tune until Room finishes WAL recovery (parallel SQLite crashes). */
    private static final long BOOT_TUNE_DELAY_MS = 5_000L;

    private static final Pattern TIVIMATE_STREAM_CHANNEL =
        Pattern.compile("/tivimate-stream/(\\d+)\\.m3u8");

    private static volatile boolean bootTuneScheduled;
    private static volatile long lastTuneAtMs;
    private static volatile int lastTuneChannel = -1;
    private static volatile long lastChannelId = -1L;
    private static volatile int lastChannelNo = -1;
    private static volatile String lastChannelName = "";
    private static volatile boolean playingTracked;

    private StepDaddyPlayer() {
    }

    public static long lastChannelId() {
        return lastChannelId;
    }

    public static int lastChannelNo() {
        return lastChannelNo;
    }

    public static String lastChannelName() {
        return lastChannelName;
    }

    public static boolean isPlayingTracked() {
        return playingTracked;
    }

    public static int parseTivimateStreamChannel(String path) {
        if (path == null) {
            return -1;
        }
        Matcher matcher = TIVIMATE_STREAM_CHANNEL.matcher(path);
        if (!matcher.find()) {
            return -1;
        }
        try {
            return Integer.parseInt(matcher.group(1));
        } catch (NumberFormatException ignored) {
            return -1;
        }
    }

    public static boolean tuneChannel(Context context, int channelNumber) {
        if (channelNumber <= 0) {
            return false;
        }
        long now = System.currentTimeMillis();
        if (channelNumber == lastTuneChannel && now - lastTuneAtMs < 1500L) {
            StepDaddyLog.i("Skipping duplicate tune channel=" + channelNumber);
            return true;
        }
        lastTuneChannel = channelNumber;
        lastTuneAtMs = now;
        long channelId = StepDaddyDb.lookupChannelId(context, channelNumber);
        recordChannel(context, channelId, channelNumber);
        Activity activity = asActivity(context);
        if (channelId > 0L && activity != null) {
            if (openFromTvGuide(activity, channelId, true)) {
                scheduleFullscreen(activity);
                onTuneSuccess(context, channelId, channelNumber);
                StepDaddyLog.i("Tuned via tv guide channelId=" + channelId);
                return true;
            }
            StepDaddyLog.w("openFromTvGuide failed for channelId=" + channelId);
        } else if (channelId <= 0L) {
            StepDaddyLog.w("No DB channel for tvg_ch_no=" + channelNumber);
        } else {
            StepDaddyLog.w("No MainActivity for tune channel=" + channelNumber);
        }
        boolean opened = openStreamUrl(context, buildStreamUrl(context, channelNumber));
        if (opened) {
            onTuneSuccess(context, channelId > 0L ? channelId : -1L, channelNumber);
        }
        return opened;
    }

    public static boolean tuneChannelId(Context context, long channelId) {
        if (channelId <= 0L) {
            return false;
        }
        StepDaddyDb.ChannelInfo info = StepDaddyDb.lookupChannelInfo(context, channelId);
        int channelNo = info != null && info.tvgChNo > 0 ? info.tvgChNo : -1;
        recordChannel(context, channelId, channelNo, info != null ? info.name : null);
        Activity activity = asActivity(context);
        if (activity != null && openFromTvGuide(activity, channelId, true)) {
            scheduleFullscreen(activity);
            onTuneSuccess(context, channelId, channelNo);
            StepDaddyLog.i("Tuned channel id=" + channelId);
            return true;
        }
        boolean opened = openStreamUrl(context, buildStreamUrl(context, channelId));
        if (opened) {
            onTuneSuccess(context, channelId, channelNo);
        }
        return opened;
    }

    public static boolean searchChannel(Context context, String query) {
        if (query == null || query.trim().isEmpty()) {
            return false;
        }
        long channelId = StepDaddyDb.lookupChannelIdByName(context, query.trim());
        if (channelId <= 0L) {
            StepDaddyLog.w("No channel match for search: " + query);
            return false;
        }
        Activity activity = asActivity(context);
        if (activity == null) {
            return tuneChannelId(context, channelId);
        }
        recordChannel(context, channelId, -1);
        if (openFromSearch(activity, channelId)) {
            scheduleFullscreen(activity);
            StepDaddyDb.ChannelInfo info = StepDaddyDb.lookupChannelInfo(context, channelId);
            int chNo = info != null ? info.tvgChNo : -1;
            onTuneSuccess(context, channelId, chNo);
            return true;
        }
        return tuneChannelId(context, channelId);
    }

    public static boolean channelUp(Context context) {
        return dispatchMediaKey(context, KeyEvent.KEYCODE_CHANNEL_UP);
    }

    public static boolean channelDown(Context context) {
        return dispatchMediaKey(context, KeyEvent.KEYCODE_CHANNEL_DOWN);
    }

    public static boolean pause(Context context) {
        playingTracked = false;
        return dispatchMediaKey(context, KeyEvent.KEYCODE_MEDIA_PAUSE);
    }

    public static boolean play(Context context) {
        boolean ok = dispatchMediaKey(context, KeyEvent.KEYCODE_MEDIA_PLAY);
        if (ok) {
            playingTracked = true;
            StepDaddyEvents.emit(context, StepDaddyEvents.PLAYBACK_STARTED, "media_play");
        }
        return ok;
    }

    public static void onPlaybackError(Object playerFragment, Object exception) {
        playingTracked = false;
        Context context = null;
        if (playerFragment != null) {
            try {
                context = (Context) playerFragment.getClass().getMethod("getContext").invoke(playerFragment);
            } catch (Exception ignored) {
            }
        }
        if (context == null) {
            Activity activity = StepDaddyActivityHolder.getMainActivity();
            if (activity != null) {
                context = activity.getApplicationContext();
            }
        }
        if (context == null) {
            return;
        }
        String detail = exception == null ? "unknown" : exception.toString();
        StepDaddyEvents.emit(context, StepDaddyEvents.PLAYBACK_ERROR, detail);
    }

    public static boolean openStream(Context context, String streamUrl) {
        if (streamUrl == null || streamUrl.trim().isEmpty()) {
            return false;
        }
        String trimmed = streamUrl.trim();
        int channel = parseTivimateStreamChannel(trimmed);
        if (channel > 0) {
            return tuneChannel(context, channel);
        }
        return openStreamUrl(context, trimmed);
    }

    private static boolean openStreamUrl(Context context, String streamUrl) {
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(streamUrl));
            intent.setClassName(context.getPackageName(), "ar.tvplayer.tv.ui.MainActivity");
            intent.putExtra(StepDaddyConstants.EXTRA_INTERNAL, true);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            context.startActivity(intent);
            StepDaddyLog.i("Opened stream " + streamUrl);
            return true;
        } catch (Exception error) {
            StepDaddyLog.w("Stream open failed", error);
            return false;
        }
    }

    public static boolean openEpgOverlay(Context context) {
        if (!(context instanceof Activity)) {
            return false;
        }
        try {
            Activity activity = (Activity) context;
            activity.dispatchKeyEvent(
                new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MENU)
            );
            activity.dispatchKeyEvent(
                new KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MENU)
            );
            return true;
        } catch (Exception error) {
            StepDaddyLog.w("EPG overlay failed", error);
            return false;
        }
    }

    static void applyBootTune(Activity activity) {
        if (bootTuneScheduled || activity == null) {
            return;
        }
        int channel = StepDaddyPrefs.bootTuneChannel(activity);
        if (channel <= 0 && StepDaddyPrefs.isSetupDone(activity)) {
            channel = StepDaddyConstants.DEFAULT_BOOT_TUNE_CHANNEL;
        }
        if (channel <= 0) {
            return;
        }
        bootTuneScheduled = true;
        final int tuneChannel = channel;
        StepDaddyLog.i("Scheduling boot-tune channel " + tuneChannel
            + " in " + BOOT_TUNE_DELAY_MS + "ms");
        new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
            @Override
            public void run() {
                if (activity.isFinishing()) {
                    return;
                }
                try {
                    if (activity.isDestroyed()) {
                        return;
                    }
                } catch (NoSuchMethodError ignored) {
                    // API < 17
                }
                StepDaddyLog.i("Boot-tune channel " + tuneChannel);
                tuneChannel(activity, tuneChannel);
                StepDaddyPrefs.clearBootTuneChannel(activity);
            }
        }, BOOT_TUNE_DELAY_MS);
    }

    private static void onTuneSuccess(Context context, long channelId, int channelNo) {
        if (channelId > 0L) {
            StepDaddyDb.ChannelInfo info = StepDaddyDb.lookupChannelInfo(context, channelId);
            if (info != null) {
                recordChannel(context, info.id, info.tvgChNo > 0 ? info.tvgChNo : channelNo, info.name);
            } else {
                recordChannel(context, channelId, channelNo, null);
            }
        } else if (channelNo > 0) {
            recordChannel(context, -1L, channelNo, null);
        }
        playingTracked = true;
        StepDaddyEvents.emit(context, StepDaddyEvents.CHANNEL_CHANGED);
        StepDaddyEvents.emit(context, StepDaddyEvents.PLAYBACK_STARTED, "tune");
    }

    private static void recordChannel(Context context, long channelId, int channelNo) {
        recordChannel(context, channelId, channelNo, null);
    }

    private static void recordChannel(
        Context context,
        long channelId,
        int channelNo,
        String name
    ) {
        if (channelId > 0L) {
            lastChannelId = channelId;
        }
        if (channelNo > 0) {
            lastChannelNo = channelNo;
            lastTuneChannel = channelNo;
        }
        if (name != null && !name.isEmpty()) {
            lastChannelName = name;
        } else if (channelId > 0L) {
            StepDaddyDb.ChannelInfo info = StepDaddyDb.lookupChannelInfo(context, channelId);
            if (info != null && info.name != null) {
                lastChannelName = info.name;
                if (info.tvgChNo > 0) {
                    lastChannelNo = info.tvgChNo;
                }
            }
        } else if (channelNo > 0) {
            StepDaddyDb.ChannelInfo info = StepDaddyDb.lookupChannelInfoByNumber(context, channelNo);
            if (info != null) {
                lastChannelId = info.id;
                if (info.name != null) {
                    lastChannelName = info.name;
                }
            }
        }
    }

    private static String buildStreamUrl(Context context, long channel) {
        String base = StepDaddyPrefs.gatewayBase(context).replaceAll("/$", "");
        return base + "/tivimate-stream/" + channel + ".m3u8";
    }

    private static Activity asActivity(Context context) {
        if (context instanceof Activity) {
            return (Activity) context;
        }
        return StepDaddyActivityHolder.getMainActivity();
    }

    private static boolean dispatchMediaKey(Context context, int keyCode) {
        Activity activity = asActivity(context);
        if (activity == null) {
            return false;
        }
        try {
            activity.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, keyCode));
            activity.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, keyCode));
            return true;
        } catch (Exception error) {
            StepDaddyLog.w("Key dispatch failed code=" + keyCode, error);
            return false;
        }
    }

    private static boolean openFromTvGuide(Activity activity, long channelId, boolean autoPlay) {
        try {
            Method[] methods = activity.getClass().getDeclaredMethods();
            for (Method method : methods) {
                Class<?>[] params = method.getParameterTypes();
                if (params.length != 4) {
                    continue;
                }
                if (params[0] != long.class || params[3] != boolean.class) {
                    continue;
                }
                method.setAccessible(true);
                method.invoke(activity, channelId, null, null, autoPlay);
                return true;
            }
        } catch (Exception error) {
            StepDaddyLog.w("openFromTvGuide reflection failed", error);
        }
        return false;
    }

    /** MainActivity openPlayerFromSearch — (channelId, group, catchupInfo). */
    private static boolean openFromSearch(Activity activity, long channelId) {
        try {
            Method[] methods = activity.getClass().getDeclaredMethods();
            for (Method method : methods) {
                Class<?>[] params = method.getParameterTypes();
                if (params.length != 3) {
                    continue;
                }
                if (params[0] != long.class) {
                    continue;
                }
                method.setAccessible(true);
                method.invoke(activity, channelId, null, null);
                StepDaddyLog.i("openPlayerFromSearch channelId=" + channelId);
                return true;
            }
        } catch (Exception error) {
            StepDaddyLog.w("openFromSearch reflection failed", error);
        }
        return false;
    }

    private static void scheduleFullscreen(Activity activity) {
        if (activity == null) {
            return;
        }
        new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
            @Override
            public void run() {
                expandToFullscreen(activity);
            }
        }, FULLSCREEN_DELAY_MS);
    }

    private static void expandToFullscreen(Activity activity) {
        try {
            Method method = activity.getClass().getDeclaredMethod(MAIN_EXPAND_PLAYER, boolean.class);
            method.setAccessible(true);
            method.invoke(activity, true);
            StepDaddyLog.i("Expanded player to fullscreen");
            return;
        } catch (Exception error) {
            StepDaddyLog.w("Fullscreen reflection failed, trying OK key", error);
        }
        try {
            activity.dispatchKeyEvent(
                new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_CENTER)
            );
            activity.dispatchKeyEvent(
                new KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DPAD_CENTER)
            );
            StepDaddyLog.i("Sent OK to expand player");
        } catch (Exception error) {
            StepDaddyLog.w("Fullscreen key dispatch failed", error);
        }
    }
}
