import { isManagedFree, ProviderProfile } from '../settings/providerProfile';
import { AiClient } from './aiClient';
import { ManagedFreeClient } from './managedFreeClient';
import { OpenAiCompatibleClient } from './openAiCompatibleClient';

/** 按配置类型选择客户端,避免把托管网关当作通用 OpenAI 代理调用。 */
export function create(profile: ProviderProfile): AiClient {
  return isManagedFree(profile) ? new ManagedFreeClient() : new OpenAiCompatibleClient();
}
