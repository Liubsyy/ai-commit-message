package com.liubs.aicommit.ai;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.liubs.aicommit.settings.AiCommitSettings;
import com.liubs.aicommit.settings.ProviderProfile;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;

public final class ManagedFreeClient implements AiClient {

    private static final Gson GSON = new Gson();
    private static final int CONNECT_TIMEOUT_MS = 10_000;
    private static final int READ_TIMEOUT_MS = 30_000;
    private static final int CANCELLATION_POLL_MS = 100;

    @Override
    public String generateCommitMessage(ProviderProfile profile, String apiKey, String diff,
                                        @Nullable ProgressIndicator indicator) throws IOException {
        ensureConfigured(profile.baseUrl);
        JsonObject body = new JsonObject();
        body.addProperty("model", profile.selectedModel);
        body.addProperty("diff", diff);
        body.addProperty("language", profile.outputLanguage);
        body.addProperty("prompt", profile.prompt == null ? "" : profile.prompt);
        String response = exchange("POST", joinUrl(profile.baseUrl, "commit-message"),
                GSON.toJson(body), true, indicator);
        JsonObject root = parseObject(response);
        JsonElement message = root.get("message");
        if (message == null || message.isJsonNull()) {
            throw new IOException("Free gateway returned no commit message");
        }
        return message.getAsString().trim();
    }

    @Override
    public String ping(ProviderProfile profile, String apiKey,
                       @Nullable ProgressIndicator indicator) throws IOException {
        ensureConfigured(profile.baseUrl);
        String response = exchange("GET", joinUrl(profile.baseUrl, "health"),
                null, false, indicator);
        JsonObject root = parseObject(response);
        JsonElement status = root.get("status");
        if (status == null || !"ok".equals(status.getAsString())) {
            throw new IOException("Free gateway health check failed");
        }
        return "OK";
    }

    @Override
    public List<String> listModels(String baseUrl, String apiKey,
                                   @Nullable ProgressIndicator indicator) throws IOException {
        ensureConfigured(baseUrl);
        String response = exchange("GET", joinUrl(baseUrl, "models"),
                null, true, indicator);
        JsonObject root = parseObject(response);
        JsonArray data = root.getAsJsonArray("data");
        List<String> models = new ArrayList<>();
        if (data != null) {
            for (JsonElement element : data) {
                if (!element.isJsonObject()) {
                    continue;
                }
                JsonElement id = element.getAsJsonObject().get("id");
                if (id != null && !id.isJsonNull() && !models.contains(id.getAsString())) {
                    models.add(id.getAsString());
                }
            }
        }
        if (models.isEmpty()) {
            throw new IOException("Free gateway returned no models");
        }
        Collections.sort(models);
        return models;
    }

    private static String exchange(String method, String url, @Nullable String body,
                                   boolean includeInstallationId,
                                   @Nullable ProgressIndicator indicator) throws IOException {
        checkCanceled(indicator);
        AtomicReference<HttpURLConnection> connectionRef = new AtomicReference<>();
        if (indicator == null) {
            return performExchange(method, url, body, includeInstallationId, connectionRef);
        }
        FutureTask<String> request = new FutureTask<>(
                () -> performExchange(method, url, body, includeInstallationId, connectionRef));
        ApplicationManager.getApplication().executeOnPooledThread(request);
        try {
            while (true) {
                try {
                    return request.get(CANCELLATION_POLL_MS, TimeUnit.MILLISECONDS);
                } catch (TimeoutException ignored) {
                    checkCanceled(indicator);
                }
            }
        } catch (ProcessCanceledException ex) {
            cancelRequest(request, connectionRef);
            throw ex;
        } catch (InterruptedException ex) {
            cancelRequest(request, connectionRef);
            Thread.currentThread().interrupt();
            throw new ProcessCanceledException();
        } catch (ExecutionException ex) {
            Throwable cause = ex.getCause();
            if (cause instanceof IOException) {
                throw (IOException) cause;
            }
            if (cause instanceof ProcessCanceledException) {
                throw (ProcessCanceledException) cause;
            }
            if (cause instanceof RuntimeException) {
                throw (RuntimeException) cause;
            }
            if (cause instanceof Error) {
                throw (Error) cause;
            }
            throw new IOException("Free gateway request failed", cause);
        }
    }

