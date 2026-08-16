import * as vscode from 'vscode';
import { ProviderProfile } from '../settings/providerProfile';

export interface AiClient {
  generateCommitMessage(
    profile: ProviderProfile,
    apiKey: string,
    diff: string,
    token?: vscode.CancellationToken,
  ): Promise<string>;

  /** 测试连接用:发一个最小请求,返回模型的简短回复 */
  ping(
    profile: ProviderProfile,
    apiKey: string,
    token?: vscode.CancellationToken,
  ): Promise<string>;

  listModels(
    baseUrl: string,
    apiKey: string,
    token?: vscode.CancellationToken,
  ): Promise<string[]>;
}
