package ar.tvplayer.tv.stepdaddy;

import org.json.JSONObject;

final class StepDaddyJson {
    private StepDaddyJson() {
    }

    static SetupPayload parseSetup(String body) {
        if (body == null || body.trim().isEmpty()) {
            return null;
        }
        try {
            JSONObject json = new JSONObject(body);
            String playlist = json.optString("playlist", "");
            String epg = json.optString("epg", "");
            if (playlist.isEmpty()) {
                return null;
            }
            return new SetupPayload(playlist, epg);
        } catch (Exception error) {
            StepDaddyLog.w("Failed to parse setup JSON", error);
            return null;
        }
    }

    static final class SetupPayload {
        final String playlistUrl;
        final String epgUrls;

        SetupPayload(String playlistUrl, String epgUrls) {
            this.playlistUrl = playlistUrl;
            this.epgUrls = epgUrls == null ? "" : epgUrls;
        }
    }
}
