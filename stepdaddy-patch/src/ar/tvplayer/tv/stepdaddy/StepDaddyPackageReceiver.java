package ar.tvplayer.tv.stepdaddy;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Marks post-upgrade session before MainActivity starts (MY_PACKAGE_REPLACED). */
public final class StepDaddyPackageReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null) {
            return;
        }
        if (!Intent.ACTION_MY_PACKAGE_REPLACED.equals(intent.getAction())) {
            return;
        }
        Context app = context.getApplicationContext();
        StepDaddyPrefs.setUpgradeJustCompleted(app, true);
        StepDaddySetup.detectUpgrade(app);
        StepDaddyLog.i("Package replaced; suppressing auto-setup this session");
    }
}
