import * as path from 'path';
import * as vscode from 'vscode';
import { activateGitExtension, getGitAPI, Repository } from '../diff/git';

/** 取目标仓库:单仓库直接用;多仓库优先当前活动文件所属,否则 QuickPick 选择 */
export async function pickRepository(): Promise<Repository | null> {
  await activateGitExtension();
  const git = getGitAPI();
  if (!git) {
    void vscode.window.showErrorMessage(
      'AI Commit Message: the built-in Git extension is not available.');
    return null;
  }
  const repos = git.repositories;
  if (repos.length === 0) {
    void vscode.window.showWarningMessage(
      'AI Commit Message: no Git repository found in this workspace.');
    return null;
  }
  if (repos.length === 1) {
    return repos[0];
  }
  const activeUri = vscode.window.activeTextEditor?.document.uri;
  if (activeUri) {
    const owner = repos.find((r) =>
      activeUri.fsPath.startsWith(r.rootUri.fsPath + path.sep)
      || activeUri.fsPath === r.rootUri.fsPath);
    if (owner) {
      return owner;
    }
  }
  const picked = await vscode.window.showQuickPick(
    repos.map((repo) => ({
      label: path.basename(repo.rootUri.fsPath),
      description: repo.rootUri.fsPath,
      repo,
    })),
    { placeHolder: 'Select the repository to generate a commit message for' },
  );
  return picked?.repo ?? null;
}
