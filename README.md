<div align="center">

# 知余 · Zhiyu

**一站式 AI 平台用量与余额监控**

![Platform](https://img.shields.io/badge/Platform-Android-3DDC84?logo=android&logoColor=white)
![Min SDK](https://img.shields.io/badge/minSdk-26%20(Android%208.0)-3DDC84)
![Language](https://img.shields.io/badge/Kotlin-100%25-7F52FF?logo=kotlin&logoColor=white)
![UI](https://img.shields.io/badge/Jetpack%20Compose-Material%203-4285F4)
![Version](https://img.shields.io/badge/version-1.0.0-blue)

</div>

---

## 功能简介

- **多平台聚合面板** —— 一个首页同时展示所有平台的限额进度与账户余额，瀑布流卡片布局，加载/陈旧状态一目了然。
- **状态栏常驻通知** —— 将任意平台「固定」到状态栏，持续显示用量百分比或余额，并标注最近更新时间。
- **桌面小组件** —— 2×2 主屏小组件，每 30 分钟自动刷新，无需打开 App 即可查看。
- **后台自动刷新** —— 基于 WorkManager 定时拉取最新数据，配合本地缓存，离线也能看到上次结果。
- **本地加密存储** —— 登录凭据与 API 密钥使用 `EncryptedSharedPreferences`（AES‑256）加密，**所有数据仅保存在本机**，不上传任何服务器。
- **WebView 登录 + API Key 双模式** —— 网页平台直接在内置 WebView 中登录，托管平台粘贴 API Key 即可。
- **备份与恢复** —— 一键将账号与密钥导出为 JSON 文件，换机或重装后导入即可恢复。

---

## 支持的平台与订阅

知余通过两种方式接入平台：**网页登录**（在内置 WebView 中登录后复用会话 Cookie）与 **API Key**（在对应控制台创建后粘贴）。

| 平台 | 接入方式 | 可识别订阅 / 档位 | 主要监控指标 |
| :--- | :--- | :--- | :--- |
| **Claude** | 网页登录 | Free · Pro · Max 5× · Max 20× · Team · Enterprise | 5 小时限额、周限额（所有模型 / Opus / Sonnet / Claude Design） |
| **Codex**（ChatGPT） | 网页登录 | 按账户计划自动识别 | 5 小时限额、周限额、Code Review（5 小时 / 周）、续订 / 到期时间、重置卡（数量 / 到期） |
| **Cursor** | 网页登录 | 按账户计划自动识别 | 本周期用量、Auto 用量、API 用量 |
| **OpenCode Zen** | 网页登录 | 按量计费（Pay-as-you-go） | 账户余额 |
| **MiniMax** | API Key（Token Plan 专属） | Token Plan | 5 小时限额、周限额（支持「无限制」与额度提升 Boost） |
| **AIHubMix** | API Key（令牌） | 按量计费账户 | 账户余额、已消费、累计请求次数 |
| **DeepSeek** | API Key | 按量计费账户 | 账户余额、赠送余额、充值余额 |

---

## 不同订阅的功能支持表

下表对照各平台 / 订阅在知余中可享受的功能。✅ 支持 · ➖ 该平台不适用。

| 功能 | Claude | Codex | Cursor | OpenCode Zen | MiniMax | AIHubMix | DeepSeek |
| :--- | :---: | :---: | :---: | :---: | :---: | :---: | :---: |
| 限额进度（百分比 + 进度条） | ✅ | ✅ | ✅ | ➖ | ✅ | ➖ | ➖ |
| 账户余额展示 | ➖ | ➖ | ➖ | ✅ | ➖ | ✅ | ✅ |
| 订阅 / 计划档位识别 | ✅ | ✅ | ✅ | ➖ | ✅ | ➖ | ➖ |
| 重置 / 续订时间倒计时 | ✅ | ✅ | ✅ | ➖ | ✅ | ➖ | ➖ |
| 重置卡（数量 / 到期） | ➖ | ✅ | ➖ | ➖ | ➖ | ➖ | ➖ |
| 「无限制」额度标识 | ➖ | ➖ | ➖ | ➖ | ✅ | ➖ | ➖ |
| 额度提升（Boost）展示 | ➖ | ➖ | ➖ | ➖ | ✅ | ➖ | ➖ |
| 多窗口指标（5 小时 / 周等） | ✅ | ✅ | ✅ | ➖ | ✅ | ➖ | ➖ |
| 首页卡片 | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| 状态栏常驻通知 | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| 桌面小组件 | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| 后台自动刷新 | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| 备份 / 恢复 | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |

> 余额类平台（AIHubMix、DeepSeek、OpenCode Zen）在状态栏与小组件上以**账户余额**作为主指标；限额类平台则展示**最高用量百分比**。
>
> **OpenCode Zen 余额说明：** Zen 目前没有官方余额接口，余额仅在网页控制台的 workspace 仪表盘可见。知余在内置 WebView 登录 `opencode.ai` 后复用其会话 Cookie 取数：优先解析仪表盘 SSR 页面，命中不到再回退到控制台内部的 SolidStart server function（`/_server`，参考 CodexBar 的实现）——无需任何后端，凭据仅留本机。其中 server function 的 id 为构建哈希，官方部署变更时可能需要同步更新；若官方上线 `GET /zen/v1/balance` 接口将迁移至更稳定的实现。

---

## 快速开始

```bash
# 克隆仓库
git clone https://github.com/Vectorking-50kg/zhiyu.git
cd zhiyu

# 构建 Debug APK
./gradlew assembleDebug

# 直接安装到已连接的设备
./gradlew installDebug
```

构建产物位于 `app/build/outputs/apk/`。环境要求：JDK 17、Android SDK 35。

**使用步骤：**
1. 打开 App → 进入「设置」。
2. 网页平台（Claude / Codex / Cursor / OpenCode Zen）点击「账号管理」在内置 WebView 中登录。
3. 托管平台（MiniMax / AIHubMix / DeepSeek）点击「API 密钥」粘贴对应 Key。
4. 返回首页即可查看额度；按需在「状态栏通知」中固定平台，或在主屏添加小组件。

---

## 技术架构

采用多模块 + 单向数据流（Repository → UseCase → ViewModel → Compose）的清晰分层。

```
app/                       应用入口、导航、依赖注入装配
core/
 ├─ domain/                领域模型（Platform、UsageInfo、UsageMetric…）与 UseCase
 ├─ network/               OkHttp + Gson，各平台用量 API 适配
 ├─ storage/               EncryptedSharedPreferences 加密存储、备份管理
 ├─ data/                  Repository、本地缓存、通知、刷新 Worker
 └─ ui/                    Material 3 主题、配色预设、通用组件
feature/
 ├─ auth/                  WebView 登录
 ├─ dashboard/             首页用量面板
 ├─ widget/                桌面小组件
 └─ settings/              设置、主题、备份、通知配置
```

**技术栈：** Kotlin · Jetpack Compose · Material 3 · Hilt · WorkManager · OkHttp · Gson · AndroidX Security Crypto。

---

## 隐私说明

- 所有账号会话与 API 密钥均通过 **AES‑256 加密**存储在设备本地。
- 应用**不部署任何后端**，用量数据直接由设备向各平台官方接口请求获取。
- 备份文件包含敏感凭据，请妥善保管、仅从可信来源导入。

---

<div align="center">

知余 · 让 AI 额度尽在掌握 ✨

</div>
