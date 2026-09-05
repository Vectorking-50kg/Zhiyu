# 额度查询可靠性更新

日期：2026-09-06。代码基线：`b97e8d4d0abea9433c9d5dc7e412e4eb2e9be250`。

本次参考 [CodexMeter e0c42ff](https://github.com/KyoMio/CodexMeter/tree/e0c42ff651b7f50813109c4be6b9708cd18c395c) 的认证校验、会话刷新和快照设计，在知余现有结构中实现，保留现有主题、Claude Design、Code Review、重置卡、续订日期与网页接入。

## 已实现

- Claude/Codex 解析从网络请求中分离；保留小数用量和原始重置时间，兼容空值、字符串数字、无效窗口及已知扩展字段。没有有效指标时不会生成伪造的 0% 正常卡片。
- HTTP 错误只保存安全类别与状态码：401、明确认证错误、普通 403、429、服务端错误分别处理。网络请求不记录凭据、正文或完整授权回调。
- 登录先验证额度与账号上下文，再提交凭据。Claude 身份使用订阅所在组织 UUID；Codex 使用平台 workspace/account ID，而非 ChatGPT 用户 ID。未知旧身份或换号需要确认。
- Claude 新增仅请求 `user:profile` 的可选 PKCE 授权；Codex 新增浏览器设备码授权。授权候选不提前保存，过期/取消/旧异步结果不会覆盖有效登录。
- OAuth 凭据加密保存；到期前续期，usage 返回认证失效时至多恢复一次。令牌轮换与同步持久化是有总超时的短事务，随后继续尊重调用取消。比较并交换防止旧刷新恢复已替换或清除的登录。
- 额度按平台与本地账号缓存。最后有效快照与最近失败原因分别保存；初次请求失败显示未知数据及原因，不显示 0%。旧缓存仅在可以唯一绑定账号时迁移，损坏缓存安全丢弃。
- 同账号重叠刷新共享结果，取消一个等待者不影响其他等待者。换号、密钥修改和备份恢复期间阻止旧请求回写；平台 Retry-After 未到期时不重复请求。
- 首页每 30 秒仅在前台重算时间显示，不增加网络轮询。通知与小组件共用同一缓存，刷新完成时更新系统表面。小组件用绝对重置时间，额度重置仍以平台新响应为准。
- Claude `extra_usage` 仅采用明确返回的百分比，不猜测缺失的金额单位。Codex `credits` 显示为不带货币符号的额外额度，独立于重置卡。
- 补回原仓库缺失的 Gradle 8.9 wrapper JAR，使 `./gradlew` 可直接工作。

## 验证

最终结果：110 项 Debug 单元测试通过，0 失败、0 错误、0 跳过；Demo、Debug、Release APK 均构建成功；Android Lint 为 0 错误、64 条警告，`git diff --check` 通过。Release 构建包含 OAuthCredential 与备份模型的 Gson 混淆保留规则。

测试覆盖解析边界、HTTP 状态、登录校验、账号切换、OAuth 回调与设备码、续期轮换、取消、存储失败补偿、旧备份模式切换、并发刷新、冷却期限、缓存迁移/损坏以及小组件的额度/余额映射。测试数据为固定样例或 MockWebServer 响应。

```sh
./gradlew :core:domain:testDebugUnitTest :core:storage:testDebugUnitTest \
  :core:network:testDebugUnitTest :core:data:testDebugUnitTest \
  :feature:auth:testDebugUnitTest :feature:settings:testDebugUnitTest :feature:widget:testDebugUnitTest
./gradlew :app:assembleDemo :app:assembleDebug :app:assembleRelease
./gradlew :app:lintDebug
```

本机可用 JDK 为 Android Studio 自带的 JBR 21。若默认 Java 版本不兼容，可先设置 `JAVA_HOME` 指向兼容 JDK。

本次不以真实账号完成第三方授权，离线测试和 APK 构建不能证明平台的内部端点与第三方授权方式长期可用。网页登录继续作为默认接入，OAuth 是显式选择的替代入口。Claude OAuth 不额外抓取网页登录的套餐信息；套餐标签可能缺省，额度仍按接口返回显示。

协议依据：[Codex 官方认证文档](https://learn.chatgpt.com/docs/auth)、[CodexBar CLI 文档](https://github.com/steipete/CodexBar/blob/main/docs/cli.md)、[Claude usage scope 相关记录](https://github.com/anthropics/claude-code/issues/16749)。这些来源用于理解兼容接入，不代表平台对知余作出支持承诺。
