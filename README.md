# 文游 α（WenYou TextQuest Alpha）

运行于 Android 的文字冒险游戏平台：支持完全离线的分支剧情，也支持接入第三方大模型 API 获得 AI 场景生成与 AI 导演自由模式。技术栈为 Kotlin、Jetpack Compose、Material 3；所有数据以 JSON 保存在应用私有目录。

## 功能

- 节点式分支引擎：节点分为叙述（`NARRATION`）、AI 生成场景（`AI`）、结局（`ENDING`）三类。支持节点进入效果、选项显示条件与选择效果、数值变量、场景标记、掷骰，以及 `${变量}` 文本插值，实现在 `data/engine/GameEngine.kt`。
- 角色卡：以名字、Emoji、性格、说话风格、背景、台词示范等字段构成角色人设，编辑后注入 AI 系统提示；对局中可维护角色的 0..100 状态值与标记，定义见 `data/model/CharacterMetrics.kt`。
- 底层基调（不可动摇规则）：独立、可复用实体，可新建多条；每个角色可多选要执行的底层基调。AI 注入时先执行底层基调、再按人设扮演，冲突时以此层为准，见 `data/model/Models.kt` 的 `BottomRule` 与 `AiDirector.personaCard`。
- 多品牌 AI 接入：OpenAI 兼容协议覆盖 DeepSeek、Kimi、GLM、Qwen、豆包、OpenRouter、硅基流动、小米 MiMo、Ollama 等服务；Anthropic 与 Gemini 分别走 Messages API 与 `streamGenerateContent` 原生协议。统一为 SSE 流式输出，提供连接测试与模型列表拉取。
- AI 正文清洗：生成结果统一剥除 markdown（加粗/列表/标题/斜体/引用/代码块）、剔除导演式思考泄漏行，思考内容独立展示不混入角色回复。DeepSeek 推理模型（`deepseek-reasoner`）自动免 `temperature`、放宽超时与 `max_tokens`，并兼容 `reasoning_content`/`reasoning` 思考字段。
- 分享与导入：剧情与角色可生成分享码（WY2 deflate 压缩文本）或二维码（单张优先，过大自动拆成多片 QR Book 轮播）；支持粘贴分享码、相机扫码、相册一次多选整套二维码导入，按 id 只补不覆盖并提示重复内容。
- 对局存档：支持随时存档、主页续玩，以及整包 JSON 导出 / 导入。

## AI 一句话创建

主页「AI 一句话创建剧情 / 人物」、剧情库和角色库「AI 创建」入口使用当前默认 AI 服务，支持「剧情＋人物」和「仅人物」。输入一句创意，先预览生成结果，再保存并打开现有编辑器继续修改。

剧情生成 AI 导演模式的标题、简介、世界观、开场和关联人物；人物包含性格、背景、说话习惯及台词示例。应用生成独立 ID 并绑定人物，新增内容不覆盖已有资料；保存失败后可重试同一草稿。支持取消生成，异常保留输入供重试；缺失字段、重复名字、过长内容及关闭成人内容时的不兼容结果会被拒绝。

## 生成实时通知（3.8.0）

验证：23 项单元测试、Android Lint、独立 APK 构建及 Pixel 7 / Android 14 通知生命周期设备测试通过。两版使用已有 DeepSeek 配置实际生成成功，普通完成通知可见；Beta 验证拒绝权限保持关闭、重新授权开启。实际小米超级岛及 Android 16 系统提升效果尚未实机验证。

设置 → 生成实时通知，可开启用户主动发起的 AI 请求进度提示。原生通知显示当前阶段、真实耗时和并行请求数，点击回到应用；通知不含剧情、思考正文、服务密钥或提示词。生成期间启用短时 dataSync 前台服务；请求结束、取消、关闭开关或禁用通知通道后退出，不自动重启任务。完成/失败使用普通通知，15 秒后清除；取消不留下完成提示。进程被强制停止后任务不会恢复。

Android 16 使用原生 ProgressStyle 未知进度样式；Android 16 QPR2 通过官方 extras 请求实时更新，是否提升由系统及用户设置决定。旧设备显示普通持续进度通知，不推测完成百分比。

