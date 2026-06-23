package ar.tvplayer.tv.stepdaddy;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public final class StepDaddyCommandReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null) {
            return;
        }
        String action = intent.getAction();
        if (action == null) {
            return;
        }
        Context app = context.getApplicationContext();
        if (StepDaddyConstants.ACTION_START_HTTP.equals(action)) {
            StepDaddyHttpService.ensureStarted(app);
            return;
        }
        if (StepDaddyConstants.ACTION_STOP_HTTP.equals(action)) {
            StepDaddyHttpService.stop(app);
            return;
        }
        StepDaddyHooks.handleIntent(app, intent);
    }
}
