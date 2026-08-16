package com.liubs.aicommit.settings;

import org.jetbrains.annotations.Nullable;

/** 输出语言的统一映射:存储代码 / 界面标签(原生写法)/ 提示词中的英文名 */
public final class OutputLanguages {

    public static final String[] CODES = {
            "auto", "en", "zh", "zh-TW", "ja", "ko", "fr", "de", "es", "pt", "ru"
    };

    public static final String[] LABELS = {
            "Auto", "English", "中文", "繁體中文", "日本語", "한국어",
            "Français", "Deutsch", "Español", "Português", "Русский"
    };

    private static final String[] ENGLISH_NAMES = {
            null, "English", "Simplified Chinese", "Traditional Chinese", "Japanese", "Korean",
            "French", "German", "Spanish", "Portuguese", "Russian"
    };

    private OutputLanguages() {
    }

    public static int indexOf(@Nullable String code) {
        for (int i = 0; i < CODES.length; i++) {
            if (CODES[i].equals(code)) {
                return i;
            }
        }
        return 0;
    }

    public static String codeAt(int index) {
        return CODES[index < 0 || index >= CODES.length ? 0 : index];
    }

    /** auto 返回 null,表示不追加语言指令 */
    @Nullable
    public static String englishName(@Nullable String code) {
        return ENGLISH_NAMES[indexOf(code)];
    }
}
