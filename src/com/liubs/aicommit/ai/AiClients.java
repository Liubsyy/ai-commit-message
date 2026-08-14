package com.liubs.aicommit.ai;

import com.liubs.aicommit.settings.ProviderProfile;

/** 按配置类型选择客户端，避免把托管网关当作通用 OpenAI 代理调用。 */
public final class AiClients {

    private AiClients() {
    }

    public static AiClient create(ProviderProfile profile) {
        return profile.isManagedFree()
                ? new ManagedFreeClient()
                : new OpenAiCompatibleClient();
    }
}
