package ar.tvplayer.tv.stepdaddy;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

public final class StepDaddyUpdateDownloader {
    public interface ProgressListener {
        void onProgress(int percent, long downloaded, long total);
    }

    private StepDaddyUpdateDownloader() {
    }

    public static File download(
        Context context,
        String url,
        String fileName,
        ProgressListener listener
    ) throws Exception {
        if (url == null || url.isEmpty()) {
            throw new IllegalArgumentException("Missing download URL");
        }
        File dir = new File(context.getCacheDir(), "apk");
        if (!dir.exists() && !dir.mkdirs()) {
            throw new IllegalStateException("Cannot create APK cache dir");
        }
        File target = new File(dir, sanitize(fileName));
        HttpURLConnection connection = null;
        InputStream input = null;
        FileOutputStream output = null;
        try {
            connection = openConnection(url);
            int code = connection.getResponseCode();
            if (code < 200 || code >= 300) {
                throw new IllegalStateException("Download HTTP " + code);
            }
            long total = connection.getContentLengthLong();
            if (total <= 0L) {
                total = connection.getContentLength();
            }
            input = new BufferedInputStream(connection.getInputStream());
            output = new FileOutputStream(target);
            byte[] buffer = new byte[16 * 1024];
            long downloaded = 0L;
            int read;
            while ((read = input.read(buffer)) != -1) {
                output.write(buffer, 0, read);
                downloaded += read;
                if (listener != null) {
                    int percent = total > 0L
                        ? (int) Math.min(100L, (downloaded * 100L) / total)
                        : -1;
                    listener.onProgress(percent, downloaded, total);
                }
            }
            output.flush();
            StepDaddyLog.i("Downloaded update APK to " + target.getAbsolutePath());
            return target;
        } finally {
            if (output != null) {
                try {
                    output.close();
                } catch (Exception ignored) {
                }
            }
            if (input != null) {
                try {
                    input.close();
                } catch (Exception ignored) {
                }
            }
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    public static boolean canInstallPackages(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return true;
        }
        return context.getPackageManager().canRequestPackageInstalls();
    }

    public static Intent buildInstallUnknownAppsSettingsIntent(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return null;
        }
        Intent intent = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES);
        intent.setData(Uri.parse("package:" + context.getPackageName()));
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        return intent;
    }

    public static void installApk(Context context, File apkFile) {
        if (apkFile == null || !apkFile.exists()) {
            throw new IllegalStateException("APK file missing");
        }
        try {
            Class<?> provider = Class.forName("androidx.core.content.FileProvider");
            Method getUri = provider.getMethod(
                "getUriForFile",
                Context.class,
                String.class,
                File.class
            );
            Uri uri = (Uri) getUri.invoke(
                null,
                context,
                "ar.tvplayer.tv.fileprovider",
                apkFile
            );
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(uri, "application/vnd.android.package-archive");
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
        } catch (Exception error) {
            throw new IllegalStateException("Install intent failed", error);
        }
    }

    private static HttpURLConnection openConnection(String urlString) throws Exception {
        URL url = new URL(urlString);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setConnectTimeout(10_000);
        connection.setReadTimeout(120_000);
        connection.setInstanceFollowRedirects(true);
        connection.setRequestMethod("GET");
        Map<String, String> headers = new HashMap<>();
        headers.put("User-Agent", StepDaddyConstants.GITHUB_USER_AGENT);
        headers.put("Accept", "application/octet-stream");
        for (Map.Entry<String, String> entry : headers.entrySet()) {
            connection.setRequestProperty(entry.getKey(), entry.getValue());
        }
        connection.connect();
        return connection;
    }

    private static String sanitize(String fileName) {
        if (fileName == null || fileName.trim().isEmpty()) {
            return "stepdaddy-update.apk";
        }
        return fileName.replaceAll("[^a-zA-Z0-9._-]", "_");
    }
}
