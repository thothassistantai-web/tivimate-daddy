package ar.tvplayer.tv.stepdaddy;

import android.content.Context;

final class StepDaddyHttpServerHolder {
    private static volatile StepDaddyHttpServer server;

    private StepDaddyHttpServerHolder() {
    }

    static void ensureStarted(Context context) {
        if (server != null) {
            return;
        }
        synchronized (StepDaddyHttpServerHolder.class) {
            if (server != null) {
                return;
            }
            try {
                server = new StepDaddyHttpServer(context.getApplicationContext());
                server.start();
            } catch (Exception error) {
                StepDaddyLog.w("HTTP server start failed", error);
            }
        }
    }

    static void stop(Context context) {
        synchronized (StepDaddyHttpServerHolder.class) {
            if (server != null) {
                server.stopServer();
                server = null;
            }
        }
    }
}
