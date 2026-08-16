<div align="center">

<img src="./assets/logo.png" width="100" height="100" alt="AI Commit Message" />

# AI Commit Message

在 JetBrains IDE 的提交窗口中，使用 AI 根据已选择变更的 diff 生成清晰、简洁的 Conventional Commit 信息。

Generate clear and concise Conventional Commit messages from selected changes directly in the JetBrains IDE commit window.

[![License](https://img.shields.io/github/license/Liubsyy/ai-commit-message?color=blue)](./LICENSE)
[![release](https://img.shields.io/jetbrains/plugin/v/33564?label=version)](https://plugins.jetbrains.com/plugin/33564-ai-commit-message)
![sdk](https://img.shields.io/badge/plugin%20sdk-IDEA%202020.3-red.svg)

</div>

<table>
  <tr>
    <td width="50%" align="center">
      <img src="./assets/pic1.png" alt="Generate an AI commit message in the JetBrains commit window">
      <br>
      <sub>生成提交信息 · Generate a commit message</sub>
    </td>
    <td width="50%" align="center">
      <img src="./assets/ai-model-setting.png" alt="Configure the AI provider and model">
      <br>
      <sub>配置 AI 模型 · Configure the AI model</sub>
    </td>
  </tr>
</table>

## 功能 · Features

| 中文 | English |
| --- | --- |
| 在提交信息工具栏中一键生成 commit message | Generate a commit message from the commit toolbar with one click |
| 内置托管免费模型，无需配置 API Key | Use publisher-hosted free models without configuring an API key |
| 支持 OpenAI 兼容接口，可配置 Base URL、API Key 和模型 | Connect to any OpenAI-compatible API with a custom base URL, API key, and model |
| 支持切换供应商与模型、自定义 Prompt 和输出语言 | Switch providers and models, customize the prompt, and select an output language |
| 默认按照 Conventional Commits 格式生成提交信息 | Generate commit messages in Conventional Commits format by default |

## 安装 · Installation

在 JetBrains IDE 中打开 `Settings | Plugins | Marketplace`，搜索 `ai-commit-message`，然后点击 `Install`。

Open `Settings | Plugins | Marketplace` in your JetBrains IDE, search for `ai-commit-message`, and click `Install`.

> 要求 JetBrains Platform 2020.3 或更高版本。
>
> Requires JetBrains Platform 2020.3 or later.

## 目录结构 · Repository Layout

```
.
├── jetbrains-plugin/          # JetBrains IDE 插件（DevKit 模块）· JetBrains IDE plugin (DevKit module)
│   ├── src/                   # Java 源码 · Java sources
│   ├── resources/             
│   └── ai-commit-message.iml
├── vscode-plugin/             # VS Code 扩展（TypeScript）· VS Code extension (TypeScript)
│   ├── src/                   # TypeScript 源码 · TypeScript sources
│   ├── resources/             
│   └── package.json
└── assets/                    
```

后续其他 IDE / 编辑器的插件将以同级目录的形式加入。

Plugins for other IDEs and editors will be added as sibling directories.

## 许可证 · License

[MIT](./LICENSE)
