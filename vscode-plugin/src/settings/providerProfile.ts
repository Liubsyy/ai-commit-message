import { randomUUID } from 'crypto';

export const TYPE_OPENAI_COMPATIBLE = 'openai-compatible';
export const TYPE_MANAGED_FREE = 'managed-free';

/**
 * 一个供应商配置:同一 baseUrl/apiKey 下可维护多个 model。
 * API Key 不在此处,存 SecretStorage。
 */
export interface ProviderProfile {
  id: string;
  /** 内置托管模型与用户自定义 OpenAI 兼容配置使用不同客户端。 */
  type: string;
  name: string;
  baseUrl: string;
  temperature: number;
  /** 自定义 prompt 模板,留空表示使用内置默认模板 */
  prompt: string;
  /** 输出语言:auto(由 prompt 决定)/ zh / en / … */
  outputLanguage: string;
  models: string[];
  selectedModel: string;
}

export function createProfile(): ProviderProfile {
  return {
    id: randomUUID(),
    type: TYPE_OPENAI_COMPATIBLE,
    name: '',
    baseUrl: '',
    temperature: 0.7,
    prompt: '',
    outputLanguage: 'auto',
    models: [],
    selectedModel: '',
  };
}

export function copyProfile(p: ProviderProfile): ProviderProfile {
  return { ...p, models: [...p.models] };
}

export function isManagedFree(p: ProviderProfile): boolean {
  return p.type === TYPE_MANAGED_FREE;
}

/** 反序列化兜底:补齐缺失字段,避免旧数据或手改数据破坏运行时假设 */
export function normalizeProfileShape(p: Partial<ProviderProfile>): ProviderProfile {
  const base = createProfile();
  return {
    id: typeof p.id === 'string' && p.id ? p.id : base.id,
    type: typeof p.type === 'string' && p.type ? p.type : base.type,
    name: typeof p.name === 'string' ? p.name : '',
    baseUrl: typeof p.baseUrl === 'string' ? p.baseUrl : '',
    temperature: typeof p.temperature === 'number' ? p.temperature : base.temperature,
    prompt: typeof p.prompt === 'string' ? p.prompt : '',
    outputLanguage: typeof p.outputLanguage === 'string' && p.outputLanguage ? p.outputLanguage : 'auto',
    models: Array.isArray(p.models) ? p.models.filter((m): m is string => typeof m === 'string') : [],
    selectedModel: typeof p.selectedModel === 'string' ? p.selectedModel : '',
  };
}
