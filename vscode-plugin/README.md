# AI Commit Message · VS Code Plugin

功能介绍与安装说明见仓库根目录 [README](../README.md)。 See the repository root [README](../README.md) for features and installation.

## 目录结构 · Directory Layout

```
vscode-plugin/
├── src/
│   ├── extension.ts          # 入口:注册命令与菜单 · entry: registers commands and menus
│   ├── ai/                   # AI 客户端(OpenAI 兼容 / 托管免费网关)· AI clients
│   ├── settings/             # 配置持久化、Profile、API Key、输出语言 · settings & secrets
│   ├── diff/                 # Git 集成与 diff 构建 · Git integration & diff building
│   ├── commands/             # 生成、切换模型命令 · generate & model-switch commands
│   └── ui/                   # 设置面板 Webview · settings webview
├── resources/                # 图标、默认 Prompt、免费网关配置 · icons, prompt, gateway config
├── dist/                     # esbuild 打包产物 · bundled output
├── package.json
├── tsconfig.json
└── esbuild.js
```

## 开发 · Development

```bash
npm install
npm run compile   # typecheck + bundle to dist/
```

用 VS Code 打开 `vscode-plugin/` 目录后按 F5 调试 Extension Development Host;`npm run package` 产出 .vsix。

Open the `vscode-plugin/` folder in VS Code and press F5 to debug in the Extension Development Host; `npm run package` produces a .vsix.
