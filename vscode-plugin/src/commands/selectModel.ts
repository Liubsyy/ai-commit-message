import * as vscode from 'vscode';
import { ApiKeyStore } from '../settings/apiKeyStore';
import { isManagedFree, ProviderProfile } from '../settings/providerProfile';
import { getSettings } from '../settings/settingsStore';
import { SettingsPanel } from '../ui/settingsPanel';
import { refreshManagedFreeModels } from './managedFreeRefresh';

interface ProviderItem extends vscode.QuickPickItem {
  profileId?: string;
  openSettings?: boolean;
}

interface ModelItem extends vscode.QuickPickItem {
  model: string;
}

/**
 * 对应 JetBrains 版的二级下拉:一级为供应商配置(当前生效的带勾号),
 * 二级列出该配置的 model,底部为「设置模型…」。
 */
export async function selectModel(
  context: vscode.ExtensionContext,
  apiKeyStore: ApiKeyStore,
): Promise<void> {
  const settings = getSettings();
  const state = settings.getState();
  const selected = settings.getSelectedProfile();

  const managed = state.profiles.find(isManagedFree);
  if (managed) {
    void refreshManagedFreeModels(managed);
  }

  const items: ProviderItem[] = state.profiles.map((profile) => ({
    label: (selected && profile.id === selected.id ? '$(check) ' : '$(blank) ')
      + (profile.name.trim() || '(unnamed)'),
    description: profile.selectedModel,
    profileId: profile.id,
  }));
  items.push({ label: '', kind: vscode.QuickPickItemKind.Separator });
  items.push({ label: '$(gear) Model Settings…', openSettings: true });

  const picked = await vscode.window.showQuickPick(items, {
    placeHolder: 'Switch AI provider profile',
  });
  if (!picked) {
    return;
  }
  if (picked.openSettings) {
    await SettingsPanel.showAndWait(context, apiKeyStore);
    return;
  }
  const profile = settings.findProfile(picked.profileId);
  if (!profile) {
    return;
  }
  await pickModelFor(context, apiKeyStore, profile);
}

async function pickModelFor(
  context: vscode.ExtensionContext,
  apiKeyStore: ApiKeyStore,
  profile: ProviderProfile,
): Promise<void> {
  const settings = getSettings();

  // 免费配置模型为空时,等一次网关刷新再展示
  if (isManagedFree(profile) && profile.models.length === 0) {
    await vscode.window.withProgress(
      { location: vscode.ProgressLocation.Notification, title: 'Refreshing free models…' },
      () => refreshManagedFreeModels(profile),
    );
  }
  const fresh = settings.findProfile(profile.id) ?? profile;

  if (fresh.models.length === 0) {
    const hint = isManagedFree(fresh)
      ? 'No free models available.' : 'No models; fetch them in settings.';
    const action = await vscode.window.showInformationMessage(
      'AI Commit Message: ' + hint, 'Open Settings');
    if (action === 'Open Settings') {
      await SettingsPanel.showAndWait(context, apiKeyStore);
    }
    return;
  }

  const selected = settings.getSelectedProfile();
  const isActiveProfile = selected?.id === fresh.id;
  const items: ModelItem[] = fresh.models.map((model) => ({
    label: (isActiveProfile && model === fresh.selectedModel ? '$(check) ' : '$(blank) ') + model,
    model,
  }));
  const picked = await vscode.window.showQuickPick(items, {
    placeHolder: 'Select model for ' + (fresh.name.trim() || '(unnamed)'),
  });
  if (!picked) {
    return;
  }
  const state = settings.getState();
  state.selectedProfileId = fresh.id;
  fresh.selectedModel = picked.model;
  await settings.save();
  vscode.window.setStatusBarMessage(
    'AI Commit Message: ' + fresh.name + ' · ' + picked.model, 3000);
}
