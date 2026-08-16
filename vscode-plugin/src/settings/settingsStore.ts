import { randomUUID } from 'crypto';
import * as vscode from 'vscode';
import {
  createManagedFreeProfile,
  normalizeManagedFree,
} from './managedFreeProvider';
import {
  isManagedFree,
  normalizeProfileShape,
  ProviderProfile,
} from './providerProfile';

const STATE_KEY = 'aiCommitMessage.state';

export interface SettingsState {
  profiles: ProviderProfile[];
  selectedProfileId: string;
  /** 随机安装标识,仅用于免费网关限流,不使用硬件或账号信息。 */
  installationId: string;
  managedFreeModelConfigVersion: number;
}

/** 等价 JetBrains 版的 application-level PersistentStateComponent,存 globalState。 */
export class SettingsStore {

  private state: SettingsState;

  constructor(private readonly memento: vscode.Memento) {
    this.state = this.loadState();
  }

  getState(): SettingsState {
    this.ensureManagedFreeProfile();
    return this.state;
  }

  /** 当前生效的配置;selectedProfileId 失效时退回第一个 */
  getSelectedProfile(): ProviderProfile | null {
    const byId = this.findProfile(this.getState().selectedProfileId);
    if (byId) {
      return byId;
    }
    return this.state.profiles.length === 0 ? null : this.state.profiles[0];
  }

  findProfile(id: string | null | undefined): ProviderProfile | null {
    if (!id) {
      return null;
    }
    return this.state.profiles.find((p) => p.id === id) ?? null;
  }

  async save(): Promise<void> {
    this.ensureManagedFreeProfile();
    await this.memento.update(STATE_KEY, this.state);
  }

  getDiffCharLimit(): number {
    const value = vscode.workspace.getConfiguration('aiCommitMessage')
      .get<number>('diffCharLimit');
    return typeof value === 'number' && Number.isFinite(value) ? value : 8000;
  }

  private loadState(): SettingsState {
    const raw = this.memento.get<Partial<SettingsState>>(STATE_KEY);
    if (!raw) {
      return createDefaultState();
    }
    const state: SettingsState = {
      profiles: Array.isArray(raw.profiles) ? raw.profiles.map(normalizeProfileShape) : [],
      selectedProfileId: typeof raw.selectedProfileId === 'string' ? raw.selectedProfileId : '',
      installationId: typeof raw.installationId === 'string' && raw.installationId.trim()
        ? raw.installationId : randomUUID(),
      managedFreeModelConfigVersion:
        typeof raw.managedFreeModelConfigVersion === 'number'
          ? raw.managedFreeModelConfigVersion : 0,
    };
    this.state = state;
    this.ensureManagedFreeProfile();
    if (state.managedFreeModelConfigVersion < 1) {
      const managed = state.profiles.find(isManagedFree);
      if (managed) {
        managed.models = [];
        managed.selectedModel = '';
      }
      state.managedFreeModelConfigVersion = 1;
    }
    return state;
  }

  private ensureManagedFreeProfile(): void {
    if (!Array.isArray(this.state.profiles)) {
      this.state.profiles = [];
    }
    let managed = this.state.profiles.find(isManagedFree) ?? null;
    if (!managed) {
      managed = createManagedFreeProfile();
      this.state.profiles.unshift(managed);
    } else {
      const wasSelected = managed.id === this.state.selectedProfileId;
      normalizeManagedFree(managed);
      if (wasSelected) {
        this.state.selectedProfileId = managed.id;
      }
    }
    if (!this.findProfile(this.state.selectedProfileId)) {
      this.state.selectedProfileId = managed.id;
    }
  }
}

function createDefaultState(): SettingsState {
  const managed = createManagedFreeProfile();
  return {
    profiles: [managed],
    selectedProfileId: managed.id,
    installationId: randomUUID(),
    managedFreeModelConfigVersion: 1,
  };
}

let instance: SettingsStore | undefined;

export function initSettings(memento: vscode.Memento): SettingsStore {
  instance = new SettingsStore(memento);
  return instance;
}

export function getSettings(): SettingsStore {
  if (!instance) {
    throw new Error('SettingsStore is not initialized');
  }
  return instance;
}
