# AI Commit Message · JetBrains Plugin

功能介绍与安装说明见仓库根目录 [README](../README.md)。 See the repository root [README](../README.md) for features and installation.

## 目录结构 · Directory Layout

```
jetbrains-plugin/
├── src/com/liubs/aicommit/
│   ├── action/               # 提交工具栏分体按钮、模型菜单、生成动作 · toolbar split button, model menu, generate action
│   ├── ai/                   # AI 客户端(OpenAI 兼容 / 托管免费网关)与 Prompt 模板 · AI clients & prompt templates
│   ├── diff/                 # 勾选变更 → unified diff · selected changes to unified diff
│   ├── settings/             # 配置持久化、Profile、PasswordSafe、输出语言 · settings & secrets
│   │   └── ui/               # 模型设置对话框 · settings dialog
│   └── util/                 # 通知工具 · notification helper
├── resources/
│   ├── META-INF/             # plugin.xml 与插件图标 · plugin.xml and plugin icons
│   ├── prompts/              # 默认 Prompt 模板 · default prompt template
│   └── managed-free-provider.properties   # 免费网关配置 · free gateway config
├── .idea/                    # IDEA 项目配置(含 Run Plugin 运行配置)· IDEA project config incl. run configuration
└── ai-commit-message.iml     # Plugin DevKit 模块 · Plugin DevKit module
```

## 开发 · Development

1. 用 IntelliJ IDEA 打开 `jetbrains-plugin/` 目录(不是仓库根目录),安装 **Plugin DevKit** 插件,并在 SDK 设置中配置 IntelliJ Platform Plugin SDK(2020.3+)。
   Open the `jetbrains-plugin/` folder (not the repository root) in IntelliJ IDEA, install the **Plugin DevKit** plugin, and configure an IntelliJ Platform Plugin SDK (2020.3+).
2. 使用内置的 **Run Plugin** 运行配置启动沙箱 IDE 调试。
   Use the bundled **Run Plugin** run configuration to launch a sandbox IDE for debugging.
3. 通过 `Build | Prepare Plugin Module 'ai-commit-message' For Deployment` 产出可安装的插件包。
   Build an installable plugin archive via `Build | Prepare Plugin Module 'ai-commit-message' For Deployment`.
