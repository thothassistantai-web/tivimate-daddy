package ar.tvplayer.tv.stepdaddy;

import android.app.Activity;

import java.lang.ref.WeakReference;

final class StepDaddyActivityHolder {
    private static WeakReference<Activity> mainActivity;

    private StepDaddyActivityHolder() {
    }

    static void setMainActivity(Activity activity) {
        if (activity != null) {
            mainActivity = new WeakReference<>(activity);
        }
    }

    static void clearMainActivity(Activity activity) {
        if (mainActivity != null && mainActivity.get() == activity) {
            mainActivity = null;
        }
    }

    static Activity getMainActivity() {
        return mainActivity != null ? mainActivity.get() : null;
    }
}
