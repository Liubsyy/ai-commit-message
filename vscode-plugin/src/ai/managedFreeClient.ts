import * as vscode from 'vscode';
import { getSettings } from '../settings/settingsStore';
import { ProviderProfile } from '../settings/providerProfile';
import { AiClient } from './aiClient';
import {
  abbreviate,
  extractModelIds,
  httpExchange,
  joinUrl,
  parseObject,
  unexpectedResponseDetail,
} from './httpUtil';

const READ_TIMEOUT_MS = 30_000;

/** 托管免费网关客户端:POST /commit-message、GET /models、GET /health。 */
export class ManagedFreeClient implements AiClient {

  async generateCommitMessage(profile: ProviderProfile, _apiKey: string, diff: string,
                              token?: vscode.CancellationToken): Promise<string> {
    ensureConfigured(profile.baseUrl);
    const body = JSON.stringify({
      model: profile.selectedModel,
      diff,
      language: profile.outputLanguage,
      prompt: profile.prompt ?? '',
    });
    const response = await exchange('POST', joinUrl(profile.baseUrl, 'commit-message'),
      body, true, token);
    const root = parseGatewayObject(response);
    const message = root['message'];
    if (typeof message !== 'string') {
      throw new Error('Free gateway returned no commit message');
    }
    return message.trim();
  }

  async ping(profile: ProviderProfile, _apiKey: string,
             token?: vscode.CancellationToken): Promise<string> {
    ensureConfigured(profile.baseUrl);
    const response = await exchange('GET', joinUrl(profile.baseUrl, 'health'),
      null, false, token);
    const root = parseGatewayObject(response);
    if (root['status'] !== 'ok') {
      throw new Error('Free gateway health check failed');
    }
    return 'OK';
  }

  async listModels(baseUrl: string, _apiKey: string,
                   token?: vscode.CancellationToken): Promise<string[]> {
    ensureConfigured(baseUrl);
    const response = await exchange('GET', joinUrl(baseUrl, 'models'), null, true, token);
    const root = parseGatewayObject(response);
    const models = extractModelIds(root);
    if (models.length === 0) {
      throw new Error('Free gateway returned no models');
    }
    return models;
  }
}

async function exchange(method: string, url: string, body: string | null,
                        includeInstallationId: boolean,
                        token?: vscode.CancellationToken): Promise<string> {
  const headers: Record<string, string> = {
    'Content-Type': 'application/json',
    'Accept': 'application/json',
    'X-AI-Commit-Client': 'vscode-plugin',
  };
  if (includeInstallationId) {
    headers['X-AI-Commit-Installation'] = getSettings().getState().installationId;
  }
  const { code, text } = await httpExchange(method, url, headers, body, READ_TIMEOUT_MS, token);
  const gatewayError = extractGatewayError(text);
  if (gatewayError !== null) {
    throw new Error('Free model service: ' + abbreviate(gatewayError) + ' (HTTP ' + code + ')');
  }
  if (code < 200 || code >= 300) {
    throw new Error(errorMessage(code, text));
  }
  return text;
}

function errorMessage(code: number, response: string): string {
  const detail = gatewayDetail(response);
  return detail
    ? 'The free model service request failed (HTTP ' + code + '). ' + detail
    : 'The free model service request failed (HTTP ' + code + '). Please try again later.';
}

function extractGatewayError(response: string): string | null {
  let root: unknown;
  try {
    root = JSON.parse(response);
  } catch {
    return null;
  }
  if (typeof root !== 'object' || root === null || Array.isArray(root)) {
    return null;
  }
  const error = (root as Record<string, unknown>)['error'];
  if (error === undefined || error === null) {
    return null;
  }
  if (typeof error === 'string') {
    return error;
  }
  if (typeof error === 'object' && !Array.isArray(error)) {
    const message = (error as Record<string, unknown>)['message'];
    if (typeof message === 'string') {
      return message;
    }
  }
  return null;
}

function parseGatewayObject(response: string): Record<string, unknown> {
  return parseObject(response,
    'The free model service returned an empty response. Please try again later.',
    'The free model service returned an unexpected response.');
}

function gatewayDetail(response: string): string {
  const detail = unexpectedResponseDetail(response);
  if (!detail) {
    return '';
  }
  if (detail.startsWith('The server returned a web page')) {
    return 'The gateway returned a web page instead of API data. Please try again later.';
  }
  return 'Please try again later. ' + detail;
}

function ensureConfigured(baseUrl: string | null | undefined): void {
  if (!baseUrl || !baseUrl.trim()) {
    throw new Error('Free gateway URL has not been configured by the plugin publisher');
  }
}
