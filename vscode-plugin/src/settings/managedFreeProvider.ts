import * as fs from 'fs';
import * as path from 'path';
import { ProviderProfile, TYPE_MANAGED_FREE } from './providerProfile';

export const PROFILE_ID = 'managed-free';

let extensionPath = '';
let cachedBaseUrl: string | undefined;

export function init(extensionRoot: string): void {
  extensionPath = extensionRoot;
  cachedBaseUrl = undefined;
}

/** 从打包资源创建由项目方托管的免费模型配置。 */
export function createManagedFreeProfile(): ProviderProfile {
  return {
    id: PROFILE_ID,
    type: TYPE_MANAGED_FREE,
    name: 'free',
    baseUrl: baseUrl(),
    temperature: 0.2,
    prompt: '',
    outputLanguage: 'auto',
    models: [],
    selectedModel: '',
  };
}

/** 保留用户选择的模型和语言,同时修正不可编辑的托管字段。 */
export function normalizeManagedFree(profile: ProviderProfile): void {
  const defaults = createManagedFreeProfile();
  profile.id = PROFILE_ID;
  profile.type = TYPE_MANAGED_FREE;
  profile.name = defaults.name;
  profile.baseUrl = defaults.baseUrl;
  profile.temperature = defaults.temperature;
  if (!Array.isArray(profile.models)) {
    profile.models = [];
  }
  if (typeof profile.selectedModel !== 'string') {
    profile.selectedModel = '';
  }
}

function baseUrl(): string {
  if (cachedBaseUrl === undefined) {
    cachedBaseUrl = load();
  }
  return cachedBaseUrl;
}

function load(): string {
  // 资源不可用时保持未配置状态,由客户端返回明确错误。
  try {
    const file = path.join(extensionPath, 'resources', 'managed-free-provider.json');
    const parsed = JSON.parse(fs.readFileSync(file, 'utf8')) as { baseUrl?: unknown };
    return typeof parsed.baseUrl === 'string' ? parsed.baseUrl.trim() : '';
  } catch {
    return '';
  }
}
