import { ManagedFreeClient } from '../ai/managedFreeClient';
import { isManagedFree, ProviderProfile } from '../settings/providerProfile';
import { getSettings } from '../settings/settingsStore';

const REFRESH_INTERVAL_MS = 5 * 60 * 1000;

let refreshInProgress = false;
let lastRefreshAttempt = 0;
let pending: Promise<void> | null = null;

/**
 * 免费模型列表后台刷新,5 分钟节流;
 * 对应 JetBrains 版打开模型菜单时的 ManagedFreeMenuGroup 刷新逻辑。
 */
export function refreshManagedFreeModels(profile: ProviderProfile): Promise<void> {
  if (!isManagedFree(profile)) {
    return Promise.resolve();
  }
  const now = Date.now();
  if (refreshInProgress && pending) {
    return pending;
  }
  if (lastRefreshAttempt !== 0 && now - lastRefreshAttempt < REFRESH_INTERVAL_MS) {
    return Promise.resolve();
  }
  refreshInProgress = true;
  lastRefreshAttempt = now;
  const baseUrl = profile.baseUrl;
  const id = profile.id;
  pending = (async () => {
    try {
      const models = await new ManagedFreeClient().listModels(baseUrl, '');
      const settings = getSettings();
      const current = settings.findProfile(id);
      if (!current || sameModels(models, current.models)) {
        return;
      }
      current.models = [...models];
      if (!models.includes(current.selectedModel)) {
        current.selectedModel = models[0];
      }
      await settings.save();
    } catch {
      // 打开菜单触发的静默刷新失败不打扰用户;真正生成时会给出明确报错
    } finally {
      refreshInProgress = false;
      pending = null;
    }
  })();
  return pending;
}

function sameModels(a: string[], b: string[]): boolean {
  return a.length === b.length && a.every((value, i) => value === b[i]);
}
