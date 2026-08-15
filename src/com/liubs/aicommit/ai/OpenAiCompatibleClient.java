package com.liubs.aicommit.ai;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.liubs.aicommit.settings.OutputLanguages;
import com.liubs.aicommit.settings.ProviderProfile;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * OpenAI 兼容客户端:POST {baseUrl}/chat/completions、GET {baseUrl}/models。
 * 通过配置不同 baseUrl/model/key 支持 OpenAI、DeepSeek、Moonshot、Ollama 等。
 * 仅用 JDK HttpURLConnection + IDEA 自带 Gson,零第三方依赖。
 */
public final class OpenAiCompatibleClient implements AiClient {

    private static final Gson GSON = new Gson();
    private static final int CONNECT_TIMEOUT_MS = 15_000;
    private static final int READ_TIMEOUT_MS = 120_000;
    private static final String OUTPUT_RULE =
            "\n\nOutput only the final commit message itself (single line, optionally with a body)."
                    + " Do not output any explanation, quotes, or Markdown code fences.";

    @Override
    public String generateCommitMessage(ProviderProfile profile, String apiKey, String diff,
                                        @Nullable ProgressIndicator indicator) throws IOException {
        JsonObject body = buildChatBody(profile, "Here is the diff of the changes to commit:\n\n" + diff, null);
        String response = exchange("POST", joinUrl(profile.baseUrl, "chat/completions"),
                apiKey, GSON.toJson(body), indicator);
        return cleanup(extractContent(response));
    }

    /** 测试连接用:发一个最小请求,返回模型的简短回复 */
    @Override
    public String ping(ProviderProfile profile, String apiKey,
                       @Nullable ProgressIndicator indicator) throws IOException {
        JsonObject body = buildChatBody(profile, "Reply with: OK", 8);
        String response = exchange("POST", joinUrl(profile.baseUrl, "chat/completions"),
                apiKey, GSON.toJson(body), indicator);
        return abbreviate(extractContent(response).trim());
    }

    @Override
    public List<String> listModels(String baseUrl, String apiKey,
                                   @Nullable ProgressIndicator indicator) throws IOException {
        String response = exchange("GET", joinUrl(baseUrl, "models"), apiKey, null, indicator);
        JsonObject root = parseObject(response);
        JsonArray data = root.getAsJsonArray("data");
        List<String> result = new ArrayList<>();
        if (data != null) {
            for (JsonElement element : data) {
                if (!element.isJsonObject()) {
                    continue;
                }
                JsonElement id = element.getAsJsonObject().get("id");
                if (id != null && !result.contains(id.getAsString())) {
                    result.add(id.getAsString());
                }
            }
        }
        if (result.isEmpty()) {
            throw new IOException("Provider returned no models: " + abbreviate(response));
        }
        Collections.sort(result);
        return result;
    }

    private static JsonObject buildChatBody(ProviderProfile profile, String userContent,
                                            @Nullable Integer maxTokens) {
        JsonObject body = new JsonObject();
        body.addProperty("model", profile.selectedModel);
        body.addProperty("temperature", profile.temperature);
        body.addProperty("stream", false);
        if (maxTokens != null) {
            body.addProperty("max_tokens", maxTokens);
        }
        JsonArray messages = new JsonArray();
        messages.add(message("system", systemPrompt(profile)));
        messages.add(message("user", userContent));
        body.add("messages", messages);
        return body;
    }

    private static String systemPrompt(ProviderProfile profile) {
        String template = profile.prompt == null ? "" : profile.prompt.trim();
        if (template.isEmpty()) {
            template = PromptTemplates.getDefaultPrompt();
        }
        String languageName = OutputLanguages.englishName(profile.outputLanguage);
        String languageRule = languageName == null
                ? "" : "\n\nWrite the commit message in " + languageName + ".";
        return template + languageRule + OUTPUT_RULE;
    }

    private static JsonObject message(String role, String content) {
        JsonObject msg = new JsonObject();
        msg.addProperty("role", role);
        msg.addProperty("content", content);
        return msg;
    }

    private static String extractContent(String response) throws IOException {
        JsonObject root = parseObject(response);
        JsonArray choices = root.getAsJsonArray("choices");
        if (choices == null || choices.size() == 0) {
            throw new IOException("No result returned: " + abbreviate(response));
        }
        JsonObject message = choices.get(0).getAsJsonObject().getAsJsonObject("message");
        if (message == null || message.get("content") == null || message.get("content").isJsonNull()) {
            throw new IOException("Unexpected response format: " + abbreviate(response));
        }
        return message.get("content").getAsString();
    }

