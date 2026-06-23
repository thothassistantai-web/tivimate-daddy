package ar.tvplayer.tv.stepdaddy;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONObject;

import java.net.ServerSocket;
import java.net.Socket;

final class StepDaddyHttpServer implements Runnable {
    private final Context context;
    private final Object lock = new Object();
    private volatile boolean running;
    private Thread thread;
    private ServerSocket serverSocket;

    StepDaddyHttpServer(Context context) {
        this.context = context.getApplicationContext();
    }

    void start() {
        synchronized (lock) {
            if (running) {
                return;
            }
            running = true;
            thread = new Thread(this, "stepdaddy-http");
            thread.setDaemon(true);
            thread.start();
        }
    }

    void stopServer() {
        synchronized (lock) {
            running = false;
            if (serverSocket != null) {
                try {
                    serverSocket.close();
                } catch (Exception ignored) {
                }
                serverSocket = null;
            }
        }
    }

    @Override
    public void run() {
        try {
            serverSocket = new ServerSocket(StepDaddyConstants.HTTP_PORT);
            StepDaddyLog.i("HTTP control listening on :" + StepDaddyConstants.HTTP_PORT);
            while (running) {
                final Socket client = serverSocket.accept();
                Thread worker = new Thread(new Runnable() {
                    @Override
                    public void run() {
                        handleClient(client);
                    }
                }, "stepdaddy-http-client");
                worker.setDaemon(true);
                worker.start();
            }
        } catch (Exception error) {
            if (running) {
                StepDaddyLog.w("HTTP server stopped", error);
            }
        } finally {
            running = false;
        }
    }

    private void handleClient(Socket client) {
        try {
            StepDaddyHttp.HttpRequest request = StepDaddyHttp.readRequest(client.getInputStream());
            byte[] response = route(request.path, request.query);
            client.getOutputStream().write(response);
            client.getOutputStream().flush();
        } catch (Exception error) {
            StepDaddyLog.w("HTTP client error", error);
        } finally {
            try {
                client.close();
            } catch (Exception ignored) {
            }
        }
    }

    private byte[] route(String path, String query) {
        if (path == null) {
            path = "/";
        }
        try {
            if ("/state".equals(path)) {
                JSONObject json = StepDaddyState.buildStateJson(context);
                json.put("ok", true);
                return jsonResponse(json);
            }
            if ("/status".equals(path) || "/".equals(path)) {
                StepDaddySetup.refreshSetupState(context);
                JSONObject json = StepDaddyState.buildStateJson(context);
                json.put("ok", true);
                json.put("package", context.getPackageName());
                json.put("gateway", StepDaddyPrefs.gatewayBase(context));
                json.put("port", StepDaddyConstants.HTTP_PORT);
                return jsonResponse(json);
            }
            if ("/setup".equals(path)) {
                StepDaddySetup.runSetup(context, null);
                return okJson("setup_started");
            }
            if ("/channel/up".equals(path)) {
                return dispatchKeyResult(StepDaddyPlayer.channelUp(context));
            }
            if ("/channel/down".equals(path)) {
                return dispatchKeyResult(StepDaddyPlayer.channelDown(context));
            }
            if ("/pause".equals(path)) {
                return dispatchKeyResult(StepDaddyPlayer.pause(context));
            }
            if ("/play".equals(path)) {
                return dispatchKeyResult(StepDaddyPlayer.play(context));
            }
            if ("/search".equals(path)) {
                String q = StepDaddyHttp.queryParam(query, "q");
                boolean ok = StepDaddyPlayer.searchChannel(context, q);
                return okJson(ok ? "search_opened" : "search_failed");
            }
            if ("/channels".equals(path)) {
                int limit = 50;
                String limitParam = StepDaddyHttp.queryParam(query, "limit");
                if (limitParam != null) {
                    try {
                        limit = Integer.parseInt(limitParam);
                    } catch (NumberFormatException ignored) {
                    }
                }
                JSONObject json = new JSONObject();
                json.put("ok", true);
                json.put("channels", StepDaddyDb.listChannels(context, limit));
                return jsonResponse(json);
            }
            if (path.startsWith("/boot-tune/")) {
                String tail = path.substring("/boot-tune/".length());
                int channel = Integer.parseInt(tail.replaceAll("[^0-9].*$", ""));
                StepDaddyPrefs.setBootTuneChannel(context, channel);
                JSONObject json = new JSONObject();
                json.put("ok", true);
                json.put("status", "boot_tune_saved");
                json.put("channel", channel);
                return jsonResponse(json);
            }
            if (path.startsWith("/tune/")) {
                String tail = path.substring("/tune/".length());
                final int channel = Integer.parseInt(tail.replaceAll("[^0-9].*$", ""));
                launchMain(context);
                new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        Activity activity = StepDaddyActivityHolder.getMainActivity();
                        if (activity != null) {
                            StepDaddyPlayer.tuneChannel(activity, channel);
                        } else {
                            StepDaddyPlayer.tuneChannel(context, channel);
                        }
                    }
                }, 450L);
                return okJson("tuned");
            }
            if (path.startsWith("/stream/")) {
                String tail = path.substring("/stream/".length());
                int channel = Integer.parseInt(tail.replaceAll("[^0-9].*$", ""));
                String url = StepDaddyPrefs.gatewayBase(context).replaceAll("/$", "")
                    + "/tivimate-stream/" + channel + ".m3u8";
                boolean opened = StepDaddyPlayer.openStream(launchMain(context), url);
                return okJson(opened ? "stream_opened" : "stream_failed");
            }
            if ("/epg".equals(path)) {
                launchMain(context);
                final Activity[] holder = new Activity[1];
                holder[0] = StepDaddyActivityHolder.getMainActivity();
                final boolean[] opened = new boolean[1];
                if (holder[0] != null) {
                    new Handler(Looper.getMainLooper()).post(new Runnable() {
                        @Override
                        public void run() {
                            opened[0] = StepDaddyPlayer.openEpgOverlay(holder[0]);
                        }
                    });
                    try {
                        Thread.sleep(300L);
                    } catch (InterruptedException ignored) {
                    }
                }
                return okJson(opened[0] ? "epg_opened" : "epg_failed");
            }
            if ("/launch".equals(path)) {
                launchMain(context);
                return okJson("launched");
            }
            return StepDaddyHttp.responseBytes(
                "HTTP/1.1 404 Not Found",
                "application/json; charset=utf-8",
                "{\"error\":\"not_found\"}"
            );
        } catch (Exception error) {
            StepDaddyLog.w("HTTP route failed for " + path, error);
            return StepDaddyHttp.responseBytes(
                "HTTP/1.1 500 Internal Server Error",
                "application/json; charset=utf-8",
                "{\"error\":\"server_error\"}"
            );
        }
    }

    private byte[] dispatchKeyResult(boolean ok) throws Exception {
        return okJson(ok ? "key_sent" : "key_failed");
    }

    private byte[] okJson(String status) throws Exception {
        JSONObject json = new JSONObject();
        json.put("ok", true);
        json.put("status", status);
        return jsonResponse(json);
    }

    private byte[] jsonResponse(JSONObject json) {
        return StepDaddyHttp.responseBytes(
            "HTTP/1.1 200 OK",
            "application/json; charset=utf-8",
            json.toString()
        );
    }

    private Context launchMain(Context context) {
        Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.setClassName(context.getPackageName(), "ar.tvplayer.tv.ui.MainActivity");
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        context.startActivity(intent);
        return context;
    }
}
