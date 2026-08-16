import * as vscode from 'vscode';
import * as aiClients from '../ai/aiClients';
import { isCanceled } from '../ai/httpUtil';
import * as promptTemplates from '../ai/promptTemplates';
import { ApiKeyStore } from '../settings/apiKeyStore';
import * as outputLanguages from '../settings/outputLanguages';
import {
  isManagedFree,
  normalizeProfileShape,
  ProviderProfile,
} from '../settings/providerProfile';
import { getSettings } from '../settings/settingsStore';
import { renderSettingsHtml } from './settingsPanelHtml';

interface ProfileEntry {
  profile: ProviderProfile;
  apiKey: string;
}

/**
 * 模型设置面板:左侧配置列表(增/删/复制),右侧表单 + 拉取模型 + 测试连接。
 * 对应 JetBrains 版 SettingsDialog,交互与字段 1:1。
 */
export class SettingsPanel {

  private static current: SettingsPanel | undefined;
  private static waiters: (() => void)[] = [];

  /** 打开(或前置)设置面板,面板关闭后 resolve */
  static showAndWait(
    context: vscode.ExtensionContext,
    apiKeyStore: ApiKeyStore,
  ): Promise<void> {
    if (SettingsPanel.current) {
      SettingsPanel.current.panel.reveal();
    } else {
      SettingsPanel.current = new SettingsPanel(context, apiKeyStore);
    }
    return new Promise((resolve) => SettingsPanel.waiters.push(resolve));
  }

  private readonly panel: vscode.WebviewPanel;
  private readonly originalProfileIds = new Set<string>();
  private disposed = false;

  private constructor(
    context: vscode.ExtensionContext,
    private readonly apiKeyStore: ApiKeyStore,
  ) {
    this.panel = vscode.window.createWebviewPanel(
      'aiCommitMessage.settings',
      'AI Commit Model Settings',
      vscode.ViewColumn.Active,
      { enableScripts: true, retainContextWhenHidden: true },
    );
    this.panel.iconPath = vscode.Uri.joinPath(context.extensionUri, 'resources', 'icon.png');
    this.panel.webview.html = renderSettingsHtml(this.panel.webview);
    this.panel.webview.onDidReceiveMessage((msg) => this.onMessage(msg));
    this.panel.onDidDispose(() => {
      this.disposed = true;
      SettingsPanel.current = undefined;
      const waiters = SettingsPanel.waiters;
      SettingsPanel.waiters = [];
      for (const resolve of waiters) {
        resolve();
      }
    });
  }

  private async onMessage(msg: Record<string, unknown>): Promise<void> {
    switch (msg['type']) {
      case 'ready':
        await this.sendInit();
        return;
      case 'fetchModels':
        await this.fetchModels(msg);
        return;
      case 'testConnection':
        await this.testConnection(msg);
        return;
      case 'save':
        await this.save(msg);
        return;
      case 'cancel':
        this.panel.dispose();
        return;
    }
  }

  private async sendInit(): Promise<void> {
    const settings = getSettings();
    const state = settings.getState();
    const entries: ProfileEntry[] = [];
    for (const profile of state.profiles) {
      entries.push({
        profile: { ...profile, models: [...profile.models] },
        apiKey: await this.apiKeyStore.get(profile.id),
      });
      this.originalProfileIds.add(profile.id);
    }
    void this.post({
      type: 'init',
      entries,
      selectedProfileId: settings.getSelectedProfile()?.id ?? '',
      defaultPrompt: promptTemplates.getDefaultPrompt(),
      languageLabels: outputLanguages.LABELS,
      languageCodes: [...outputLanguages.CODES],
    });
  }

  private async fetchModels(msg: Record<string, unknown>): Promise<void> {
    const profile = normalizeProfileShape(msg['profile'] as Partial<ProviderProfile>);
    const apiKey = typeof msg['apiKey'] === 'string' ? msg['apiKey'] : '';
    try {
      const models = await vscode.window.withProgress(
        {
          location: vscode.ProgressLocation.Notification,
          title: 'Fetching models from provider…',
          cancellable: true,
        },
        (_p, token) => aiClients.create(profile).listModels(profile.baseUrl, apiKey, token),
      );
      void this.post({ type: 'fetchModelsResult', ok: true, models });
    } catch (e) {
      if (isCanceled(e)) {
        void this.post({ type: 'fetchModelsResult', ok: false, canceled: true });
        return;
      }
      void this.post({
        type: 'fetchModelsResult',
        ok: false,
        error: 'Fetch failed: ' + (e instanceof Error ? e.message : String(e)),
      });
    }
  }

  private async testConnection(msg: Record<string, unknown>): Promise<void> {
    const profile = normalizeProfileShape(msg['profile'] as Partial<ProviderProfile>);
    const apiKey = typeof msg['apiKey'] === 'string' ? msg['apiKey'] : '';
    try {
      const result = await vscode.window.withProgress(
        {
          location: vscode.ProgressLocation.Notification,
          title: 'Testing connection…',
          cancellable: true,
        },
        async (_p, token) => {
          const client = aiClients.create(profile);
          if (!profile.selectedModel) {
            const models = await client.listModels(profile.baseUrl, apiKey, token);
            return 'Connection OK. Provider returned ' + models.length + ' models.';
          }
          const reply = await client.ping(profile, apiKey, token);
          return 'Connection OK. Model replied: ' + reply;
        },
      );
      void this.post({ type: 'testConnectionResult', ok: true, message: result });
    } catch (e) {
      if (isCanceled(e)) {
        void this.post({ type: 'testConnectionResult', ok: false, canceled: true });
        return;
      }
      void this.post({
        type: 'testConnectionResult',
        ok: false,
        message: 'Connection failed: ' + (e instanceof Error ? e.message : String(e)),
      });
    }
  }

  private async save(msg: Record<string, unknown>): Promise<void> {
    const rawEntries = Array.isArray(msg['entries']) ? msg['entries'] : [];
    const entries: ProfileEntry[] = rawEntries.map((raw) => {
      const record = raw as Record<string, unknown>;
      return {
        profile: normalizeProfileShape(record['profile'] as Partial<ProviderProfile>),
        apiKey: typeof record['apiKey'] === 'string' ? record['apiKey'] : '',
      };
    });

    for (let i = 0; i < entries.length; i++) {
      const p = entries[i].profile;
      if (!isManagedFree(p) && (!p.name.trim() || !p.baseUrl.trim())) {
        void this.post({
          type: 'validationError',
          index: i,
          message: 'Name and Base URL must not be empty',
        });
        return;
      }
    }

    const settings = getSettings();
    const state = settings.getState();
    const keptIds = new Set<string>();
    const profiles: ProviderProfile[] = [];
    for (const entry of entries) {
      profiles.push(entry.profile);
      keptIds.add(entry.profile.id);
      await this.apiKeyStore.set(entry.profile.id, entry.apiKey);
    }
    for (const removedId of this.originalProfileIds) {
      if (!keptIds.has(removedId)) {
        await this.apiKeyStore.set(removedId, null);
      }
    }
    state.profiles = profiles;
    if (!settings.findProfile(state.selectedProfileId)) {
      state.selectedProfileId = profiles.length === 0 ? '' : profiles[0].id;
    }
    await settings.save();
    if (!this.disposed) {
      this.panel.dispose();
    }
  }

  private post(message: unknown): Thenable<boolean> {
    return this.panel.webview.postMessage(message);
  }
}
