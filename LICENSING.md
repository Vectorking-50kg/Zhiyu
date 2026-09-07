# 许可说明

知余采用 **[MIT License](LICENSE)**。

Copyright (c) 2026 Vectorking-50kg

MIT 允许使用、复制、修改、合并、发布、分发、再许可和商业使用；分发本项目或其重要部分时，需要保留版权声明和完整许可文本。软件按许可中规定的方式按原样提供，不附带保证。完整条款以根目录 `LICENSE` 为准，APK 同时包含一致的 [zhiyu.txt](app/src/main/assets/licenses/zhiyu.txt)。

## 第三方代码与资源

本项目的 MIT 许可不替代第三方作品的原始许可。下面列出已有明确来源记录的主要组件；引入或升级依赖时，应同步核对它们的传递依赖及适用的版权、许可和 `NOTICE`。


| 项目                                          | 用途                                        | 原始许可与来源                                                                                                                                                                                                                                 |
| ------------------------------------------- | ----------------------------------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Miuix `0.3.4`                               | Miuix 控件、圆角及主题                            | [Apache-2.0](app/src/main/assets/licenses/miuix.txt)、[来源](app/src/main/assets/licenses/miuix-source.txt)                                                                                                                                |
| MaterialKolor `2.0.0`                       | 动态配色                                      | [MIT](app/src/main/assets/licenses/materialkolor.txt)、[来源](app/src/main/assets/licenses/materialkolor-source.txt)                                                                                                                       |
| Material Color Utilities                    | MaterialKolor 内含的配色算法                     | [Apache-2.0](app/src/main/assets/licenses/materialkolor-color-utilities.txt)；与 MaterialKolor 的 MIT 声明一并保留                                                                                                                               |
| Colormath `3.6.0`、Poko Annotations `0.17.1` | 配色传递依赖                                    | [Colormath MIT](app/src/main/assets/licenses/materialkolor-colormath.txt)、[Poko Apache-2.0](app/src/main/assets/licenses/materialkolor-poko-annotations.txt)、[署名与来源](app/src/main/assets/licenses/materialkolor-transitive-sources.txt) |
| Lobe Icons                                  | 平台品牌图标                                    | [MIT](app/src/main/assets/licenses/lobe-icons.txt)、[版本与来源记录](app/src/main/assets/licenses/lobe-icons-source.txt)                                                                                                                        |
| Material Symbols                            | 通用图标                                      | [Apache-2.0](app/src/main/assets/licenses/material-symbols.txt)、[来源](app/src/main/assets/licenses/material-symbols-source.txt)                                                                                                          |
| CodexMeter                                  | 认证校验、会话续期与快照设计参考                          | [MIT](app/src/main/assets/licenses/codexmeter.txt)、[来源](app/src/main/assets/licenses/reference-projects-source.txt)                                                                                                                     |
| CodexBar                                    | OpenCode Zen 余额读取相关的源码参考与适配，并非 Gradle 库依赖 | [MIT](app/src/main/assets/licenses/codexbar.txt)、[来源](app/src/main/assets/licenses/reference-projects-source.txt)                                                                                                                       |


Kotlin、kotlinx.coroutines、AndroidX、Dagger/Hilt、OkHttp 和 Gson 等基础依赖的主要许可为 Apache-2.0，具体版本以 [Gradle 版本目录](gradle/libs.versions.toml)及各模块构建声明为准。本表不声称已穷举最终 APK 的所有传递依赖。
