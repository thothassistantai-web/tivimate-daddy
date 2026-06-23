package ar.tvplayer.tv.stepdaddy;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;

public final class StepDaddyBridgeActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Intent intent = getIntent();
        if (intent != null) {
            Uri data = intent.getData();
            if (data != null) {
                StepDaddyHooks.handleUri(this, data);
            } else {
                StepDaddyHooks.handleUri(this, buildUriFromExtras(intent));
            }
        }
        finish();
    }

    private static Uri buildUriFromExtras(Intent intent) {
        String action = intent.getAction();
        if (StepDaddyConstants.ACTION_SETUP.equals(action)) {
            String base = intent.getStringExtra(StepDaddyConstants.EXTRA_GATEWAY_BASE);
            if (base == null) {
                return new Uri.Builder()
                    .scheme(StepDaddyConstants.SCHEME)
                    .authority(StepDaddyConstants.HOST_SETUP)
                    .build();
            }
            return new Uri.Builder()
                .scheme(StepDaddyConstants.SCHEME)
                .authority(StepDaddyConstants.HOST_SETUP)
                .appendQueryParameter("base", base)
                .build();
        }
        if (StepDaddyConstants.ACTION_TUNE.equals(action)) {
            int channel = intent.getIntExtra(StepDaddyConstants.EXTRA_CHANNEL, -1);
            if (channel > 0) {
                return new Uri.Builder()
                    .scheme(StepDaddyConstants.SCHEME)
                    .authority(StepDaddyConstants.HOST_CHANNEL)
                    .appendPath(String.valueOf(channel))
                    .build();
            }
        }
        if (StepDaddyConstants.ACTION_STREAM.equals(action)) {
            String streamUrl = intent.getStringExtra(StepDaddyConstants.EXTRA_STREAM_URL);
            if (streamUrl != null) {
                return new Uri.Builder()
                    .scheme(StepDaddyConstants.SCHEME)
                    .authority(StepDaddyConstants.HOST_STREAM)
                    .appendQueryParameter("url", streamUrl)
                    .build();
            }
        }
        return null;
    }
}
