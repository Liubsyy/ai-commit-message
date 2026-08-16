<div align="center">

<img src="resources/icon.png" width="100" height="100" alt="AI Commit Message" />

# AI Commit Message

在 VS Code 源代码管理面板中，使用 AI 根据变更 diff 生成清晰、简洁的 Conventional Commit 信息。

Generate clear and concise Conventional Commit messages from your changes directly in the VS Code Source Control panel.

</div>

## 功能 · Features

| 中文 | English |
| --- | --- |
| 在源代码管理标题栏一键生成 commit message | Generate a commit message from the Source Control title bar with one click |
| 内置托管免费模型，无需配置 API Key | Use publisher-hosted free models without configuring an API key |
| 支持 OpenAI 兼容接口，可配置 Base URL、API Key 和模型 | Connect to any OpenAI-compatible API with a custom base URL, API key, and model |
| 支持切换供应商与模型、自定义 Prompt 和输出语言 | Switch providers and models, customize the prompt, and select an output language |
| 默认按照 Conventional Commits 格式生成提交信息 | Generate commit messages in Conventional Commits format by default |

## 使用 · Usage

1. 打开源代码管理(Source Control)视图，点击标题栏的 ✦(**AI Generate Commit Message**)按钮。
   Open the Source Control view and click the ✦ (**AI Generate Commit Message**) button in the title bar.
2. 已暂存(staged)的变更优先；没有 staged 时使用全部工作区变更。
   Staged changes are preferred; if nothing is staged, all working tree changes are used.
3. 点击 ⌄(**Switch AI Model**)切换供应商配置与模型，或打开 **AI Commit Model Settings…** 管理配置。
   Click ⌄ (**Switch AI Model**) to switch profiles and models, or open **AI Commit Model Settings…** to manage profiles.

## 设置 · Settings

- `aiCommitMessage.diffCharLimit`:发送给模型的 diff 字符上限(默认 8000,0 为不限)。
  Maximum diff characters sent to the model (default 8000; 0 = unlimited).
- API Key 保存在 VS Code SecretStorage(系统钥匙串),不落明文。
  API keys are stored in VS Code SecretStorage (system keychain), never in plain text.

> 要求 VS Code 1.82 或更高版本。 Requires VS Code 1.82 or later.

## 开发 · Development

```bash
npm install
npm run compile   # typecheck + bundle to dist/
```

用 VS Code 打开 `vscode-plugin/` 目录后按 F5 调试 Extension Development Host;`npm run package` 产出 .vsix。

Open the `vscode-plugin/` folder in VS Code and press F5 to debug in the Extension Development Host; `npm run package` produces a .vsix.

## 许可证 · License

[MIT](../LICENSE)
