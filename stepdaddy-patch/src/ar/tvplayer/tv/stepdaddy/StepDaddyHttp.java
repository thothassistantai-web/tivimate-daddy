package ar.tvplayer.tv.stepdaddy;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

final class StepDaddyHttp {
    private StepDaddyHttp() {
    }

    static String get(String urlString) {
        return get(urlString, null);
    }

    static String get(String urlString, java.util.Map<String, String> headers) {
        HttpURLConnection connection = null;
        try {
            URL url = new URL(urlString);
            connection = (HttpURLConnection) url.openConnection();
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(15000);
            connection.setRequestMethod("GET");
            if (headers != null) {
                for (java.util.Map.Entry<String, String> entry : headers.entrySet()) {
                    connection.setRequestProperty(entry.getKey(), entry.getValue());
                }
            }
            connection.connect();
            int code = connection.getResponseCode();
            InputStream stream = code >= 200 && code < 300
                ? connection.getInputStream()
                : connection.getErrorStream();
            if (stream == null) {
                return null;
            }
            BufferedReader reader = new BufferedReader(new InputStreamReader(stream, "UTF-8"));
            StringBuilder body = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                body.append(line);
            }
            reader.close();
            if (code < 200 || code >= 300) {
                StepDaddyLog.w("HTTP " + code + " for " + urlString);
                return null;
            }
            return body.toString();
        } catch (Exception error) {
            StepDaddyLog.w("GET failed: " + urlString, error);
            return null;
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    static byte[] responseBytes(String statusLine, String contentType, String body) {
        String payload = body == null ? "" : body;
        String response = statusLine + "\r\n"
            + "Content-Type: " + contentType + "\r\n"
            + "Content-Length: " + payload.getBytes().length + "\r\n"
            + "Connection: close\r\n"
            + "\r\n"
            + payload;
        return response.getBytes();
    }

    static final class HttpRequest {
        final String path;
        final String query;

        HttpRequest(String path, String query) {
            this.path = path;
            this.query = query;
        }
    }

    static HttpRequest readRequest(InputStream inputStream) throws Exception {
        BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, "UTF-8"));
        String requestLine = reader.readLine();
        if (requestLine == null || requestLine.isEmpty()) {
            return new HttpRequest("/", null);
        }
        String[] parts = requestLine.trim().split(" ");
        if (parts.length < 2) {
            return new HttpRequest("/", null);
        }
        String raw = parts[1];
        String path = raw;
        String query = null;
        int q = raw.indexOf('?');
        if (q >= 0) {
            path = raw.substring(0, q);
            query = raw.substring(q + 1);
        }
        if (path.isEmpty()) {
            path = "/";
        }
        return new HttpRequest(path, query);
    }

    static String readRequestPath(InputStream inputStream) throws Exception {
        return readRequest(inputStream).path;
    }

    static String queryParam(String query, String key) {
        if (query == null || key == null) {
            return null;
        }
        for (String pair : query.split("&")) {
            int eq = pair.indexOf('=');
            if (eq <= 0) {
                continue;
            }
            String name = pair.substring(0, eq);
            if (!key.equals(name)) {
                continue;
            }
            String value = pair.substring(eq + 1);
            try {
                return java.net.URLDecoder.decode(value, "UTF-8");
            } catch (Exception ignored) {
                return value;
            }
        }
        return null;
    }

    static int postJson(String urlString, String jsonBody) {
        HttpURLConnection connection = null;
        try {
            URL url = new URL(urlString);
            connection = (HttpURLConnection) url.openConnection();
            connection.setConnectTimeout(3000);
            connection.setReadTimeout(5000);
            connection.setRequestMethod("POST");
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            byte[] bytes = jsonBody.getBytes("UTF-8");
            connection.setFixedLengthStreamingMode(bytes.length);
            connection.getOutputStream().write(bytes);
            connection.getOutputStream().flush();
            return connection.getResponseCode();
        } catch (Exception error) {
            StepDaddyLog.w("POST failed: " + urlString, error);
            return -1;
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }
}
