import * as vscode from 'vscode';

/** API Key 按 profile id 存取 SecretStorage,不落明文配置。 */
export class ApiKeyStore {

  constructor(private readonly secrets: vscode.SecretStorage) {
  }

  private key(profileId: string): string {
    return 'aiCommitMessage.apiKey.' + profileId;
  }

  async get(profileId: string): Promise<string> {
    const value = await this.secrets.get(this.key(profileId));
    return value ?? '';
  }

  async set(profileId: string, apiKey: string | null | undefined): Promise<void> {
    const trimmed = (apiKey ?? '').trim();
    if (!trimmed) {
      await this.secrets.delete(this.key(profileId));
    } else {
      await this.secrets.store(this.key(profileId), trimmed);
    }
  }
}
