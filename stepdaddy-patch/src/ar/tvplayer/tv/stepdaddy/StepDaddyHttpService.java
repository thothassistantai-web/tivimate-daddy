package ar.tvplayer.tv.stepdaddy;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.IBinder;

public final class StepDaddyHttpService extends Service {
    public static void ensureStarted(Context context) {
        StepDaddyHttpServerHolder.ensureStarted(context.getApplicationContext());
    }

    public static void stop(Context context) {
        StepDaddyHttpServerHolder.stop(context.getApplicationContext());
        context.getApplicationContext().stopService(new Intent(context, StepDaddyHttpService.class));
    }

    @Override
    public void onCreate() {
        super.onCreate();
        StepDaddyHttpServerHolder.ensureStarted(this);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        StepDaddyHttpServerHolder.ensureStarted(this);
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        StepDaddyHttpServerHolder.stop(this);
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
