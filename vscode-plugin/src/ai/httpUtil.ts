import * as vscode from 'vscode';

/** 用户取消(手动或超时中止不算):调用方捕获后静默返回 */
export class CanceledError extends Error {
  constructor() {
    super('Canceled');
    this.name = 'CanceledError';
  }
}

export function isCanceled(e: unknown): boolean {
  return e instanceof CanceledError;
}

export interface HttpResponse {
  code: number;
  text: string;
}

/**
 * fetch 封装:统一超时(AbortController)与取消联动。
 * 返回状态码 + 文本,错误语义由各客户端自行解析。
 */
export async function httpExchange(
  method: string,
  url: string,
  headers: Record<string, string>,
  body: string | null,
  timeoutMs: number,
  token?: vscode.CancellationToken,
): Promise<HttpResponse> {
  if (token?.isCancellationRequested) {
    throw new CanceledError();
  }
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), timeoutMs);
  const cancelSub = token?.onCancellationRequested(() => controller.abort());
  try {
    let response: Response;
    try {
      response = await fetch(url, {
        method,
        headers,
        body: body ?? undefined,
        signal: controller.signal,
      });
    } catch (e) {
      if (token?.isCancellationRequested) {
        throw new CanceledError();
      }
      if (controller.signal.aborted) {
        throw new Error('The request timed out (' + Math.round(timeoutMs / 1000) + 's): ' + url);
      }
      throw new Error('Cannot reach ' + url + ': ' + errorMessage(e));
    }
    const text = await response.text();
    if (token?.isCancellationRequested) {
      throw new CanceledError();
    }
    return { code: response.status, text };
  } finally {
    clearTimeout(timer);
    cancelSub?.dispose();
  }
}

export function errorMessage(e: unknown): string {
  if (e instanceof Error) {
    return e.cause instanceof Error && e.message === 'fetch failed'
      ? e.cause.message
      : e.message;
  }
  return String(e);
}

export function abbreviate(text: string | null | undefined): string {
  const t = (text ?? '').trim();
  return t.length <= 300 ? t : t.slice(0, 300) + '…';
}

export function joinUrl(baseUrl: string | null | undefined, path: string): string {
  let base = (baseUrl ?? '').trim();
  while (base.endsWith('/')) {
    base = base.slice(0, -1);
  }
  return base + '/' + path;
}

export function unexpectedResponseDetail(response: string | null | undefined): string {
  const value = (response ?? '').trim();
  if (!value) {
    return '';
  }
  const lower = value.toLowerCase();
  if (lower.startsWith('<!doctype html') || lower.startsWith('<html')) {
    return 'The server returned a web page instead of API data.';
  }
  return 'Response: ' + abbreviate(value.replace(/\s+/g, ' '));
}

export function parseObject(response: string, emptyMessage: string, unexpectedMessage: string): Record<string, unknown> {
  let parsed: unknown;
  try {
    parsed = JSON.parse(response);
  } catch {
    const detail = unexpectedResponseDetail(response);
    throw new Error(detail ? unexpectedMessage + ' ' + detail : emptyMessage);
  }
  if (typeof parsed !== 'object' || parsed === null || Array.isArray(parsed)) {
    throw new Error(unexpectedMessage + ' ' + unexpectedResponseDetail(response));
  }
  return parsed as Record<string, unknown>;
}

/** 从 {data:[{id}]} 提取去重排序后的模型 id 列表 */
export function extractModelIds(root: Record<string, unknown>): string[] {
  const data = root['data'];
  const result: string[] = [];
  if (Array.isArray(data)) {
    for (const element of data) {
      if (typeof element !== 'object' || element === null) {
        continue;
      }
      const id = (element as Record<string, unknown>)['id'];
      if (typeof id === 'string' && !result.includes(id)) {
        result.push(id);
      }
    }
  }
  result.sort();
  return result;
}
