package ar.tvplayer.tv.stepdaddy;

import android.app.Activity;
import android.os.Handler;
import android.os.Looper;
import android.view.KeyEvent;

final class StepDaddyUi {
    private static final int DISMISS_ATTEMPTS = 4;
    private static final long DISMISS_INTERVAL_MS = 400L;

    private StepDaddyUi() {
    }

    static void scheduleRemoteHintDismiss(Activity activity) {
        if (activity == null || !StepDaddyPrefs.isWizardPending(activity)) {
            return;
        }
        if (!StepDaddySetup.hasGatewayPlaylist(activity)) {
            StepDaddyLog.i("Deferring remote intro dismiss until playlist is saved");
            return;
        }
        dismissRemoteHint(activity, 0);
    }

    private static void dismissRemoteHint(Activity activity, int attempt) {
        if (!StepDaddyPrefs.isWizardPending(activity)) {
            return;
        }
        if (!StepDaddySetup.hasGatewayPlaylist(activity)) {
            StepDaddyPrefs.setWizardPending(activity, false);
            StepDaddyPrefs.setSetupDone(activity, false);
            StepDaddyLog.w("Remote intro dismiss aborted; gateway playlist not in DB");
            return;
        }
        if (attempt >= DISMISS_ATTEMPTS) {
            if (StepDaddySetup.hasGatewayPlaylist(activity)) {
                StepDaddySetup.confirmSetupComplete(activity);
                StepDaddyLog.i("Remote control intro dismiss attempts finished");
            } else {
                StepDaddyPrefs.setWizardPending(activity, false);
                StepDaddyPrefs.setSetupDone(activity, false);
                StepDaddyLog.w("Remote intro dismiss finished without playlist in DB");
            }
            return;
        }
        long delay = attempt == 0 ? 250L : DISMISS_INTERVAL_MS;
        new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
            @Override
            public void run() {
                if (!StepDaddyPrefs.isWizardPending(activity)) {
                    return;
                }
                if (!StepDaddySetup.hasGatewayPlaylist(activity)) {
                    StepDaddyPrefs.setWizardPending(activity, false);
                    StepDaddyPrefs.setSetupDone(activity, false);
                    StepDaddyLog.w("Remote intro dismiss stopped; playlist missing from DB");
                    return;
                }
                try {
                    activity.dispatchKeyEvent(
                        new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_CENTER)
                    );
                    activity.dispatchKeyEvent(
                        new KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DPAD_CENTER)
                    );
                    StepDaddyLog.i("Sent OK to dismiss remote control intro (attempt "
                        + (attempt + 1) + ")");
                } catch (Exception error) {
                    StepDaddyLog.w("Remote hint key dispatch failed", error);
                }
                if (StepDaddySetup.hasGatewayPlaylist(activity)) {
                    StepDaddySetup.confirmSetupComplete(activity);
                    return;
                }
                dismissRemoteHint(activity, attempt + 1);
            }
        }, delay);
    }
}
