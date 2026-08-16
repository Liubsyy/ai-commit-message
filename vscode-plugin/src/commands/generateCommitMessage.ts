import * as vscode from 'vscode';
import * as aiClients from '../ai/aiClients';
import { isCanceled } from '../ai/httpUtil';
import { buildDiff, resolveChanges } from '../diff/changesDiffBuilder';
import { ApiKeyStore } from '../settings/apiKeyStore';
import { getSettings } from '../settings/settingsStore';
import { SettingsPanel } from '../ui/settingsPanel';
import { pickRepository } from './repositoryPicker';

let busy = false;

/** 按钮主体:读取变更 → 生成 diff → 调用 AI → 回填提交信息 */
export async function generateCommitMessage(
  context: vscode.ExtensionContext,
  apiKeyStore: ApiKeyStore,
): Promise<void> {
  if (busy) {
    return;
  }
  busy = true;
  try {
    await doGenerate(context, apiKeyStore);
  } finally {
    busy = false;
  }
}

async function doGenerate(
  context: vscode.ExtensionContext,
  apiKeyStore: ApiKeyStore,
): Promise<void> {
  const repo = await pickRepository();
  if (!repo) {
    return;
  }

  const settings = getSettings();
  let profile = settings.getSelectedProfile();
  if (!profile || !profile.selectedModel.trim()) {
    await SettingsPanel.showAndWait(context, apiKeyStore);
    profile = settings.getSelectedProfile();
    if (!profile || !profile.selectedModel.trim()) {
      void vscode.window.showWarningMessage(
        'AI Commit Message: no available model configured. Generation canceled.');
      return;
    }
  }

  const resolved = resolveChanges(repo);
  if (resolved.changes.length === 0) {
    void vscode.window.showWarningMessage(
      'AI Commit Message: no changes found. Stage or modify files first.');
    return;
  }

  const finalProfile = profile;
  const apiKey = await apiKeyStore.get(profile.id);
  const charLimit = settings.getDiffCharLimit();

  try {
    const message = await vscode.window.withProgress(
      {
        location: vscode.ProgressLocation.Notification,
        title: 'Generating commit message with '
          + finalProfile.name + ' · ' + finalProfile.selectedModel + '…',
        cancellable: true,
      },
      async (_progress, token) => {
        const diff = await buildDiff(repo, resolved, charLimit);
        return aiClients.create(finalProfile)
          .generateCommitMessage(finalProfile, apiKey, diff, token);
      },
    );
    if (!message.trim()) {
      void vscode.window.showWarningMessage(
        'AI Commit Message: the model returned empty content.');
      return;
    }
    repo.inputBox.value = message;
  } catch (e) {
    if (isCanceled(e)) {
      return;
    }
    void vscode.window.showErrorMessage(
      'AI Commit Message: generation failed: ' + (e instanceof Error ? e.message : String(e)));
  }
}
