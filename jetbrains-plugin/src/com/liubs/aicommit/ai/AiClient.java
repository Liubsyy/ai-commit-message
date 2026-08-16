package com.liubs.aicommit.ai;

import com.intellij.openapi.progress.ProgressIndicator;
import com.liubs.aicommit.settings.ProviderProfile;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.List;

public interface AiClient {

    /** 根据 diff 生成提交信息 */
    String generateCommitMessage(ProviderProfile profile, String apiKey, String diff,
                                 @Nullable ProgressIndicator indicator) throws IOException;

    /** 发起最小请求验证当前模型可用。 */
    String ping(ProviderProfile profile, String apiKey,
                @Nullable ProgressIndicator indicator) throws IOException;

    /** 从上游拉取可用模型列表(GET {baseUrl}/models) */
    List<String> listModels(String baseUrl, String apiKey,
                            @Nullable ProgressIndicator indicator) throws IOException;
}
