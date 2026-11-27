# Antigravity（Google AI）接入

知余通过 Google 授权读取 Antigravity 额度，并在原有账户、后台监控、通知和桌面小组件中展示。没有引入新的运行时依赖。

## 登录与续期

1. 添加账户时选择 Antigravity，默认进入知余内置的 Google 授权网页。
2. 选择用于 Antigravity 的 Google 账号并授权。
3. 知余自动接收授权结果并验证账号和额度，全部成功后才加密保存并完成连接。

授权使用 Antigravity 分发的 installed-app OAuth 注册。知余为每次登录生成随机 `state` 和 PKCE 校验值。应用内流程在 WebView 导航前截获 `http://127.0.0.1:8089/oauth2callback`，不绑定或访问本机端口。只接受匹配地址、路径及单一 `state` 的授权响应；`shouldOverrideUrlLoading`、主框架 `onPageStarted` 和错误回调共同兜底。重复回调不重复换取令牌，旧页面迟到回调不能影响新的授权，拒绝授权和校验失败会显示可重试的错误。

OAuth 注册的 Client ID / client secret 通过构建参数注入：正式发布使用 GitHub Actions Secrets，本地构建使用已忽略的 `local.properties`、Gradle 属性或环境变量，名称见 [构建说明](README.md#本地构建)。仓库不保存实际注册值；它们属于 installed-app 注册配置，不是用户的登录令牌。客户端仍需把这些配置打包进 APK，用户授权取得的 access / refresh token 则仅保存在设备的加密存储中。

若 Google 阻止当前设备的内嵌登录，用户可手动选择「改用浏览器」。只有这条备用流程会使用同手机浏览器和随机本机端口监听，退出、重新授权或五分钟超时会关闭监听。

后台通过 `https://oauth2.googleapis.com/token` 续期，保留没有轮换的 refresh token，并持久化返回的新 token；遇到 401 仅续期重试一次。权限不足、限流、服务异常不会触发反复登录。撤销授权后，在「账户设置」重新连接。身份使用 Google userinfo 返回的稳定账号 ID，切换账号沿用现有确认和缓存清理机制。

Google 的[原生应用 OAuth 文档](https://developers.google.com/identity/protocols/oauth2/native-app)说明了 PKCE、离线访问和续期，其[OAuth 政策](https://developers.google.com/identity/protocols/oauth2/policies#secure-browsers)限制内嵌浏览器，不能保证所有账号、风控或验证步骤都能在 WebView 完成。其 loopback 回调已不适用于 Android 类型的 OAuth 注册；本实现使用上游 Antigravity 桌面客户端注册，依赖 Google 继续接受该注册的回调和权限。普通自建 Android Client ID 不能直接替换它。应用进程被系统结束时，需要重新发起授权。

## 额度口径

接口使用 TokenTracker 当前实现中的 `daily-cloudcode-pa.googleapis.com`，避免无前缀主机可能返回的满额占位数据。

| 接口 | 用途 |
| --- | --- |
| `POST /v1internal:retrieveUserQuotaSummary` | 读取 `gemini-5h`、`gemini-weekly`、`3p-5h`、`3p-weekly` 明确标识的额度窗口 |
| `POST /v1internal:fetchAvailableModels` | 汇总接口要求订阅、不可用或结构无法识别时，回退读取各模型的 `quotaInfo` |
| `POST /v1internal:loadCodeAssist` | 可选读取 `paidTier` / `currentTier` 中的订阅名称；失败不丢弃已获取的额度 |

`gemini-*` 显示为 Gemini 模型组，`3p-*` 显示为 Claude and GPT 模型组，与平台用量页保持一致。`remainingFraction` 转换成已用百分比：`(1 - remainingFraction) × 100`。只接受有效的 0–1 值，缺失和异常值不当作 0% 已用。保存平台提供的绝对重置时间，以便缓存、小组件和通知随时间更新。

模型接口没有提供窗口类型，因此按原模型名称分别显示，不按剩余额度大小推断「每周／5 小时」。不推测 Google AI Pro、Ultra 或免费套餐，也不将 Antigravity 额度当作 Gemini App、AI Studio 或 Google One 全部权益。上述额度接口属于第一方内部接口，未来字段和授权策略可能变化。

## 来源与验证

- 额度接口、订阅回退和 OAuth 续期参数参考 [TokenTracker `usage-limits.js`](https://github.com/xiufengsun/TokenTracker/blob/a3f10f2289c24f6612f26b8becbba4ff797c7754/src/lib/usage-limits.js)，固定参考提交 `a3f10f2289c24f6612f26b8becbba4ff797c7754`；MIT 许可已随包保留。
- 应用内回调拦截、硬件渲染、焦点与 WebView 配置参考 [CodexMeter `WebViewAuthScreen.kt`](https://github.com/KyoMio/CodexMeter/blob/7e9a4ce350717a0efc6186654ee0b3e206ce8878/app/src/main/java/com/kmnexus/codexmeter/ui/auth/WebViewAuthScreen.kt)，参考提交 `7e9a4ce350717a0efc6186654ee0b3e206ce8878`；已有 MIT 许可与来源记录已更新。
- 授权地址和近期所需 `aicode` scope 的协议事实另核对了 [Antigravity-Manager OAuth 模块](https://github.com/lbjlaq/Antigravity-Manager/blob/148838519e12b2f27aec227a4e789e9bacde2f49/src-tauri/src/modules/oauth.rs)。没有复制该项目代码；本项目独立实现 Kotlin 授权及回调。
- 自动化测试覆盖汇总和各模型解析、订阅名称、异常及缺失字段、令牌刷新和轮换、身份绑定、PKCE / state、回调取消和超时、重定向保护、401 / 403 / 429 / 5xx 分类。
- Android API 35 模拟器已验证内置 WebView 打开 Google 官方「继续使用 Google Antigravity」登录页；模拟授权拒绝的真实 WebView 导航会显示明确错误及「重新授权」，不访问回环端口或停在等待状态。Android 回归测试另外覆盖校验失败、重复回调、取消和旧页面迟到回调。演示版本提供 Gemini、Claude and GPT 两组共四个额度窗口的样例数据。
- 真实 Google AI Pro / Ultra 订阅的授权、首次额度和长期后台续期仍需用户账号验收。模拟响应验证不能替代真实订阅验收。
