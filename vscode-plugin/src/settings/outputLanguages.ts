/** 输出语言的统一映射:存储代码 / 界面标签(原生写法)/ 提示词中的英文名 */

export const CODES = [
  'auto', 'en', 'zh', 'zh-TW', 'ja', 'ko', 'fr', 'de', 'es', 'pt', 'ru',
] as const;

export const LABELS = [
  'Auto', 'English', '中文', '繁體中文', '日本語', '한국어',
  'Français', 'Deutsch', 'Español', 'Português', 'Русский',
];

const ENGLISH_NAMES: (string | null)[] = [
  null, 'English', 'Simplified Chinese', 'Traditional Chinese', 'Japanese', 'Korean',
  'French', 'German', 'Spanish', 'Portuguese', 'Russian',
];

export function indexOf(code: string | undefined | null): number {
  const i = CODES.indexOf((code ?? '') as (typeof CODES)[number]);
  return i < 0 ? 0 : i;
}

export function codeAt(index: number): string {
  return CODES[index < 0 || index >= CODES.length ? 0 : index];
}

/** auto 返回 null,表示不追加语言指令 */
export function englishName(code: string | undefined | null): string | null {
  return ENGLISH_NAMES[indexOf(code)];
}
