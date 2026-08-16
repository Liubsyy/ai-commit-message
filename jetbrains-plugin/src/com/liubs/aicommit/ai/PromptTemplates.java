package com.liubs.aicommit.ai;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

public final class PromptTemplates {

    /**
     * Keep the button useful even when an IDE development build fails to copy
     * resources into the module output directory. The packaged plugin still
     * uses the richer Markdown template from resources/prompts.
     */
    private static final String FALLBACK_PROMPT = String.join("\n",
            "## Commit Format",
            "",
            "```text",
            "<type>(<scope>): <subject>",
            "```",
            "",
            "Use an English Conventional Commit type such as feat, fix, docs, "
                    + "style, refactor, perf, test, build, ci, or chore.",
            "Choose type and scope only from the supplied diff.",
            "Write one concise subject sentence in the requested language.",
            "Do not end the subject with a period and do not invent changes.",
            "",
            "Examples:",
            "",
            "```text",
            "feat(settings): support custom provider profiles",
            "fix(editor): preserve the generated commit message",
            "```",
            "");

    private static volatile String defaultPrompt;

    private PromptTemplates() {
    }

    public static String getDefaultPrompt() {
        String cached = defaultPrompt;
        if (cached != null) {
            return cached;
        }
        synchronized (PromptTemplates.class) {
            if (defaultPrompt == null) {
                defaultPrompt = load();
            }
            return defaultPrompt;
        }
    }

    private static String load() {
        try (InputStream in = PromptTemplates.class.getResourceAsStream("/prompts/default-commit-prompt.md")) {
            if (in == null) {
                return FALLBACK_PROMPT;
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int n;
            while ((n = in.read(buffer)) > 0) {
                out.write(buffer, 0, n);
            }
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return FALLBACK_PROMPT;
        }
    }
}
