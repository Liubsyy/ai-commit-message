# VSCode 插件移植计划(1:1 复刻 jetbrains-plugin)

目标:在 `vscode-plugin/` 目录下实现一个功能与 JetBrains 插件完全对等的 VSCode 扩展 —— 从 SCM 提交框一键用 AI 生成 Conventional Commit 信息,支持托管免费模型、任意 OpenAI 兼容接口、多 profile 管理、自定义 Prompt 与输出语言。

## 一、JetBrains 版功能清单(移植基准)

| # | 功能 | JetBrains 实现 | VSCode 对应方案 |
|---|------|----------------|-----------------|
| 1 | 提交工具栏「AI Generate」按钮 | `AiCommitSplitButtonAction` 自绘胶囊分体按钮 | SCM 面板标题栏图标按钮(`scm/title` navigation 组;`scm/inputBox` 仍是 proposed API,第三方扩展不可用),生成中显示 loading |
| 2 | 下拉切换 供应商 → 模型 | `ProviderMenuGroup` 二级菜单 + 勾号 | 独立命令 + 两级 QuickPick(供应商 → 模型,当前项打勾),底部「模型设置…」入口 |
| 3 | 勾选变更 → unified diff | `ChangesDiffBuilder`(IdeaTextPatchBuilder,超长截断,兜底文件清单) | Git 扩展 API:优先 staged diff(`repo.diff(true)`),无 staged 时取工作区全部变更;同样按 `diffCharLimit` 截断,失败兜底文件清单 |
| 4 | OpenAI 兼容客户端 | `OpenAiCompatibleClient`(chat/completions、models、ping,错误解析) | 同结构 TypeScript 移植,用全局 `fetch`,零第三方依赖 |
| 5 | 托管免费网关客户端 | `ManagedFreeClient`(commit-message、models、health,installationId 限流头) | 同结构移植,`X-AI-Commit-Client: vscode-plugin`,installationId 存 `globalState` |
| 6 | Profile 数据模型 | `ProviderProfile`(id/type/name/baseUrl/temperature/prompt/outputLanguage/models/selectedModel) | 同字段 TS interface,序列化 JSON |
| 7 | 设置持久化 | `AiCommitSettings` PersistentStateComponent(内置 free profile 兜底、selectedProfileId 失效回退、managedFreeModelConfigVersion 迁移) | `globalState` Memento,同样的 ensure/normalize/迁移逻辑 |
| 8 | API Key 安全存储 | PasswordSafe(按 profile id) | `context.secrets`(SecretStorage,按 profile id),删 profile 时清除 |
| 9 | 设置界面 | `SettingsDialog`:左侧列表(增/删/复制)+ 右侧表单(Name/BaseURL/Key/Model+拉取/语言/Prompt+恢复默认/测试连接),free profile 字段锁定 | Webview 面板 1:1 复刻同布局与交互(含校验:Name/BaseURL 非空;Save/Cancel) |
| 10 | 默认 Prompt 模板 | `resources/prompts/default-commit-prompt.md` + 代码内兜底 | 原文件复制到 `vscode-plugin/resources/prompts/`,加载失败用内置兜底串 |
| 11 | 输出语言 | `OutputLanguages`(11 种,auto 不加语言指令) | 同数组直接移植 |
| 12 | 免费模型列表后台刷新 | 打开菜单时 5 分钟节流刷新 | 打开模型 QuickPick 时同样节流刷新 |
| 13 | 进度与取消 | `Task.Backgroundable` + ProgressIndicator | `window.withProgress`(Notification 位置,cancellable)+ `AbortController` 贯穿 HTTP |
| 14 | 通知 | `Notifier` Balloon | `window.showInformation/Warning/ErrorMessage` |
| 15 | 生成结果清洗 | 去代码围栏/引号 | 同逻辑移植 |
| 16 | diff 字符上限 | `diffCharLimit`(默认 8000) | VSCode `configuration`:`aiCommitMessage.diffCharLimit`(默认 8000) |

## 二、目录结构

```
vscode-plugin/
├── package.json              # manifest:commands、menus(scm/title)、configuration、icon
├── tsconfig.json
├── esbuild.js                # 打包成单文件 dist/extension.js
├── .vscodeignore
├── resources/
│   ├── icon.png              # 复用 assets/logo.png
│   ├── sparkle.svg           # 按钮图标(明/暗两套或 currentColor)
│   ├── prompts/default-commit-prompt.md    # 原样复制
│   └── managed-free-provider.json          # baseUrl(对应 .properties)
└── src/
    ├── extension.ts          # activate:注册命令、菜单、SettingsPanel
    ├── ai/
    │   ├── aiClient.ts       # 接口:generateCommitMessage / ping / listModels
    │   ├── openAiCompatibleClient.ts
    │   ├── managedFreeClient.ts
    │   ├── aiClients.ts      # 按 profile.type 分发
    │   └── promptTemplates.ts
    ├── settings/
    │   ├── providerProfile.ts
    │   ├── settingsStore.ts  # globalState 持久化 + ensureManagedFreeProfile + 迁移
    │   ├── managedFreeProvider.ts
    │   ├── outputLanguages.ts
    │   └── apiKeyStore.ts    # SecretStorage 封装
    ├── diff/
    │   └── changesDiffBuilder.ts   # 基于 vscode.git API
    ├── commands/
    │   ├── generateCommitMessage.ts
    │   └── selectModel.ts    # 两级 QuickPick
    └── ui/
        └── settingsPanel.ts  # Webview 设置面板(HTML/CSS/JS 内联或 media/)
```

