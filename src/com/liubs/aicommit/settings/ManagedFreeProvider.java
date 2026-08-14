package com.liubs.aicommit.settings;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Properties;

/** 从打包资源创建由项目方托管的免费模型配置。 */
public final class ManagedFreeProvider {

    public static final String PROFILE_ID = "managed-free";
    private static final String RESOURCE = "/managed-free-provider.properties";

    private static final Properties PROPERTIES = loadProperties();

    private ManagedFreeProvider() {
    }

    public static ProviderProfile createProfile() {
        ProviderProfile profile = new ProviderProfile();
        profile.id = PROFILE_ID;
        profile.type = ProviderProfile.TYPE_MANAGED_FREE;
        profile.name = "free";
        profile.baseUrl = property("baseUrl");
        profile.temperature = 0.2;
        profile.outputLanguage = "auto";
        profile.models = new ArrayList<>();
        profile.selectedModel = "";
        return profile;
    }

    /** 保留用户选择的模型和语言，同时修正不可编辑的托管字段。 */
    public static void normalize(ProviderProfile profile) {
        ProviderProfile defaults = createProfile();
        profile.id = PROFILE_ID;
        profile.type = ProviderProfile.TYPE_MANAGED_FREE;
        profile.name = defaults.name;
        profile.baseUrl = defaults.baseUrl;
        profile.temperature = defaults.temperature;
        if (profile.models == null) {
            profile.models = new ArrayList<>();
        }
        if (profile.selectedModel == null) {
            profile.selectedModel = "";
        }
    }

    private static String property(String name) {
        String value = PROPERTIES.getProperty(name);
        return isBlank(value) ? "" : value.trim();
    }

    private static Properties loadProperties() {
        Properties properties = new Properties();
        try (InputStream in = ManagedFreeProvider.class.getResourceAsStream(RESOURCE)) {
            if (in != null) {
                properties.load(in);
            }
        } catch (IOException ignored) {
            // 打包资源不可用时保持未配置状态，由客户端返回明确错误。
        }
        return properties;
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
