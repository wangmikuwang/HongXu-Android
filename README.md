<div align="center">

<img src="docs/launcher-icon-preview.png" width="132" alt="虹叙应用图标">

# 虹叙 · HongXu

**让你的故事继续。**

多元人物与情感故事的 Android 文字冒险与 AI 叙事工坊——离线游玩分支剧本，或接入大模型，让 AI 导演与角色陪你即兴共创。

[![最新版本](https://img.shields.io/github/v/release/wangmikuwang/WenYou-TextQuest?label=%E6%9C%80%E6%96%B0%E7%89%88%E6%9C%AC&color=ff2d8f)](https://github.com/wangmikuwang/WenYou-TextQuest/releases/latest)
![Android 8.0+](https://img.shields.io/badge/Android-8.0%2B-3DDC84?logo=android&logoColor=white)
![Kotlin](https://img.shields.io/badge/Kotlin-2.0-7F52FF?logo=kotlin&logoColor=white)
![Jetpack Compose](https://img.shields.io/badge/Jetpack%20Compose-Material%203-4285F4?logo=jetpackcompose&logoColor=white)
![本地优先](https://img.shields.io/badge/%E6%95%B0%E6%8D%AE-%E4%BB%85%E5%AD%98%E6%9C%AC%E6%9C%BA-2ea44f)

### [⬇️ 下载最新版](https://github.com/wangmikuwang/WenYou-TextQuest/releases/latest)

[功能](#-亮点) · [快速开始](#-快速开始) · [玩法](#-三种玩法) · [开发者](#%EF%B8%8F-开发者) · [更新日志](CHANGELOG.md) · [致谢](#-致谢)

</div>

<br>

<table>
  <tr>
    <td align="center"><img src="docs/screenshots/home.png" width="200" alt="首页"><br><sub>首页 · 一键续写</sub></td>
    <td align="center"><img src="docs/screenshots/play.png" width="200" alt="AI 导演对局"><br><sub>AI 导演 · 旁白与角色分栏</sub></td>
    <td align="center"><img src="docs/screenshots/choices.png" width="200" alt="分支剧本"><br><sub>分支剧本 · 选择推动剧情</sub></td>
    <td align="center"><img src="docs/screenshots/branch-map.png" width="200" alt="分支图"><br><sub>分支图 · 探索进度</sub></td>
  </tr>
</table>

<p align="center">
  <img src="docs/screenshots/glass-lens.png" width="540" alt="液态玻璃透镜底栏"><br>
  <sub>🫧 按住或拖动底栏：选中胶囊化作玻璃透镜，放大并折射下方图标</sub>
</p>

## ✨ 亮点

- 🎭 **两种故事，一个应用**：作者预编排的**分支剧本**完全离线可玩；**AI 导演**模式里，模型同时扮演主持人与角色，和你即兴共创。
- 🧑‍🤝‍🧑 **会记事的角色**：人设卡、0–100 状态值、人物关系与剧情记忆随存档保存；开局可选择扮演哪位角色。
- ✍️ **一句话创作**：一句创意生成完整剧情与人物，再用「一句话修改」微调，预览后才写入。
- 🗺️ **分支图与探索进度**：游玩中随时查看剧情走向，标出当前位置、已到达节点和已解锁结局。
- 🫧 **液态玻璃界面**：实时模糊与边缘折射的玻璃材质，按住底栏会浮起一枚会放大的透镜；也可切回 Material You。
- 🌈 **多元人物与情感故事**：内置异性恋、同性恋、双性恋、泛性恋与无性恋角色及配套剧情；LGBT 与 18+ 内容各有独立开关，只影响显示、不删数据。
- 📦 **分享与导出**：剧情和角色可生成分享码或多页二维码；对局可导出成小说文本；整包备份随时迁移。
- 🔒 **本地优先**：所有数据只存在你的手机里；AI 请求直连你自己配置的服务商，密钥不上传。

## 🚀 快速开始

1. **安装**：到 [Releases](https://github.com/wangmikuwang/WenYou-TextQuest/releases/latest) 下载 `HongXu-v版本号.apk`。需要 Android 8.0 及以上；首次安装请允许本应用「安装未知应用」。之后应用会自动检查更新，在应用内下载并校验。
2. **开玩**：打开即可游玩内置剧情。标着「分支剧本」的故事完全离线，不需要任何配置。
3. **接入 AI（可选）**：设置 → AI 服务与生成 → 管理 AI 服务，添加服务商的地址、密钥和模型，先「测试连接」再保存。之后就能玩 AI 导演、AI 场景，以及一句话创作。

支持 OpenAI 兼容协议（DeepSeek、Kimi、GLM、Qwen、豆包、OpenRouter、硅基流动、小米 MiMo、Ollama 等），以及 Anthropic 与 Gemini 原生协议。

## 🎮 三种玩法

| 模式 | 怎么玩 | 需要 AI 吗 | 适合 |
| --- | --- | --- | --- |
| 分支剧本 | 作者预编排节点与选项，引擎按条件、效果、掷骰推进 | 不需要，完全离线 | 多线叙事、多结局 |
| AI 场景节点 | 分支骨架中插入 AI 节点，模型生成正文与动态选项，可接回主线 | 需要 | 框架稳定、局部自由发挥 |
| AI 导演 | 整局自由对话推进，模型同时扮演角色与主持人 | 需要 | 开放式探索叙事 |

## 📖 功能详解

### 游玩

- **对局界面**：AI 思考过程（可折叠）、旁白、角色台词分开显示，按发生顺序排列；AI 导演会给出走向灵感，点一下即可采用，也能自由输入。
- **选择身份**：开局或重开时可扮演剧情里的某位角色，或用自由身份；身份随存档保存。
- **剧情记忆与人物关系**：对局右上角的人物按钮查看 AI 累计记下的关键事件、好感与信任，以及人物之间的关系变化。
- **分支图**：分支剧本的选项上方点「分支图」，只读查看整部剧情，自动定位当前位置；「已到达 x / y 个节点 · 已解锁结局 a / b」帮你查漏补缺。探索记录随整包备份一起导出与合并。
- **存档**：随时存档、首页续玩；在剧情的「读取存档」中可重命名，留空恢复自动名称。
- **导出对局文本**：在剧情记忆侧栏或结局面板导出 .txt，按顺序保存旁白、角色台词与你的选择，不含 AI 思考过程。
- **成就馆**：7 项成长成就（初次启程、世界探索者、命运抉择、灵感火花、共创故事、旅途终章、结局收藏家）；反复读档不会刷进度，备份恢复也不会清空已解锁成就。

### 创作

- **手动编辑**：底栏中央「创建」新建剧情或角色。剧情由叙述、AI 场景、结局三类节点组成，支持进入效果、选项显示条件、选择效果、数值变量、场景标记、掷骰和 `${变量}` 文本插值；编辑页的「分支图」可点节点直接跳到对应位置。
- **角色卡**：名字、Emoji、性格、说话风格、背景与台词示范构成人设，注入 AI 提示；可设定 0–100 的状态值与标记。
- **底层基调**：独立、可复用的「不可动摇规则」，每个角色可多选；AI 先遵守底层基调，再按人设扮演。
- **一句话创作与修改**：一句创意生成剧情与人物的完整表单（导演要求、变量、节点分支、人设与规则）。创建页、剧情编辑页、人物编辑页都能「一句话修改」，先预览再应用，未要求修改的内容保持原样。

### 内容与分级

内置异性恋、男/女同性恋、双性恋、泛性恋与无性恋角色及配套剧情，成人向（18+）内容覆盖同样的取向。剧情与角色都可标记 **LGBT** 与 **18+**：同时标记的作品在两个筛选下都会出现，都未标记则归为「全年龄」。内容开关只影响列表显示，不会删除本地数据。

### 外观

- **两种界面风格**：Material You（支持动态取色）与液态玻璃，均支持跟随系统、浅色与深色。
- **液态玻璃**：浮动导航与对局操作区实时采样背后内容，带高斯模糊、边缘折射、高光与阴影；文字和图标单独绘制，保持清晰。按住或拖动底栏，选中胶囊会膨胀成放大、折射的玻璃透镜（Android 13 及以上），松手弹回。Android 12 使用模糊，Android 8–11 回退为可读的着色材质。
- **外观与主题**：界面预设、屏幕帧率、图标与弹窗样式、色彩风格、高级配色、字号与界面缩放、字体加粗、开屏壁纸、桌面图标和界面标签语言。
- **字体**：默认使用离线内置的[霞鹜文楷](https://github.com/lxgw/LxgwWenKai)，也可导入自己的字体。
- **系统长截图**：Android 12 及以上支持滚动截图，浮层不会遮挡正文。

### 分享与数据

- **分享码与二维码**：剧情和角色可生成压缩分享码，或二维码（内容大时自动拆成多页轮播）；支持粘贴、相机扫码和相册多选导入，只补不覆盖。
- **整包备份**：设置 → 存储与备份，导出或导入全部剧情、角色、存档、服务配置、成就与探索记录。

### AI 服务

- **多协议流式输出**：统一 SSE 流式接收，提供连接测试与模型列表拉取。
- **用量与费用**：记录本机最近 100 次请求的实际 tokens、耗时与状态；可填写模型单价，DeepSeek 官方接口支持官方价格快照与峰谷时段。未知用量不按零计，账单以服务商为准。
- **生成实时通知**：可选的生成进度通知，显示阶段与真实耗时，不含剧情内容；已完成小米超级岛代码适配（需平台授权后生效）。

### 更新

- 启动和返回前台时自动检查官方正式发布，在应用内下载，下载后核对 SHA-256、大小、包名、签名与版本，再交给系统安装界面确认。
- 仓库中的 `update-policy.json` 声明最低支持版本，低于该版本时只显示升级页。更新请求只访问公开的 GitHub 接口。

### 彩蛋

设置页与成就馆藏着三个小惊喜，只在本机显示，不改动存档和成就。

<details>
<summary>查看彩蛋线索（剧透）</summary>

- 设置 → 系统与关于，连续点五次标题「虹叙」：幕后导演。
- 长按同一标题：第四面墙。
- 成就馆长按标题（或连续点五次）：好奇心万岁隐藏奖杯，不计入七项成就。

</details>

## 🔒 隐私

- 剧情、角色、存档、服务配置与 AI 密钥只保存在应用私有目录，不上传到任何服务器。
- AI 请求由手机直接发给你配置的服务商；应用更新只访问公开的 GitHub 接口，不携带任何个人数据。
- 分享码和二维码只包含你选中的剧情或角色，不含密钥、存档或服务配置。

## 🛠️ 开发者

<details>
<summary><b>架构总览</b></summary>

应用按「UI → ViewModel → 容器服务 → 引擎 / 网络 / 持久化」分层，`WenYouApp.AppContainer` 为手写依赖注入入口，不引入 Hilt：

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
        F[JSON 文件]
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
    INIT --> ROLE_SELECT : 新开局 / 重开
    ROLE_SELECT --> AUTHORED : 选定身份（分支剧本）
    ROLE_SELECT --> DM_INPUT : 选定身份（AI 导演）
    INIT --> AUTHORED : 读档
    INIT --> DM_INPUT : 读档（AI 导演）
    INIT --> STOPPED : 剧情或节点缺失

    AUTHORED --> AI_WORKING : 进入 AI 节点 / 继续生成
    AUTHORED --> STOPPED : 抵达结局 / 无后续分支
    DM_INPUT --> AI_WORKING : 玩家输入 / 采用灵感
    AI_WORKING --> AUTHORED : 场景生成完成
    AI_WORKING --> DM_INPUT : 导演回复完成
    AI_WORKING --> STOPPED : 生成失败
```

</details>

<details>
<summary><b>AI 生成链路与协议适配</b></summary>

每次生成先拼提示词（人设卡、最近剧情、变量与角色状态快照、底层基调），以 SSE 逐帧接收增量，结束后解析为结构化结果并清洗正文（剥离 markdown、剔除思考泄漏）：

```mermaid
sequenceDiagram
    participant P as PlayViewModel
    participant D as AiDirector
    participant C as ChatClient
    participant A as LLM API

    P->>D: generateScene / directorTurn
    D->>C: streamText(system, user)
    C->>A: POST（stream=true）
    loop SSE data 帧
        A-->>C: 文本 / 思考增量
        C-->>P: 思考进度（合并刷新）
    end
    C-->>D: 完整文本
    D->>D: parseScene → 提取 JSON
    D-->>P: AiScene(entries, choices, state)
```

| 协议 | 聊天端点 | 增量字段 | 模型列表 |
| --- | --- | --- | --- |
| OpenAI 兼容 | `POST {base}/chat/completions` | `choices[0].delta.content`，推理模型另取 `reasoning_content` / `reasoning` | `GET {base}/models` |
| Anthropic | `POST {base}/v1/messages` | `content_block_delta` 的 `delta.text` | `GET {base}/v1/models` |
| Gemini | `POST {base}/models/{model}:streamGenerateContent?alt=sse` | `candidates[0].content.parts[].text` | `GET {base}/models` |

模型输出约定为单个 JSON 对象，由 `AiDirector.parseScene` 解析；思考过程单独折叠，不会被当作台词：

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

`[to:节点id]` 仅用于 AI 场景节点接回作者分支。实现集中在 `data/llm/ChatClient.kt` 与 `data/ai/AiDirector.kt`。

</details>

<details>
<summary><b>数据文件</b></summary>

运行时数据以 JSON 存于应用私有目录，字段对手工编辑友好；每次写入先写临时文件再原子替换：

| 文件 | 内容 |
| --- | --- |
| `providers.json` | AI 服务档案（地址、密钥、模型、单价） |
| `characters.json` | 角色卡（含底层基调引用） |
| `stories.json` | 剧情节点图与会话设置 |
| `saves.json` | 存档（日志、变量与角色状态快照） |
| `bottom_rules.json` | 底层基调 |
| `achievements.json` | 成就进度 |
| `progress.json` | 分支剧本的探索记录 |
| `usage.json` | 最近 100 次 AI 请求的用量与费用 |

内置预设位于 `app/src/<flavor>/assets/presets/`，启动时按 id 合并进资料库，只补不覆盖；已合并的文件会被记录，用户删除的内置内容不会被写回。

</details>

<details>
<summary><b>构建与测试</b></summary>

环境：JDK 17、`compileSdk 36`、`minSdk 26`、`targetSdk 34`，仓库自带 Gradle Wrapper 8.9，可直接用 Android Studio 打开。

```bash
./gradlew :app:assembleDebug        # 调试包
./gradlew :app:assembleRelease      # 正式包（R8 压缩与资源裁剪）
./gradlew :app:test :app:lint       # 单元测试与静态检查
./gradlew :app:connectedCheck       # 设备上的界面测试（需模拟器或真机）
```

构建输出默认放在 Gradle 用户目录的 `caches/wnq-build/HongXu`，以避开同步盘文件锁；可用环境变量 `WENYOU_BUILD_DIR` 指定位置。网络相关测试使用本地拦截响应，不需要 API Key。

版本号按 `x.yy.zz` 维护在 `version.properties`：

- 修复 bug：`./gradlew bumpVersion`，`zz` +1（满 100 进位）
- 新功能或重大变化：`-Pbump=minor`，`yy` +1 且 `zz` 归零（满 10 进位）
- 重大架构变化：`-Pbump=major`，`xx` +1 且其余归零

`versionCode` 每次递增；发布前先升版本。

</details>

<details>
<summary><b>目录结构</b></summary>

```text
app/src/main/java/io/wenyou/textquest/
├── CrashLog.kt        崩溃日志多路径落盘
├── data/model/        持久化模型
├── data/engine/       分支引擎、成就、对局文本导出（纯逻辑无 IO）
├── data/ai/           提示词组装、模型输出解析与正文清洗
├── data/llm/          多协议流式客户端、品牌预设、用量统计
├── data/repo/         本地 JSON 资料库与设置
├── data/sample/       首次启动植入的示例角色与剧情
└── ui/                Compose 页面、ViewModel、主题与液态玻璃
third_party/           随包组件的来源与许可
```

</details>

<details>
<summary><b>设计取舍与已知限制</b></summary>

- 不引入 Hilt 与 Room：依赖注入手写，持久化直接读写 JSON。单文件写入是原子的，但整包导入跨多个文件，中途失败时请重新导入完整备份。
- 分享码解压后上限 8 MiB，超限请改用整包备份。
- AI 上下文取最近若干条日志，超出部分截断；剧情记忆是有限长度的 AI 摘要，不保证保留所有细节。
- 流式生成结束前不写入对局日志，存档始终是一致状态。
- 正文清洗只作用于 AI 生成内容，作者手写的节点文本保持原样。

</details>

## 🙏 致谢

这个项目从一个想法开始，一路有 AI 伙伴并肩：**DeepSeek 的 Harness**、**OpenAI 的 Codex** 与 **Anthropic 的 Claude**，帮助把想法一点点变成现实。感谢的话写在[这条置顶 issue](https://github.com/wangmikuwang/WenYou-TextQuest/issues/1) 里。

- [wangmikuwang](https://github.com/wangmikuwang)：项目发起、整体架构与产品设计。
- Little Code Sauce（AI 编程搭档）：功能实现、代码审核与优化、构建与发布流程。

随包使用或参考的开源项目：

| 项目 | 用途 | 许可 |
| --- | --- | --- |
| [Ionicons](https://github.com/ionic-team/ionicons) | 圆润线框图标（子集） | MIT |
| [霞鹜文楷 LXGW WenKai](https://github.com/lxgw/LxgwWenKai) | 默认字体 | SIL OFL 1.1 |
| [Material Color Utilities](https://github.com/material-foundation/material-color-utilities) | 动态配色算法 | Apache-2.0 |
| [LiquidGlassKMP](https://github.com/philipplackner/LiquidGlassKMP) | 液态玻璃的分层设计参考（未使用其代码） | — |

来源与许可原文见 [`third_party/`](third_party/)。