技术选型:TypeScript + esbuild,运行时零依赖(HTTP 用 Node 18+ 全局 `fetch`);`engines.vscode` 定为 `^1.82.0` —— 下限由全局 `fetch` 决定(VS Code 1.82 起 Node 升到 18.15),其余所用 API(SecretStorage 1.53+、scm/title、QuickPick、Webview)均远早于此。若未来需要下探到更低版本,可改用 Node `https` 模块替代 fetch。

## 三、关键设计决策(与 JetBrains 版的差异点)

1. **入口按钮**:VSCode 无法自绘"分体按钮",且输入框内按钮(`scm/inputBox`)至今仍是 proposed API(vscode#195474,Backlog),第三方扩展无法上架使用。因此拆成两个动作 —— 主按钮(✦ 图标,点击即生成)放 `scm/title` 标题栏;切换模型用 QuickPick 命令(命令面板与标题栏菜单均可触达)。这是平台限制下最接近的 1:1。
2. **"勾选的变更"语义**:JetBrains 的 included changes 对应 VSCode 的 **staged(Index)区**;若 staged 为空则取全部工作区变更(与主流 AI commit 扩展一致),并在使用工作区变更时保持静默、行为可预期。多仓库工作区时用当前活动/所属仓库,多个则 QuickPick 选择。
3. **diff 生成**:优先调用 git 扩展 API `repository.diff(cached)` 拿真实 unified diff;API 不可用时降级为直接执行 `git diff [--cached]`;再失败兜底输出文件级变更清单(同 JetBrains 兜底)。截断规则原样保留(前 N 字符 + 截断注记)。
4. **设置界面**:为保证 1:1(左列表 + 右表单 + 拉取模型 + 测试连接 + free profile 锁定),用 **Webview 面板**实现,不用原生 Settings UI;`diffCharLimit` 这类标量则同时暴露在 VSCode 原生设置里。Webview 遵循 CSP、使用 VSCode 主题变量适配明暗主题。API Key 只经 postMessage 往返内存,不落盘。
5. **持久化位置**:profiles/selectedProfileId/installationId/managedFreeModelConfigVersion 全部放 `globalState`(跨工作区全局,等价 application-level PersistentStateComponent);Key 放 SecretStorage。
6. **免费网关协议不变**:`POST /commit-message`、`GET /models`、`GET /health`,请求头 `X-AI-Commit-Client: vscode-plugin`、`X-AI-Commit-Installation: <uuid>`,超时(连接/读)用 AbortController 模拟。

## 四、实施步骤

1. **脚手架**:建 `vscode-plugin/` 工程(package.json、tsconfig、esbuild、.vscodeignore),注册空命令跑通 F5 调试。
2. **纯逻辑层移植**(与 UI 无关,可直接对照 Java 翻译):`providerProfile` / `outputLanguages` / `promptTemplates` / `openAiCompatibleClient` / `managedFreeClient` / `aiClients`,含全部错误解析与 cleanup 逻辑。
3. **存储层**:`settingsStore`(含 ensureManagedFreeProfile、版本迁移)、`apiKeyStore`、`managedFreeProvider`(读 resources 配置)。
4. **diff 层**:`changesDiffBuilder`(git API → CLI 降级 → 文件清单兜底,截断)。
5. **生成命令**:`generateCommitMessage` —— 无可用模型时先弹设置面板;取变更、withProgress 调 AI、结果写回 `repository.inputBox.value`;错误/空结果通知。
6. **模型切换**:两级 QuickPick + 免费模型 5 分钟节流后台刷新 + 「设置…」入口。
7. **设置 Webview**:复刻 SettingsDialog 布局与全部交互(增/删/复制、拉取模型、测试连接、恢复默认 Prompt、free profile 锁定、保存校验、删 profile 清 Key)。
8. **收尾**:manifest 完善(图标、双语描述与 README 同步)、`vsce package` 打包验证、更新根 README 目录结构说明。

## 五、验证方式

- F5 Extension Development Host 手工回归:免费模型全流程(拉模型/生成/切模型)、自定义 OpenAI 兼容配置(填 baseUrl+key → Fetch From Provider → Test Connection → 生成)、staged/未 staged/无变更三种状态、取消生成、删配置后 Key 清理、重启后配置保留。
- 网关侧联调:确认 `X-AI-Commit-Client: vscode-plugin` 不被网关拒绝(若网关校验 client 白名单需同步放行)。
- `vsce package` 产出 .vsix 并本地安装验证。

## 六、暂不做 / 风险

- 不做流式输出、不做逐字回填(JetBrains 版也没有)。
- 若 `scm/inputBox` 菜单点未来转为稳定 API,可在不破坏兼容的前提下把按钮迁到输入框内(通过 `when` 条件双投放)。
- Webview 表单与原生 Swing 观感必有差异,以"字段与交互一致"为 1:1 标准,而非像素级。
