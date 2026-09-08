<div align="center">
  <img src="assets/readme/logo.png" alt="App 图标" width="100" />
  <h1>知余</h1>
</div>

<div align="center">

简体中文 | [English](README_en-US.md)

支持多平台 AI 用量和账户余额监控的 Android 应用，在应用、桌面小组件和常驻通知中随时查看你的 AI 额度

[![Android](https://img.shields.io/badge/Android-8.0%2B-3DDC84?logo=android&logoColor=white)](https://developer.android.com) [![Kotlin](https://img.shields.io/badge/Kotlin-2.1.0-7F52FF?logo=kotlin&logoColor=white)](https://kotlinlang.org) [![Compose](https://img.shields.io/badge/UI-Jetpack_Compose-4285F4?logo=jetpackcompose&logoColor=white)](https://developer.android.com/jetpack/compose) [![License: MIT](https://img.shields.io/badge/License-MIT-green.svg)](LICENSE) [![GitHub Release](https://img.shields.io/github/v/release/Vectorking-50kg/Zhiyu?label=Release&logo=github)](https://github.com/Vectorking-50kg/Zhiyu/releases/latest)

</div>

<div align="center">
  <img src="assets/readme/screenshots/blank.png"
       width="160" alt="空白页">
  <img src="assets/readme/screenshots/home.png"
       width="160" alt="应用首页">
  <img src="assets/readme/screenshots/detail.png"
       width="160" alt="详情页">
  <img src="assets/readme/screenshots/account.png"
       width="160" alt="账户页">
  <img src="assets/readme/screenshots/account-setting.png"
       width="160" alt="账户设置页">
</div>


## 声明

知余是独立的第三方项目，与所支持的平台不存在任何官方的隶属或背书关系。

## 获取应用

从 GitHub Releases 页面获取最新 APK：[Releases](https://github.com/Vectorking-50kg/Zhiyu/releases)

- **系统要求：** Android 8.0（API 26）及以上。

## 主要功能

- **多平台、多账号概览**：统一查看套餐额度和账户余额，按类型筛选；用深浅两层进度条对照用量与周期时间。
- **独立账户管理**：添加、搜索和重命名账户，分别控制概览显示、后台监控、提醒与状态栏固定；暂停监控后保留缓存。
- **通知与桌面小组件**：无需打开 App 即可查看用量；额度达到 80% / 95%、检测到额度重置或网页登录失效时提供提醒。
- **后台刷新与失败处理**：可选 15 / 30 / 60 分钟刷新间隔；区分网络失败、限流和登录失效，失败时保留上次有效数据及更新时间。
- **两种界面风格**：切换 Material / Miuix，支持浅色、深色、跟随系统、纯黑背景、系统动态配色和自选颜色。
- **本地凭据与备份**：加密保存应用使用的登录凭据和 API 密钥，通过 JSON 文件导出、导入账户连接与配置。

## 支持的平台

| 平台 | 接入方式 | 当前可读取的信息 |
| --- | --- | --- |
| **ChatGPT** | 网页登录；可选设备码授权 | Codex 相关的 5 小时与周限额、附加窗口、Code Review、重置卡、续订信息、额外额度 |
| **Claude** | 网页登录；可选 OAuth 授权 | 5 小时与周限额、模型分类窗口、Claude Design、额外用量百分比 |
| **Cursor** | 网页登录 | 本周期用量、Auto 用量、API 用量与套餐信息 |
| **OpenCode Zen** | 网页登录 | 账户余额 |
| **MiniMax** | Token Plan 对应的 API Key | 5 小时与周限额、无限制标识、额度提升（Boost） |
| **AIHubMix** | API Key（令牌） | 账户余额、累计消费、累计请求次数 |
| **DeepSeek** | API Key | 可用余额、赠送余额、充值余额 |

实际展示取决于账户权限、订阅类型及平台返回的字段。表中列出的是已实现的解析能力，不代表所有账户都会返回全部指标；

部分平台接入依赖网页登录后的内部接口或控制台页面，平台调整接口后可能需要更新应用。

## 开始使用

1. 打开应用，点击「添加第一个账户」；也可以在「账户」页添加更多账户。
2. 选择平台。ChatGPT、Claude、Cursor、OpenCode 使用网页登录；MiniMax、AIHubMix、DeepSeek 填写对应平台的密钥。
3. 登录或密钥通过校验后，返回「概览」查看额度与余额，点击「查看详情」查看各个窗口与重置时间。
4. 在「账户」中按需开启监控、提醒和状态栏固定；在「设置」中调整外观、刷新间隔和全局通知。
5. 如需桌面展示，通过系统的小组件选择器添加知余小组件。

## 数据与隐私

- **网络请求**：登录、授权和额度查询会向对应平台及其认证服务发送必要信息。应用不会将账号凭据或用量数据上传到服务器。
- **本地存储**：应用凭据存储使用 AndroidX Security Crypto 加密；偏好、用量缓存与 WebView 网站数据分别由对应的本地存储机制管理。
- **备份文件**：导出的备份文件是包含登录凭据与密钥的**未加密 JSON 文件**。请妥善保存，仅从可信来源导入，不要附在 Issue、截图或公开仓库中。
- **反馈问题**：提供系统版本、App 版本、平台名称和复现步骤即可；请移除 Cookie、Token、API Key、完整授权链接及可识别账户的信息。

## 本地构建

建议使用 Android Studio，配置 JDK 17 和 Android SDK Platform 35。仓库包含 Gradle Wrapper，无需另行安装 Gradle。

```bash
git clone https://github.com/Vectorking-50kg/Zhiyu.git
cd Zhiyu

# 构建调试安装包
./gradlew :app:assembleDebug

# 安装到已连接并开启 USB 调试的设备
./gradlew :app:installDebug
```

开发验证可运行：

```bash
./gradlew testDebugUnitTest :app:lintDebug
```

## 项目结构

```text
app/                  应用入口、导航、概览 / 账户 / 设置 / 外观页面
core/
  domain/             领域模型与数据仓库接口
  network/            平台接口、解析、OAuth 与网络错误处理
  storage/            凭据、账户配置、外观偏好与备份
  data/               数据仓库、缓存、后台刷新与通知
  ui/                 当前主题、图标和通用 Compose 组件
feature/
  auth/               WebView 登录与授权界面
  widget/             桌面小组件
```

## 反馈与贡献

欢迎通过 [Issues](https://github.com/Vectorking-50kg/Zhiyu/issues) 反馈问题或提出需求。

## 许可与致谢

本项目采用 **[MIT License](LICENSE)**，允许使用、修改、商业使用和再分发，并需保留版权及许可声明。

第三方代码与资源继续适用各自的原始许可证，详见 [许可说明](LICENSING.md) 和 [随包许可文本](app/src/main/assets/licenses)。

感谢 [Miuix](https://github.com/miuix-kotlin-multiplatform/miuix)、[MaterialKolor](https://github.com/jordond/MaterialKolor)、[Lobe Icons](https://github.com/lobehub/lobe-icons)、[Material Symbols](https://github.com/google/material-design-icons)、[CodexMeter](https://github.com/KyoMio/CodexMeter) 项目。