小米 OS2/OS3 按 `notification_focus_protocol` 添加 `miui.focus.param` 及图标 Bundle，使用官方文本模板，未授权时保留普通通知。Alpha 与 Beta 需要分别申请包名、签名及 `ai_generation` 场景权限；该场景标识是待审核配置，平台若核准其他标识需同步替换。当前只完成代码适配，不代表已获超级岛资格。参考 [小米开发指南](https://dev.mi.com/xiaomihyperos/documentation/detail?pId=2131)、[接入流程](https://dev.mi.com/xiaomihyperos/documentation/detail?pId=2132)、[Android 实时更新](https://developer.android.com/develop/ui/views/notifications/live-update)。

## 外观与排版

设置 → 外观 → 界面风格可切换 Material You 与液态玻璃，两者都支持跟随系统、浅色及深色。液态玻璃延续蓝色强调、分组背景和阅读字阶，在浮动导航及对局操作区实时采样背后内容，加入高斯模糊、边缘折射、高光与阴影；文字和图标独立绘制保持清晰；动态壁纸取色仅适用于 Material You。主题选择保存于本机，旧版 Apple 风格自动使用液态玻璃。Android 13+ 支持折射，Android 12 使用模糊，Android 8–11 回退为可读的着色材质。

颜色、字体、圆角集中在 `ui/theme/`，公共卡片与输入组件复用主题；设置页直接观察资料库，不再复制服务列表状态。选色面板与设置操作自动换行，结局按钮纵向排列，对局操作区可滚动并避让键盘，系统栏随主题切换。

设计参考：[Apple HIG](https://developer.apple.com/design/human-interface-guidelines)、[compose-cupertino](https://github.com/alexzhirkevich/compose-cupertino)。使用现有 Compose 控件和 Android 字体，无新增 UI 依赖。玻璃渲染使用 Compose GraphicsLayer 与 Android RenderEffect / AGSL，参考 [Apple 材质指南](https://developer.apple.com/design/human-interface-guidelines/materials)，渲染代码为本项目实现。

验证：19 项回归测试、玻璃背景采样与动态更新设备测试、Android Lint 与构建通过；Pixel 7 / Android 14 虚拟机检查主题切换与重启保持、200% 字号、选色面板、结局和键盘输入。

## 玩法模式

| 模式 | 玩法 | 是否依赖 AI | 适用场景 |
| --- | --- | --- | --- |
| 分支剧本（`SCRIPT`） | 作者预编排节点与选项，引擎按条件、效果、掷骰推进 | 否，完全离线 | 结构可控的多线叙事、多结局 |
| AI 场景节点 | 分支骨架中插入 `AI` 节点，由模型生成正文与动态选项，可通过主线出口接回作者节点 | 是 | 框架稳定、局部自由发挥 |
| AI 导演（`AI_DIRECTOR`） | 整局自由对话推进，模型同时扮演角色与主持人，维护世界观与人设一致性 | 是 | 开放结局的探索式叙事 |

## 架构总览

应用按“UI → ViewModel → 容器服务 → 引擎 / 网络 / 持久化”分层。`WenYouApp.AppContainer` 为手写依赖注入入口，不引入 Hilt：

```mermaid
flowchart TB
    subgraph UI["ui/ · Compose"]
        S[Screen 页面]
        VM[ViewModel]
    end

    subgraph CORE["WenYouApp.AppContainer"]
        ENG[GameEngine 分支引擎]
        DIR[AiDirector 提示词与解析]
        CL[ChatClient 流式客户端]
        LIB[LocalLibrary JSON 资料库]
        ST[SettingsStore 设置]
    end

    subgraph EXT["外部"]
        F[JSON 文件 · saves/stories/characters/providers/bottom_rules]
        API[第三方 LLM API]
    end

    S --> VM
    VM --> ENG
    VM --> DIR
    VM --> LIB
    VM --> ST
    DIR --> CL
    LIB --> F
    CL --> API
```

对局页由 `PlayViewModel` 统一驱动状态机，三种玩法共用同一套阶段：

```mermaid
stateDiagram-v2
    [*] --> INIT
    INIT --> AUTHORED : 分支剧本载入 / 读档
    INIT --> DM_INPUT : AI 导演载入
    INIT --> STOPPED : 剧情或节点缺失

    AUTHORED --> AI_WORKING : 选择进入 AI 节点 / 继续生成
    AUTHORED --> STOPPED : 抵达结局 / 无后续分支
    AUTHORED --> AUTHORED : 选项指回本节点，不重复正文

    DM_INPUT --> AI_WORKING : 玩家输入 / 采用灵感
    AI_WORKING --> AUTHORED : 场景生成完成（返回动态选项）
    AI_WORKING --> DM_INPUT : 导演回复完成
    AI_WORKING --> STOPPED : 生成失败
```

对局页「重开本局」会丢弃当前会话并重新执行开局流程：分支剧本回到起始节点（若起始节点为 AI 节点则直接进入 `AI_WORKING`），AI 导演剧本回到 `DM_INPUT`。

## AI 生成链路

每次生成先拼提示词（人设卡、最近剧情、变量与角色状态快照、底层基调），再以 SSE 逐帧接收文本增量驱动打字机，结束后把完整输出解析为结构化结果并做正文清洗（剥 markdown、剔思考泄漏）：

```mermaid
sequenceDiagram
    participant P as PlayViewModel
    participant D as AiDirector
    participant C as ChatClient
    participant A as LLM API
    participant U as UI 状态

    P->>D: generateScene / directorTurn
    D->>C: streamText(system, user)
    C->>A: POST（stream=true）
    loop SSE data 帧
        A-->>C: 文本增量
        C-->>P: onDelta
        P-->>U: 打字机追加显示
    end
    C-->>D: 完整文本
    D->>D: parseScene → 提取 JSON
    D-->>P: AiScene(text, choices)
```

### 协议适配

三类 `ProviderKind` 的端点与解析路径如下，实现集中在 `data/llm/ChatClient.kt`：

| 协议 | 聊天端点 | 增量字段 | 模型列表端点 |
| --- | --- | --- | --- |
| OpenAI 兼容 | `POST {base}/chat/completions` | `choices[0].delta.content`，推理模型回退 `reasoning_content` / `reasoning` | `GET {base}/models`，取 `data[].id` |
| Anthropic | `POST {base}/v1/messages` | `content_block_delta` 的 `delta.text` | `GET {base}/v1/models`，取 `data[].id` |
| Gemini | `POST {base}/models/{model}:streamGenerateContent?alt=sse` | `candidates[0].content.parts[].text` | `GET {base}/models?pageSize=1000`，取 `models[].name`（去 `models/` 前缀） |

模型输出约定为单个 JSON 对象，由 `AiDirector.parseScene` 解析。思考过程独立折叠，旁白与角色内对话按发生顺序分别展示：

```json
{
  "entries": [
    { "speakerId": "", "text": "本幕旁白……" },
    { "speakerId": "角色id", "text": "角色台词……" },
    { "speaker": "临时人物称谓", "text": "临时人物台词……" }
  ],
  "choices": [
    { "text": "选项一" },
    { "text": "带主线出口的选项[to:node_id]" }
  ]
}
```

`[to:节点id]` 标记仅用于 AI 场景节点接回作者分支。兼容旧版 `text` 正文格式与存档；旧存档中已混写的正文保留原文。服务返回的思考字段不再回退为旁白。

## 版本

文游 α 内置异性恋、男/女同性恋、双性恋、泛性恋与无性恋角色及配套剧情。成人向（18+）内容覆盖同样的取向：异性向在 `wenyou-adult-straight-presets.json`，男同、女同、双性恋、泛性恋与无性恋各一组在 `wenyou-adult-diverse-presets.json`。设置页提供内容开关。

构建配置见 `app/build.gradle.kts`。内容开关只影响列表过滤，不删除本地数据；过滤逻辑位于 `ui/vm/LibraryViewModel.kt`。成人向的性取向预设受「显示 LGBT」与「成人内容」两个开关共同约束（见 `WenYouApp.onCreate`）。

### 内容分级

剧情与角色都可标记内容分类（LGBT / 18+），在各自编辑器中以开关设置：

- 标记影响剧情库/角色库的分类筛选，并受内容开关约束；
- 分类采用「按标签归属匹配」——同时标记 LGBT 与 18+ 的作品在两个筛选下都会出现，卡片也会同时显示两个标签；
- 两项都未标记则归为「全年龄」。

## 数据与预设

运行时数据分五个 JSON 文件存于应用私有目录，字段均对手工编辑友好：

| 文件 | 内容 | 维护入口 |
| --- | --- | --- |
| `providers.json` | AI 服务档案（品牌、baseUrl、Key、模型） | 「AI 服务」页 |
| `characters.json` | 角色卡（含底层基调多选 `bottomRuleIds`） | 「角色」页 |
| `stories.json` | 剧情节点图与会话设置 | 「剧情」编辑器 |
| `saves.json` | 存档（含日志与角色状态快照） | 对局内 / 主页 |
| `bottom_rules.json` | 底层基调（不可动摇规则）实体 | 设置 → 底层基调 |

内置题材预设以 `assets/presets/*.json` 提供，启动时检查合并状态：尚未合并过的包按 id 并入资料库，规则为只补不覆盖；已合并的文件记录在 `SettingsStore` 的 `preset_files_applied_v2` 中，避免重复导入。打包以 `app/src/alpha/assets/presets/` 下的文件为准；`presets/` 目录只保留历史生成脚本及其旧输出，与打包内容并不一致。

## 构建

编译环境要求：`compileSdk 35`、`minSdk 26`、`targetSdk 34`、JDK 17。仓库自带 Gradle wrapper（8.9），可直接用 Android Studio（Ladybug 或更新）打开运行。

命令行构建示例：

```bash
./gradlew :app:assembleAlphaDebug
```

构建输出默认位于 Gradle 用户目录的 `caches/wnq-build/WenYouTextQuest`，以避开 OneDrive 文件锁；可用环境变量 `WENYOU_BUILD_DIR` 指定其它位置。

回归与静态检查（18 项 JVM 回归测试）：

```bash
./gradlew :app:testAlphaDebugUnitTest :app:lintAlphaDebug
```

测试覆盖资料库并发写入与失败保护、分支存读档、节点循环、角色条件、掷骰边界、AI 正文/思考与状态解析、分享码完整性和解压大小限制，以及内置预设包的完整性与自洽。网络测试使用本地拦截响应，不需要 API Key。接管审核记录见 [AUDIT.md](AUDIT.md)。

版本号按 `x.yy.zz` 规则维护在 `version.properties`；执行 `./gradlew bumpVersion` 递增：

- `bumpVersion`（默认 / `-Pbump=patch`）：仅 bug 修复，`zz` +1（范围 0–99，满 100 进位到 `yy`）。
- `-Pbump=minor`：新功能或重大变化，`yy` +1 且 `zz` 归零（`yy` 范围 0–9，满 10 进位到 `xx`）。
- `-Pbump=major`：重大架构变化或巨大功能增加，`xx` +1 且 `yy=zz=0`。

`versionCode` 在每次 `bumpVersion` 时单调递增。正式打包前应先执行该任务。

## 目录结构

```text
app/src/main/java/io/wenyou/textquest/
├── CrashLog.kt        崩溃日志多路径落盘（内部 / 外部 / SAF Documents）
├── data/model/        持久化模型，JSON 序列化字段对手工编辑友好
├── data/engine/       分支引擎：条件、效果、掷骰、模板插值，纯逻辑无 IO
├── data/ai/           AI 场景与导演的提示词组装、模型 JSON 输出解析与正文清洗
├── data/llm/          多协议流式客户端与品牌预设目录
├── data/repo/         本地 JSON 资料库与 SharedPreferences 设置
├── data/sample/       首次启动植入的示例角色与剧情
└── ui/                Compose 页面、ViewModel、主题
```

## 贡献者

- [wangmikuwang](https://github.com/wangmikuwang)：项目发起、整体架构与产品设计。
- Little Code Sauce（AI 编程搭档）：功能实现、代码审核与优化、构建与发布流程。

## 设计决策与已知限制

- 未引入 Hilt 与 Room：依赖注入在 `WenYouApp` 中手动完成，持久化直接读写 JSON 文件。资料库内部统一串行写入，先写临时文件再原子替换，成功后才更新对应内存列表；失败会显示错误。多文件整包导入仍不具备跨文件事务，部分文件写入成功后失败时应重新导入完整备份。
- 分享码兼容 WY1/WY2；解压后的 JSON 上限为 8 MiB，截断或超限负载会拒绝导入。超过分享上限的内容请使用设置中的整包 JSON 导出。
- AI 上下文取最近 `historyWindow` 条日志，超出部分自动截断，以避免提示词超长。
- 流式生成结束前不写入对局日志，因此生成过程中无法保存“半句”内容；整段结束后存档即为一致状态。
- AI 生成正文统一清洗（剥 markdown、剔思考泄漏），仅作用于 AI 生成，作者手写节点文本保留原样。
- 底层基调支持独立实体与角色内嵌单条两种来源，均注入人设最底；角色删除某条引用或删除规则时自动摘除关联，避免悬空 id。
- 用户已删除的内置内容不会在后续启动时被自动写回。

## 剧情记忆与人物关系

对局右上角人物按钮可查看累计剧情记忆、对玩家的好感和信任、人物之间的关系及变化原因。AI 场景与 AI 导演每轮更新已发生事件的摘要，并在后续续写中带入记忆和当前关系；这些信息随存档保存，兼容旧存档。模型未返回记忆时保留原记录，不额外调用摘要服务。记忆是有限长度的 AI 摘要，不能保证保留所有细节。

## 生成进度、用量与费用

生成过程中显示等待、思考或生成阶段、实际耗时与接收字符数，不显示虚假的完成百分比。AI 服务页、对局和一句话创建可查看本机最近 100 次请求，记录实际服务返回的输入/输出及缓存 tokens、耗时和完成/失败/取消状态，并在本机保存。未知用量和取消/失败请求的费用不按零计。编辑服务时可填写当前模型每百万 tokens 的输入、输出、缓存读取和写入单价及 CNY/USD 等币种；费用为按请求时配置的估算，缺少必需单价时显示未知，不同币种分别汇总。换模型时请核对价格，账单以服务商为准。

协议参考：[DeepSeek 流式用量](https://api-docs.deepseek.com/api/create-chat-completion/)、[Anthropic 流式用量](https://platform.claude.com/docs/en/build-with-claude/streaming)、[Gemini usageMetadata](https://ai.google.dev/api/generate-content#UsageMetadata)。
