import * as vscode from 'vscode';
import * as promptTemplates from './ai/promptTemplates';
import { generateCommitMessage } from './commands/generateCommitMessage';
import { selectModel } from './commands/selectModel';
import { ApiKeyStore } from './settings/apiKeyStore';
import * as managedFreeProvider from './settings/managedFreeProvider';
import { initSettings } from './settings/settingsStore';
import { SettingsPanel } from './ui/settingsPanel';

export function activate(context: vscode.ExtensionContext): void {
  promptTemplates.init(context.extensionPath);
  managedFreeProvider.init(context.extensionPath);
  initSettings(context.globalState);
  const apiKeyStore = new ApiKeyStore(context.secrets);

  context.subscriptions.push(
    vscode.commands.registerCommand('aiCommitMessage.generate',
      () => generateCommitMessage(context, apiKeyStore)),
    vscode.commands.registerCommand('aiCommitMessage.selectModel',
      () => selectModel(context, apiKeyStore)),
    vscode.commands.registerCommand('aiCommitMessage.openSettings',
      () => SettingsPanel.showAndWait(context, apiKeyStore)),
  );
}

export function deactivate(): void {
}