    private static String performExchange(String method, String url, @Nullable String body,
                                          boolean includeInstallationId,
                                          AtomicReference<HttpURLConnection> connectionRef)
            throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connectionRef.set(connection);
        try {
            if (Thread.currentThread().isInterrupted()) {
                throw new ProcessCanceledException();
            }
            connection.setRequestMethod(method);
            connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
            connection.setReadTimeout(READ_TIMEOUT_MS);
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setRequestProperty("Accept", "application/json");
            connection.setRequestProperty("X-AI-Commit-Client", "idea-plugin");
            if (includeInstallationId) {
                connection.setRequestProperty("X-AI-Commit-Installation",
                        AiCommitSettings.getInstance().getState().installationId);
            }
            if (body != null) {
                connection.setDoOutput(true);
                try (OutputStream out = connection.getOutputStream()) {
                    out.write(body.getBytes(StandardCharsets.UTF_8));
                }
            }
            int code = connection.getResponseCode();
            InputStream stream = code >= 200 && code < 300
                    ? connection.getInputStream() : connection.getErrorStream();
            String text = readAll(stream);
            String gatewayError = extractGatewayError(text);
            if (gatewayError != null) {
                throw new IOException("Free model service: " + abbreviate(gatewayError)
                        + " (HTTP " + code + ")");
            }
            if (code < 200 || code >= 300) {
                throw new IOException(errorMessage(code, text));
            }
            return text;
        } finally {
            connection.disconnect();
            connectionRef.compareAndSet(connection, null);
        }
    }

    private static void cancelRequest(Future<String> request,
                                      AtomicReference<HttpURLConnection> connectionRef) {
        request.cancel(true);
        HttpURLConnection connection = connectionRef.getAndSet(null);
        if (connection != null) {
            connection.disconnect();
        }
    }

    private static String errorMessage(int code, String response) {
        String detail = unexpectedResponseDetail(response);
        return detail.isEmpty()
                ? "The free model service request failed (HTTP " + code + "). Please try again later."
                : "The free model service request failed (HTTP " + code + "). " + detail;
    }

    @Nullable
    private static String extractGatewayError(String response) {
        try {
            JsonObject root = new JsonParser().parse(response).getAsJsonObject();
            JsonElement error = root.get("error");
            if (error == null || error.isJsonNull()) {
                return null;
            }
            if (error.isJsonPrimitive()) {
                return error.getAsString();
            }
            JsonElement message = error.isJsonObject()
                    ? error.getAsJsonObject().get("message") : null;
            if (message != null && !message.isJsonNull()) {
                return message.getAsString();
            }
        } catch (RuntimeException ignored) {
        }
        return null;
    }

    private static String readAll(@Nullable InputStream stream) throws IOException {
        if (stream == null) {
            return "";
        }
        try (InputStream in = stream) {
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int n;
            while ((n = in.read(buffer)) > 0) {
                out.write(buffer, 0, n);
            }
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        }
    }

    private static JsonObject parseObject(String response) throws IOException {
        try {
            return new JsonParser().parse(response).getAsJsonObject();
        } catch (RuntimeException e) {
            String detail = unexpectedResponseDetail(response);
            String message = detail.isEmpty()
                    ? "The free model service returned an empty response. Please try again later."
                    : "The free model service returned an unexpected response. " + detail;
            throw new IOException(message, e);
        }
    }

    private static String unexpectedResponseDetail(@Nullable String response) {
        String value = response == null ? "" : response.trim();
        if (value.isEmpty()) {
            return "";
        }
        String lower = value.toLowerCase(java.util.Locale.ROOT);
        if (lower.startsWith("<!doctype html") || lower.startsWith("<html")) {
            return "The gateway returned a web page instead of API data. Please try again later.";
        }
        return "Please try again later. Response: "
                + abbreviate(value.replaceAll("\\s+", " "));
    }

    private static String joinUrl(String baseUrl, String path) {
        String base = canonicalBaseUrl(baseUrl);
        return base + "/" + path;
    }

    private static String canonicalBaseUrl(String baseUrl) {
        String base = baseUrl == null ? "" : baseUrl.trim();
        while (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        return base;
    }

    private static void ensureConfigured(String baseUrl) throws IOException {
        if (baseUrl == null || baseUrl.trim().isEmpty()) {
            throw new IOException("Free gateway URL has not been configured by the plugin publisher");
        }
    }

    private static String abbreviate(String text) {
        String value = text == null ? "" : text.trim();
        return value.length() <= 300 ? value : value.substring(0, 300) + "…";
    }

    private static void checkCanceled(@Nullable ProgressIndicator indicator) {
        if (indicator != null && indicator.isCanceled()) {
            throw new ProcessCanceledException();
        }
    }
}
