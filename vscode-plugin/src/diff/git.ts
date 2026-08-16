import * as vscode from 'vscode';

/**
 * VSCode 内置 Git 扩展(vscode.git)公开 API 的最小类型子集,
 * 字段与官方 git.d.ts 保持一致,仅保留本插件用到的部分。
 */

export interface GitExtension {
  readonly enabled: boolean;
  getAPI(version: 1): GitAPI;
}

export interface GitAPI {
  readonly repositories: Repository[];
}

export interface Repository {
  readonly rootUri: vscode.Uri;
  readonly inputBox: { value: string };
  readonly state: RepositoryState;
  /** cached=true 为 staged diff(git diff --cached),false 为工作区 diff */
  diff(cached?: boolean): Promise<string>;
}

export interface RepositoryState {
  readonly indexChanges: Change[];
  readonly workingTreeChanges: Change[];
  readonly untrackedChanges: Change[];
  readonly mergeChanges: Change[];
}

export interface Change {
  readonly uri: vscode.Uri;
  readonly status: Status;
}

/** 与 vscode.git 扩展的 Status 枚举数值一致 */
export enum Status {
  INDEX_MODIFIED = 0,
  INDEX_ADDED = 1,
  INDEX_DELETED = 2,
  INDEX_RENAMED = 3,
  INDEX_COPIED = 4,
  MODIFIED = 5,
  DELETED = 6,
  UNTRACKED = 7,
  IGNORED = 8,
  INTENT_TO_ADD = 9,
  INTENT_TO_RENAME = 10,
  TYPE_CHANGED = 11,
  ADDED_BY_US = 12,
  ADDED_BY_THEM = 13,
  DELETED_BY_US = 14,
  DELETED_BY_THEM = 15,
  BOTH_ADDED = 16,
  BOTH_DELETED = 17,
  BOTH_MODIFIED = 18,
}

export function statusLabel(status: Status): string {
  switch (status) {
    case Status.INDEX_ADDED:
    case Status.UNTRACKED:
    case Status.INTENT_TO_ADD:
      return 'ADDED';
    case Status.INDEX_DELETED:
    case Status.DELETED:
      return 'DELETED';
    case Status.INDEX_RENAMED:
    case Status.INTENT_TO_RENAME:
      return 'RENAMED';
    case Status.INDEX_COPIED:
      return 'COPIED';
    default:
      return 'MODIFIED';
  }
}

export function getGitAPI(): GitAPI | null {
  const extension = vscode.extensions.getExtension<GitExtension>('vscode.git');
  if (!extension) {
    return null;
  }
  const exports = extension.isActive ? extension.exports : null;
  if (!exports || !exports.enabled) {
    return null;
  }
  return exports.getAPI(1);
}

export async function activateGitExtension(): Promise<void> {
  const extension = vscode.extensions.getExtension<GitExtension>('vscode.git');
  if (extension && !extension.isActive) {
    try {
      await extension.activate();
    } catch {
      // Git 扩展被禁用时由调用方给出提示
    }
  }
}
