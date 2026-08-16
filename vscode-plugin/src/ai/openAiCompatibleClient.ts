import * as vscode from 'vscode';
import * as outputLanguages from '../settings/outputLanguages';
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
import * as promptTemplates from './promptTemplates';

const READ_TIMEOUT_MS = 120_000;
const OUTPUT_RULE =
  '\n\nOutput only the final commit message itself (single line, optionally with a body).'
  + ' Do not output any explanation, quotes, or Markdown code fences.';

/**
 * OpenAI 兼容客户端:POST {baseUrl}/chat/completions、GET {baseUrl}/models。
 * 通过配置不同 baseUrl/model/key 支持 OpenAI、DeepSeek、Moonshot、Ollama 等。
 */
export class OpenAiCompatibleClient implements AiClient {

  async generateCommitMessage(profile: ProviderProfile, apiKey: string, diff: string,
                              token?: vscode.CancellationToken): Promise<string> {
    const body = buildChatBody(profile, 'Here is the diff of the changes to commit:\n\n' + diff, null);
    const response = await exchange('POST', joinUrl(profile.baseUrl, 'chat/completions'),
      apiKey, JSON.stringify(body), token);
    return cleanup(extractContent(response));
  }

  async ping(profile: ProviderProfile, apiKey: string,
             token?: vscode.CancellationToken): Promise<string> {
    const body = buildChatBody(profile, 'Reply with: OK', 8);
    const response = await exchange('POST', joinUrl(profile.baseUrl, 'chat/completions'),
      apiKey, JSON.stringify(body), token);
    return abbreviate(extractContent(response).trim());
  }

  async listModels(baseUrl: string, apiKey: string,
                   token?: vscode.CancellationToken): Promise<string[]> {
    const response = await exchange('GET', joinUrl(baseUrl, 'models'), apiKey, null, token);
    const root = parseProviderObject(response);
    const result = extractModelIds(root);
    if (result.length === 0) {
      throw new Error('Provider returned no models: ' + abbreviate(response));
    }
    return result;
  }
}

function buildChatBody(profile: ProviderProfile, userContent: string,
                       maxTokens: number | null): Record<string, unknown> {
  const body: Record<string, unknown> = {
    model: profile.selectedModel,
    temperature: profile.temperature,
    stream: false,
  };
  if (maxTokens !== null) {
    body['max_tokens'] = maxTokens;
  }
  body['messages'] = [
    { role: 'system', content: systemPrompt(profile) },
    { role: 'user', content: userContent },
  ];
  return body;
}

function systemPrompt(profile: ProviderProfile): string {
  let template = (profile.prompt ?? '').trim();
  if (!template) {
    template = promptTemplates.getDefaultPrompt();
  }
  const languageName = outputLanguages.englishName(profile.outputLanguage);
  const languageRule = languageName === null
    ? '' : '\n\nWrite the commit message in ' + languageName + '.';
  return template + languageRule + OUTPUT_RULE;
}

function extractContent(response: string): string {
  const root = parseProviderObject(response);
  const choices = root['choices'];
  if (!Array.isArray(choices) || choices.length === 0) {
    throw new Error('No result returned: ' + abbreviate(response));
  }
  const message = (choices[0] as Record<string, unknown>)?.['message'] as
    Record<string, unknown> | undefined;
  const content = message?.['content'];
  if (typeof content !== 'string') {
    throw new Error('Unexpected response format: ' + abbreviate(response));
  }
  return content;
}

/** 去掉模型可能包上的代码块围栏和引号 */
function cleanup(text: string): string {
  let t = text.trim();
  if (t.startsWith('```')) {
    const firstNewline = t.indexOf('\n');
    if (firstNewline >= 0) {
      t = t.slice(firstNewline + 1);
    }
    if (t.endsWith('```')) {
      t = t.slice(0, -3);
    }
    t = t.trim();
  }
  if (t.length >= 2 && t.startsWith('"') && t.endsWith('"')) {
    t = t.slice(1, -1).trim();
  }
  return t;
}

async function exchange(method: string, url: string, apiKey: string,
                        body: string | null,
                        token?: vscode.CancellationToken): Promise<string> {
  const headers: Record<string, string> = {
    'Content-Type': 'application/json',
    'Accept': 'application/json',
  };
  const key = (apiKey ?? '').trim();
  if (key) {
    headers['Authorization'] = 'Bearer ' + key;
  }
  const { code, text } = await httpExchange(method, url, headers, body, READ_TIMEOUT_MS, token);
  const providerError = extractProviderError(text);
  if (providerError !== null) {
    throw new Error('AI provider: ' + abbreviate(providerError) + ' (HTTP ' + code + ')');
  }
  if (code < 200 || code >= 300) {
    const detail = unexpectedResponseDetail(text);
    throw new Error(detail
      ? 'The AI provider request failed (HTTP ' + code + '). ' + detail
      : 'The AI provider request failed (HTTP ' + code + ') with an empty response.');
  }
  return text;
}

function parseProviderObject(response: string): Record<string, unknown> {
  return parseObject(response,
    'The AI provider returned an empty response.',
    'The AI provider returned an unexpected response.');
}

function extractProviderError(response: string): string | null {
  let root: unknown;
  try {
    root = JSON.parse(response);
  } catch {
    return null;
  }
  if (typeof root !== 'object' || root === null || Array.isArray(root)) {
    return null;
  }
  const obj = root as Record<string, unknown>;
  const message = errorText(obj['error']);
  if (message !== null) {
    return message;
  }
  if ('choices' in obj || 'data' in obj) {
    return null;
  }
  const fallback = firstText(obj, 'message', 'msg', 'detail', 'error_description');
  return fallback === null || !fallback.trim() ? null : fallback;
}

function errorText(error: unknown): string | null {
  if (error === undefined || error === null) {
    return null;
  }
  if (typeof error === 'string') {
    return error;
  }
  if (typeof error === 'number' || typeof error === 'boolean') {
    return String(error);
  }
  if (Array.isArray(error)) {
    return error.length > 0 ? errorText(error[0]) : null;
  }
  if (typeof error === 'object') {
    return firstText(error as Record<string, unknown>,
      'message', 'msg', 'detail', 'error_description');
  }
  return null;
}

function firstText(object: Record<string, unknown>, ...names: string[]): string | null {
  for (const name of names) {
    const value = object[name];
    if (typeof value === 'string') {
      return value;
    }
    if (typeof value === 'number' || typeof value === 'boolean') {
      return String(value);
    }
  }
  return null;
}
