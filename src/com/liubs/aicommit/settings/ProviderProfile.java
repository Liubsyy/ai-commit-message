package com.liubs.aicommit.settings;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 一个供应商配置:同一 baseUrl/apiKey 下可维护多个 model。
 * 公开字段用于 XML 持久化(API Key 不在此处,存 PasswordSafe)。
 */
public class ProviderProfile {
    public static final String TYPE_OPENAI_COMPATIBLE = "openai-compatible";
    public static final String TYPE_MANAGED_FREE = "managed-free";

    public String id = UUID.randomUUID().toString();
    /** 内置托管模型与用户自定义 OpenAI 兼容配置使用不同客户端。 */
    public String type = TYPE_OPENAI_COMPATIBLE;
    public String name = "";
    public String baseUrl = "";
    public double temperature = 0.7;
    /** 自定义 prompt 模板,留空表示使用内置默认模板 */
    public String prompt = "";
    /** 输出语言:auto(由 prompt 决定)/ zh / en */
    public String outputLanguage = "auto";
    public List<String> models = new ArrayList<>();
    public String selectedModel = "";

    public ProviderProfile() {
    }

    public ProviderProfile copy() {
        ProviderProfile p = new ProviderProfile();
        p.id = id;
        p.type = type;
        p.name = name;
        p.baseUrl = baseUrl;
        p.temperature = temperature;
        p.prompt = prompt;
        p.outputLanguage = outputLanguage;
        p.models = new ArrayList<>(models);
        p.selectedModel = selectedModel;
        return p;
    }

    public boolean isManagedFree() {
        return TYPE_MANAGED_FREE.equals(type);
    }

    @Override
    public String toString() {
        return name;
    }
}
