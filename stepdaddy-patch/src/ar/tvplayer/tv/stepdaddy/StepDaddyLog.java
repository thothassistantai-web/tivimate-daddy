package ar.tvplayer.tv.stepdaddy;

import android.util.Log;

final class StepDaddyLog {
    private static final String TAG = "StepDaddyBridge";

    private StepDaddyLog() {
    }

    static void i(String message) {
        Log.i(TAG, message);
    }

    static void w(String message) {
        Log.w(TAG, message);
    }

    static void w(String message, Throwable error) {
        Log.w(TAG, message, error);
    }
}
