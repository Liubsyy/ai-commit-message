import * as fs from 'fs/promises';
import * as path from 'path';
import * as vscode from 'vscode';
import { Change, Repository, Status, statusLabel } from './git';

export interface ResolvedChanges {
  /** true 表示取 staged(等价 JetBrains 勾选的变更);false 表示工作区全部变更 */
  staged: boolean;
  changes: Change[];
}

/**
 * 对应 JetBrains 的「勾选的变更」:优先 staged 区;
 * staged 为空时退回工作区全部变更(含未跟踪文件)。
 */
export function resolveChanges(repo: Repository): ResolvedChanges {
  const staged = repo.state.indexChanges;
  if (staged.length > 0) {
    return { staged: true, changes: [...staged] };
  }
  const untracked = repo.state.untrackedChanges ?? [];
  return { staged: false, changes: [...repo.state.workingTreeChanges, ...untracked] };
}

/** 把变更转成统一 diff 文本,超长按字符预算截断 */
export async function buildDiff(repo: Repository, resolved: ResolvedChanges,
                                charLimit: number): Promise<string> {
  let diff: string;
  try {
    diff = await repo.diff(resolved.staged);
    if (!resolved.staged) {
      diff += await describeUntrackedFiles(repo, resolved.changes, charLimit);
    }
  } catch {
    diff = describeChanges(repo, resolved.changes);
  }
  if (!diff.trim()) {
    diff = describeChanges(repo, resolved.changes);
  }
  if (charLimit > 0 && diff.length > charLimit) {
    diff = diff.slice(0, charLimit)
      + '\n\n[Note: diff truncated to the first ' + charLimit + ' characters]';
  }
  return diff.trim();
}

/**
 * `git diff` 不包含未跟踪文件;为对齐 JetBrains 版(新文件内容会进 patch),
 * 把未跟踪文本文件的内容以伪 diff 形式补进来,单文件超预算时只列文件名。
 */
async function describeUntrackedFiles(repo: Repository, changes: Change[],
                                      charLimit: number): Promise<string> {
  const untracked = changes.filter(
    (c) => c.status === Status.UNTRACKED || c.status === Status.INTENT_TO_ADD);
  if (untracked.length === 0) {
    return '';
  }
  const perFileBudget = charLimit > 0 ? charLimit : 8000;
  let result = '';
  for (const change of untracked) {
    const relative = relativePath(repo, change.uri);
    result += '\ndiff --git a/' + relative + ' b/' + relative + '\nnew file\n';
    const content = await readTextFile(change.uri.fsPath, perFileBudget);
    if (content !== null) {
      result += content.split('\n').map((line) => '+' + line).join('\n') + '\n';
    }
  }
  return result;
}

async function readTextFile(fsPath: string, budget: number): Promise<string | null> {
  try {
    const stat = await fs.stat(fsPath);
    if (!stat.isFile() || stat.size > budget) {
      return null;
    }
    const buffer = await fs.readFile(fsPath);
    if (buffer.includes(0)) {
      return null; // 二进制文件只留文件名
    }
    return buffer.toString('utf8');
  } catch {
    return null;
  }
}

/** diff 生成失败时的兜底:至少给出文件级变更概览 */
function describeChanges(repo: Repository, changes: Change[]): string {
  let sb = 'Changed files (full diff unavailable):\n';
  for (const change of changes) {
    sb += '- ' + statusLabel(change.status) + ' ' + relativePath(repo, change.uri) + '\n';
  }
  return sb;
}

function relativePath(repo: Repository, uri: vscode.Uri): string {
  const root = repo.rootUri.fsPath;
  const rel = path.relative(root, uri.fsPath);
  return rel.split(path.sep).join('/');
}
