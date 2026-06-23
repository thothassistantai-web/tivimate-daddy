package ar.tvplayer.tv.stepdaddy;

import android.content.Context;

import org.json.JSONObject;

/** Fire-and-forget POST of lifecycle events to StepDaddy Gateway. */
final class StepDaddyEvents {
    static final String PLAYBACK_STARTED = "PLAYBACK_STARTED";
    static final String PLAYBACK_ERROR = "PLAYBACK_ERROR";
    static final String CHANNEL_CHANGED = "CHANNEL_CHANGED";
    static final String WIZARD_STEP = "WIZARD_STEP";
    static final String SETUP_COMPLETE = "SETUP_COMPLETE";

    private StepDaddyEvents() {
    }

    static void emit(Context context, String type) {
        emit(context, type, null, null);
    }

    static void emit(Context context, String type, String detail) {
        emit(context, type, detail, null);
    }

    static void emit(Context context, String type, String detail, JSONObject extra) {
        if (context == null || type == null || type.isEmpty()) {
            return;
        }
        final Context app = context.getApplicationContext();
        final JSONObject payload = buildPayload(app, type, detail, extra);
        new Thread(new Runnable() {
            @Override
            public void run() {
                postEvent(app, payload);
            }
        }, "stepdaddy-event-" + type).start();
    }

    private static JSONObject buildPayload(
        Context context,
        String type,
        String detail,
        JSONObject extra
    ) {
        JSONObject json = new JSONObject();
        try {
            json.put("type", type);
            json.put("timestamp", System.currentTimeMillis());
            json.put("patchVersion", StepDaddyConstants.PATCH_VERSION);
            long channelId = StepDaddyPlayer.lastChannelId();
            int channelNo = StepDaddyPlayer.lastChannelNo();
            String channelName = StepDaddyPlayer.lastChannelName();
            if (channelId > 0L) {
                json.put("channelId", channelId);
            }
            if (channelNo > 0) {
                json.put("channelNo", channelNo);
            }
            if (channelName != null && !channelName.isEmpty()) {
                json.put("channelName", channelName);
            }
            json.put("wizardPhase", StepDaddyState.wizardPhase());
            json.put("setupDone", StepDaddyPrefs.isSetupDone(context));
            if (detail != null && !detail.isEmpty()) {
                json.put("detail", detail);
            }
            if (extra != null) {
                java.util.Iterator<String> keys = extra.keys();
                while (keys.hasNext()) {
                    String key = keys.next();
                    json.put(key, extra.get(key));
                }
            }
        } catch (Exception error) {
            StepDaddyLog.w("buildPayload failed", error);
        }
        return json;
    }

    private static void postEvent(Context context, JSONObject payload) {
        String base = StepDaddyPrefs.gatewayBase(context).replaceAll("/$", "");
        String url = base + StepDaddyConstants.EVENTS_PATH;
        try {
            int code = StepDaddyHttp.postJson(url, payload.toString());
            if (code < 200 || code >= 300) {
                StepDaddyLog.w("Event POST " + code + " for " + payload.optString("type"));
            } else {
                StepDaddyLog.i("Event posted: " + payload.optString("type"));
            }
        } catch (Exception error) {
            StepDaddyLog.w("Event POST failed", error);
        }
    }
}
