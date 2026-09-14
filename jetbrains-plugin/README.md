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
│   └── util/                 # 通知与进度任务兼容工具 · notification and progress helpers
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

## API 兼容性 · API Compatibility

最低支持版本保持 IntelliJ Platform 2020.3（`since-build="203"`），发布类文件使用 Java 11。

- 进度任务统一通过 `ProgressTasks` 调用，显式传递 `ProgressIndicator`。为保留 2020.3 的原生进度、取消和模态行为，该工具继续使用新版 SDK 中标记为 `Obsolete` 的 `Task` API。
- 模型选择使用平台 `ToggleOptionAction`，由新版平台提供 EDT 更新。分体按钮的项目可用性检查仍使用旧版 `update()` 约定；2020.3 没有 `ActionUpdateThread`，因此新版 IDE 仍可能对此处记录 `OLD_EDT` 提示。
- 配置列表保留原生 `ToolbarDecorator`，仅通过 `AnActionButton` 设置复制图标，以保留旧版的选择状态和快捷键处理。这是另一个已知的 `Obsolete` 使用点。

升级 API 时需同时编译 203 基线和较新版 SDK，并用 JetBrains Plugin Verifier 检查实际发布 JAR。不要通过实现类、废弃接口或伪造平台类来绕过版本差异。交互和取消回归检查见 [tests/README.md](tests/README.md)。
