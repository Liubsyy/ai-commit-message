package com.liubs.aicommit.settings;

import com.intellij.credentialStore.CredentialAttributes;
import com.intellij.credentialStore.CredentialAttributesKt;
import com.intellij.credentialStore.Credentials;
import com.intellij.ide.passwordSafe.PasswordSafe;

/** API Key 按 profile id 存取 PasswordSafe,不落明文配置文件 */
public final class ApiKeyStore {

    private ApiKeyStore() {
    }

    private static CredentialAttributes attributes(String profileId) {
        return new CredentialAttributes(
                CredentialAttributesKt.generateServiceName("AI Commit Message", profileId));
    }

    public static String get(String profileId) {
        Credentials credentials = PasswordSafe.getInstance().get(attributes(profileId));
        String key = credentials == null ? null : credentials.getPasswordAsString();
        return key == null ? "" : key;
    }

    public static void set(String profileId, String apiKey) {
        if (apiKey == null || apiKey.trim().isEmpty()) {
            PasswordSafe.getInstance().set(attributes(profileId), null);
        } else {
            PasswordSafe.getInstance().set(attributes(profileId), new Credentials("api-key", apiKey.trim()));
        }
    }
}
