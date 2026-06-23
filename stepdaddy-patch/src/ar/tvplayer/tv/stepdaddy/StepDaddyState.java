package ar.tvplayer.tv.stepdaddy;

import android.app.Activity;
import android.content.Context;

import org.json.JSONObject;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/** Runtime state for GET /state and event payloads. */
final class StepDaddyState {
    static final String PHASE_IDLE = "idle";
    static final String PHASE_URL = "url";
    static final String PHASE_STATUS = "status";
    static final String PHASE_EPG = "epg";
    static final String PHASE_IMPORTING = "importing";
    static final String PHASE_DONE = "done";

    static final String MODE_FULLSCREEN = "fullscreen";
    static final String MODE_GUIDE = "guide";
    static final String MODE_PIP = "pip";
    static final String MODE_UNKNOWN = "unknown";

    /** MainActivity.ނ() — PlayerHostFragment accessor. */
    private static final String MAIN_GET_PLAYER_HOST = "\u0782";
    /** PlayerHostFragment.ޑ() — active PlayerFragment. */
    private static final String HOST_GET_PLAYER = "\u0791";
    /** PlayerHostFragment.ތ() — TV guide / channel list visible. */
    private static final String HOST_IS_GUIDE = "\u078c";
    /** PlayerHostFragment.ޒ() — fullscreen player surface visible. */
    private static final String HOST_IS_FULLSCREEN = "\u0792";
    /** PlayerFragment.ޗ() — player view-model. */
    private static final String FRAGMENT_GET_MODEL = "\u0797";

    private static volatile String wizardPhase = PHASE_IDLE;

    private StepDaddyState() {
    }

    static void setWizardPhase(String phase) {
        wizardPhase = (phase == null || phase.isEmpty()) ? PHASE_IDLE : phase;
    }

    static String wizardPhase() {
        return wizardPhase;
    }

    static JSONObject buildStateJson(Context context) {
        Context app = context.getApplicationContext();
        StepDaddySetup.refreshSetupState(app);
        JSONObject json = new JSONObject();
        try {
            json.put("setupDone", StepDaddyPrefs.isSetupDone(app));
            json.put("wizardPending", StepDaddyPrefs.isWizardPending(app));
            json.put("wizardPhase", wizardPhase());
            json.put("currentChannelId", StepDaddyPlayer.lastChannelId());
            json.put("currentChannelNo", StepDaddyPlayer.lastChannelNo());
            String name = StepDaddyPlayer.lastChannelName();
            if (name != null && !name.isEmpty()) {
                json.put("currentChannelName", name);
            }
            json.put("isPlaying", detectIsPlaying(app));
            json.put("playerMode", detectPlayerMode(app));
            json.put("playlistCount", StepDaddyDb.playlistCount(app));
            json.put("channelCount", StepDaddyDb.channelCount(app));
            json.put("gatewayBase", StepDaddyPrefs.gatewayBase(app));
            json.put("patchVersion", StepDaddyConstants.PATCH_VERSION);
        } catch (Exception error) {
            StepDaddyLog.w("buildStateJson failed", error);
        }
        return json;
    }

    private static boolean detectIsPlaying(Context context) {
        if (StepDaddyPlayer.isPlayingTracked()) {
            return true;
        }
        Activity activity = StepDaddyActivityHolder.getMainActivity();
        if (activity == null) {
            return false;
        }
        try {
            Object host = callNoArg(activity, MAIN_GET_PLAYER_HOST);
            if (host == null) {
                return false;
            }
            Object playerFragment = callNoArg(host, HOST_GET_PLAYER);
            if (playerFragment == null) {
                return false;
            }
            Object model = callNoArg(playerFragment, FRAGMENT_GET_MODEL);
            if (model == null) {
                return false;
            }
            for (Field field : model.getClass().getDeclaredFields()) {
                String typeName = field.getType().getName();
                if (!typeName.contains("exoplayer2") && !typeName.contains(".Player")) {
                    continue;
                }
                field.setAccessible(true);
                Object player = field.get(model);
                if (player == null) {
                    continue;
                }
                try {
                    Method isPlaying = player.getClass().getMethod("isPlaying");
                    if (Boolean.TRUE.equals(isPlaying.invoke(player))) {
                        return true;
                    }
                } catch (NoSuchMethodException ignored) {
                }
            }
        } catch (Exception ignored) {
        }
        return false;
    }

    private static String detectPlayerMode(Context context) {
        Activity activity = StepDaddyActivityHolder.getMainActivity();
        if (activity == null) {
            return MODE_UNKNOWN;
        }
        try {
            Object host = callNoArg(activity, MAIN_GET_PLAYER_HOST);
            if (host == null) {
                return MODE_UNKNOWN;
            }
            for (Field field : host.getClass().getDeclaredFields()) {
                if (field.getType() == int.class && "\u0789".equals(field.getName())) {
                    field.setAccessible(true);
                    if (field.getInt(host) != 0) {
                        return MODE_PIP;
                    }
                }
            }
            if (Boolean.TRUE.equals(callNoArgBoolean(host, HOST_IS_GUIDE))) {
                return MODE_GUIDE;
            }
            if (Boolean.TRUE.equals(callNoArgBoolean(host, HOST_IS_FULLSCREEN))) {
                return MODE_FULLSCREEN;
            }
        } catch (Exception error) {
            StepDaddyLog.w("detectPlayerMode failed", error);
        }
        return MODE_UNKNOWN;
    }

    private static Object callNoArg(Object target, String name) throws Exception {
        Method method = target.getClass().getDeclaredMethod(name);
        method.setAccessible(true);
        return method.invoke(target);
    }

    private static Boolean callNoArgBoolean(Object target, String name) {
        try {
            Method method = target.getClass().getDeclaredMethod(name);
            method.setAccessible(true);
            Object result = method.invoke(target);
            return result instanceof Boolean ? (Boolean) result : null;
        } catch (Exception ignored) {
            return null;
        }
    }
}
