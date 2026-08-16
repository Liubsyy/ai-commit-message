import * as fs from 'fs';
import * as path from 'path';

/** 打包资源读取失败时的兜底模板,保证按钮始终可用 */
const FALLBACK_PROMPT = [
  '## Commit Format',
  '',
  '```text',
  '<type>(<scope>): <subject>',
  '```',
  '',
  'Use an English Conventional Commit type such as feat, fix, docs, '
    + 'style, refactor, perf, test, build, ci, or chore.',
  'Choose type and scope only from the supplied diff.',
  'Write one concise subject sentence in the requested language.',
  'Do not end the subject with a period and do not invent changes.',
  '',
  'Examples:',
  '',
  '```text',
  'feat(settings): support custom provider profiles',
  'fix(editor): preserve the generated commit message',
  '```',
  '',
].join('\n');

let extensionPath = '';
let defaultPrompt: string | undefined;

export function init(extensionRoot: string): void {
  extensionPath = extensionRoot;
  defaultPrompt = undefined;
}

export function getDefaultPrompt(): string {
  if (defaultPrompt === undefined) {
    defaultPrompt = load();
  }
  return defaultPrompt;
}

function load(): string {
  try {
    const file = path.join(extensionPath, 'resources', 'prompts', 'default-commit-prompt.md');
    return fs.readFileSync(file, 'utf8');
  } catch {
    return FALLBACK_PROMPT;
  }
}
