package com.medialabbridge.app;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

final class BridgeHttpClient {
    private static final int CONNECT_TIMEOUT_MS = 8_000;
    private static final int[] RETRY_DELAYS_MS = {0, 1_000, 2_500};

    interface RetryListener {
        void onRetry(int attempt, int total);
    }

    static final class Result {
        final int code;
        final String body;

        Result(int code, String body) {
            this.code = code;
            this.body = body;
        }
    }

    private BridgeHttpClient() {
    }

    static Result requestWithRetry(
            String method,
            String endpoint,
            String body,
            String bearerToken,
            int readTimeoutMs,
            RetryListener listener) throws Exception {
        IOException lastError = null;
        for (int attempt = 0; attempt < RETRY_DELAYS_MS.length; attempt++) {
            if (RETRY_DELAYS_MS[attempt] > 0) {
                Thread.sleep(RETRY_DELAYS_MS[attempt]);
            }
            try {
                return request(method, endpoint, body, bearerToken, readTimeoutMs);
            } catch (IOException error) {
                lastError = error;
                if (listener != null) {
                    listener.onRetry(attempt + 1, RETRY_DELAYS_MS.length);
                }
            }
        }
        throw new IOException(
                "No se pudo completar la conexión después de " + RETRY_DELAYS_MS.length + " intentos. "
                        + (lastError == null ? "" : lastError.getMessage()),
                lastError);
    }

    private static Result request(
            String method,
            String endpoint,
            String body,
            String bearerToken,
            int readTimeoutMs) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(endpoint).openConnection();
        try {
            connection.setRequestMethod(method);
            connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
            connection.setReadTimeout(readTimeoutMs);
            connection.setUseCaches(false);
            connection.setRequestProperty("Accept", "application/json");
            connection.setRequestProperty("Connection", "keep-alive");
            if (bearerToken != null) {
                connection.setRequestProperty("Authorization", "Bearer " + bearerToken);
            }
            if (body != null) {
                byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
                connection.setDoOutput(true);
                connection.setFixedLengthStreamingMode(bytes.length);
                connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
                try (OutputStream output = new BufferedOutputStream(connection.getOutputStream(), 64 * 1024)) {
                    output.write(bytes);
                }
            }
            int code = connection.getResponseCode();
            InputStream stream = code >= 400 ? connection.getErrorStream() : connection.getInputStream();
            return new Result(code, readAll(stream));
        } finally {
            connection.disconnect();
        }
    }

    private static String readAll(InputStream stream) throws IOException {
        if (stream == null) {
            return "";
        }
        StringBuilder result = new StringBuilder();
        char[] buffer = new char[16 * 1024];
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            int read;
            while ((read = reader.read(buffer)) != -1) {
                result.append(buffer, 0, read);
            }
        }
        return result.toString().trim();
    }
}