    /** 去掉模型可能包上的代码块围栏和引号 */
    private static String cleanup(String text) {
        String t = text.trim();
        if (t.startsWith("```")) {
            int firstNewline = t.indexOf('\n');
            if (firstNewline >= 0) {
                t = t.substring(firstNewline + 1);
            }
            if (t.endsWith("```")) {
                t = t.substring(0, t.length() - 3);
            }
            t = t.trim();
        }
        if (t.length() >= 2 && t.startsWith("\"") && t.endsWith("\"")) {
            t = t.substring(1, t.length() - 1).trim();
        }
        return t;
    }

    private static String exchange(String method, String url, String apiKey,
                                   @Nullable String body,
                                   @Nullable ProgressIndicator indicator) throws IOException {
        checkCanceled(indicator);
        HttpURLConnection connection = openConnection(url);
        try {
            connection.setRequestMethod(method);
            connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
            connection.setReadTimeout(READ_TIMEOUT_MS);
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setRequestProperty("Accept", "application/json");
            if (apiKey != null && !apiKey.trim().isEmpty()) {
                connection.setRequestProperty("Authorization", "Bearer " + apiKey.trim());
            }
            if (body != null) {
                connection.setDoOutput(true);
                try (OutputStream out = connection.getOutputStream()) {
                    out.write(body.getBytes(StandardCharsets.UTF_8));
                }
            }
            int code = connection.getResponseCode();
            checkCanceled(indicator);
            InputStream stream = code >= 200 && code < 300
                    ? connection.getInputStream() : connection.getErrorStream();
            String text = readAll(stream);
            String providerError = extractProviderError(text);
            if (providerError != null) {
                throw new IOException("AI provider: " + abbreviate(providerError)
                        + " (HTTP " + code + ")");
            }
            if (code < 200 || code >= 300) {
                String detail = unexpectedResponseDetail(text);
                throw new IOException(detail.isEmpty()
                        ? "The AI provider request failed (HTTP " + code + ") with an empty response."
                        : "The AI provider request failed (HTTP " + code + "). " + detail);
            }
            return text;
        } finally {
            connection.disconnect();
        }
    }

    private static HttpURLConnection openConnection(String url) throws IOException {
        try {
            return (HttpURLConnection) URI.create(url).toURL().openConnection();
        } catch (RuntimeException e) {
            throw new IOException("Invalid provider URL: " + url, e);
        }
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

    @Nullable
    private static String extractProviderError(String response) {
        try {
            JsonObject root = JsonParser.parseString(response).getAsJsonObject();
            JsonElement error = root.get("error");
            String message = errorText(error);
            if (message != null) {
                return message;
            }
            if (root.has("choices") || root.has("data")) {
                return null;
            }
            message = firstText(root, "message", "msg", "detail", "error_description");
            return message == null || message.trim().isEmpty() ? null : message;
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    @Nullable
    private static String errorText(@Nullable JsonElement error) {
        if (error == null || error.isJsonNull()) {
            return null;
        }
        if (error.isJsonPrimitive()) {
            return error.getAsString();
        }
        if (error.isJsonObject()) {
            return firstText(error.getAsJsonObject(),
                    "message", "msg", "detail", "error_description");
        }
        if (error.isJsonArray() && error.getAsJsonArray().size() > 0) {
            return errorText(error.getAsJsonArray().get(0));
        }
        return null;
    }

    @Nullable
    private static String firstText(JsonObject object, String... names) {
        for (String name : names) {
            JsonElement value = object.get(name);
            if (value != null && !value.isJsonNull() && value.isJsonPrimitive()) {
                return value.getAsString();
            }
        }
        return null;
    }

    private static JsonObject parseObject(String response) throws IOException {
        try {
            return JsonParser.parseString(response).getAsJsonObject();
        } catch (RuntimeException e) {
            String detail = unexpectedResponseDetail(response);
            String message = detail.isEmpty()
                    ? "The AI provider returned an empty response."
                    : "The AI provider returned an unexpected response. " + detail;
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
            return "The server returned a web page instead of API data.";
        }
        return "Response: " + abbreviate(value.replaceAll("\\s+", " "));
    }

    private static String joinUrl(String baseUrl, String path) {
        String base = baseUrl == null ? "" : baseUrl.trim();
        while (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        return base + "/" + path;
    }

    private static String abbreviate(String text) {
        String t = text == null ? "" : text.trim();
        return t.length() <= 300 ? t : t.substring(0, 300) + "…";
    }

    private static void checkCanceled(@Nullable ProgressIndicator indicator) {
        if (indicator != null && indicator.isCanceled()) {
            throw new ProcessCanceledException();
        }
    }
}
