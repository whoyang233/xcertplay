<div align="center">
  <img src="https://raw.githubusercontent.com/shilapi/xcertplay/refs/heads/master/asset/xcertplay_small.png" width="180" height="180" alt="xcertplay icon" />
  <h1>xcertplay 个人版</h1>
  <a href="README.md">English</a> | <a href="README.zh-CN.md">中文</a>
  <p>面向 Android 车机的 CarPlay 接收端，支持有线与无线连接。</p>
</div>

本分支是 [shilapi/xcertplay](https://github.com/shilapi/xcertplay) 的个人开发版本，维护于 [`personal-dev`](https://github.com/whoyang233/xcertplay/tree/personal-dev)。包含中文设置、切换应用时的尺寸处理优化、Wi-Fi 启动恢复、扫描抑制和可调信道策略。

## 下载与安装

**[下载已验证的 1.4.2B automotive 车载包 ZIP（低延迟档位版）](https://github.com/whoyang233/xcertplay/actions/runs/39354887281/artifacts/12235370209)** · [查看对应构建](https://github.com/whoyang233/xcertplay/actions/runs/39354887281) · [查看个人分支构建记录](https://github.com/whoyang233/xcertplay/actions?query=branch%3Apersonal-dev)

下载 GitHub Actions 产物需要登录 GitHub。直链无法打开时，进入对应构建页面，在底部 **Artifacts** 点击 `personal-apks-<commit>`，解压后选择适合设备的 APK。产物有保留期限；过期后可从后续成功构建下载，或在 Actions 的 **Android Build → Run workflow** 中选择 `personal-dev` 重新构建。

| 安装包 | 适用设备 |
| --- | --- |
| `automotive-debug.apk` | 车载 automotive 变体，对应上游 automotive 发布包。 |

最低要求为 **Android 9（API 28）**。MFi 认证需配置 CH341、原生 I2C、本地证书文件或远程服务中的一种。

| 项目 | 个人版 |
| --- | --- |
| 桌面名称 | xcertplay 个人版 |
| 应用包名 | `com.whoyang233.xcertplay` |
| 当前上游基础版本 / versionCode | `1.4.2` / `1402` |
| 当前测试版显示版本 | `1.4.2B` |

个人版可与上游 `com.shilapi.xcertplay` 同时安装，数据、授权和配对记录独立，**无需卸载原版**。首次安装个人版需重新配置、授权和配对；后续个人版保持包名和固定私钥，即可覆盖升级并保留数据。个人分支今后只构建 automotive APK，与上游 automotive 变体对应。连接 CarPlay 时使用其中一个应用，两个应用不能同时独占 Wi-Fi、USB 或 VPN 资源。

## 连接等待页与配置界面

[查看 8 个设置分类的完整滚动截图（含音质子页）](docs/SETTINGS_SCREENSHOTS.zh-CN.md)。

[页面配置核对、0 ms 实现、Web 删除范围与截图说明](docs/UI_BUFFER_SETTINGS.zh-CN.md)。

本轮界面改动先更新源码并执行测试，按要求暂不打包；上方下载链接仍是此前已验证的低延迟版本。Actions 手动运行时可选 `validation_only`，仅执行测试和 lint，跳过签名、APK 打包及 APK 上传。

等待页改为中文连接概览，显示当前步骤、四阶段进度和 Wi-Fi / 蓝牙 / CarPlay 数据通道检查。协议交换及调试日志默认收起，通过右上角「连接详情」查看；「连接设置」始终保留在顶部。大屏双栏、小窗口单栏滚动，字体跟随窗口并限制额外放大，避免旧的大色块和过大文字挤占 1080P 画面。连接失败时显示操作提示，仅在原有重试逻辑允许时提供重试按钮。

连接设置优先展示传输方式、MFi 认证方式与信道策略。Wi-Fi 高性能、启动恢复和 ADB 扫描抑制移至「无线连接高级选项」；热点状态收进「连接诊断信息」。长说明可展开查看，宽屏选项值靠右显示，窄屏或放大字体自动改为上下排列。音频焦点标明立即保存，媒体缓冲的完整限制说明可展开查看。重启 CarPlay / 退出应用仍固定在右上角，保存、校验和放弃修改的规则不变。

折叠仅影响显示，不会关闭任何已启用选项，也不改变 ADB 授权、连接流程、声道、导航音量或原有默认值。新界面逻辑为个人版实现。

## 比亚迪车辆数据（ADB）

车辆数据源改为参考 DiPlay 0.2.21 的只读本机 ADB/autoservice；旧 JSON 导入、标准 Android Car 属性后端及相关权限已移除。支持读取车速、挡位、电量、续航、剩余电量与充电状态，并接入现有 CarPlay 上报。四轮胎压/胎温仅用于诊断。上报默认关闭，旧来源的开关不会自动启用新后端。[查看字段、授权、协议、时效及兼容说明](docs/VEHICLE_PROFILES.zh-CN.md)。

## 启动画面比例与显示菜单

新握手等待窗口和系统栏尺寸稳定；屏幕物理基准改为实际显示尺寸的长/短边，避免历史横竖屏最大值混合。默认按当前窗口协商，新增默认关闭的「多区域快速切换（实验性）」和「恢复显示默认值」菜单，保留上游显示区域能力与个人版音频、Wi-Fi、ADB 数据功能。单区域画面与触控按同一比例适配。[查看修复范围、菜单及实车核对方法](docs/DISPLAY_STARTUP.zh-CN.md)。

## DiPlay 定向迁移与官方差异

官方基础仍为 1.4.2。本轮增加默认关闭的比亚迪通话弹窗屏蔽、软件 Opus 异常保护、iPhone 恢复播放时的音频焦点恢复，以及有线启动超时/已恢复会话保护；不引入临时系统栏。[查看官方与个人版对照、来源和使用限制](docs/PERSONAL_DIFF_AND_DIPLAY.zh-CN.md)。

## 主要功能

- Android 与 Android Automotive OS 上的有线/无线 CarPlay 接收端，支持 CH341、原生 I2C、本地 MFi 文件及远程认证。
- 语音、导航、音乐多通道音频，以及媒体缓冲和麦克风增益设置。
- 动态分辨率、HEVC 和安全区域；Web 主屏/仪表输出已从个人分支移除。
- 可选的车机位置与车辆数据回传，实际可用数据取决于车机接口、权限和配置。
- 保留上游实验性 CarPlay Ultra 触发能力；相关协议栈尚未完成，不能视作完整 Ultra 支持。

## 个人版改动

| 功能 | 当前行为 |
| --- | --- |
| 低延迟缓冲 | 主媒体缓冲支持 20、50–1000 ms，保留旧值与 100 ms 默认；小缓冲启动门槛和阻塞写入不超过容量，覆盖高质量默认音乐流。 |
| 比亚迪通话弹窗 | 默认关闭；Android 10+ 且有比亚迪通话组件时可用，需本机 ADB；仅前台已连接时抑制悬浮窗，后台/断开尝试恢复原始权限。 |
| 音频恢复与保护 | 软件 Opus 异常丢弃单帧并重置；iPhone 从暂停/停止恢复播放时按音频焦点开关重新申请焦点。 |
| 有线恢复 | 发出会话启动请求后 30 秒无会话则触发现有重试，USB 模式切换 10 秒后重查；延迟重试避开新恢复的健康会话。 |
| 比亚迪车辆数据 | 只读 ADB 读取六个基本量，复用并扩展 CarPlay 上报；胎压/胎温仅诊断。移除旧 JSON/Android Car 后端。 |
| 启动与显示比例 | 等待布局稳定，使用真实屏幕长/短边基准；默认单区域兼容，按需开启多区域快速切换，并可恢复显示默认值。 |
| Web 渲染 | 已删除服务器、网页、证书、远程音视频转发、配置入口和可选构建代码；旧开启状态不能改变本地输出。 |
| 上游 1.4.2 | 合入麦克风上行修复、音质菜单、视频缓冲复用和显示性能优化，保留个人设置、导航增益与 Wi-Fi 行为。 |
| 切换应用时的连接稳定性 | 非全屏按设置预留系统栏空间，忽略后台、失焦期间的临时尺寸变化，减少切出后立刻返回导致的分辨率重连；真实分屏和旋转仍按实际尺寸处理。 |
| 中文设置 | 默认简体中文，按类别显示配置；右上角固定“重启 CarPlay”和“退出应用”，窄窗口显示“重启”“退出”。 |
| 1080P 设置布局 | 设置页使用独立的紧凑密度与字号，自适应分类栏；分类和详情可分别滚动，长信道列表支持滚动选择。 |
| 设置应用方式 | 点击右上角“重启 CarPlay”校验、保存并应用修改；关闭设置放弃未保存的编辑。 |
| 音乐与导航声道 | 音乐与导航分别绑定媒体/导航用途，并支持 DiPlay 式 0–20 编号通道；导航软件音量 0%–200%，不改系统音量，不影响音乐、通话或 Siri。 |
| 关于 | 显示当前应用版本、Android 版本及 API 等级。 |
| Wi-Fi 启动恢复 | 自动热点首次启动失败后释放资源，尝试关闭再开启 Wi-Fi，并用新实例重试一次；系统拒绝开关 Wi-Fi 时仅重建热点，手动热点不自动重置。 |
| 信道策略 | Wi-Fi Direct 可选择系统自动、36 优先、149 优先或指定非 DFS 5 GHz 信道，可在请求被拒绝时回退自动。 |
| 扫描抑制 | 借鉴 DiPlay，提供默认关闭的连接期间暂停系统自动搜网选项，需本机 ADB 授权。 |
| 安装与升级 | 独立包名与固定 APK 签名，支持与原版共存及后续个人版覆盖升级。 |

Wi-Fi 启动恢复默认开启；取消或退出时，应用会先尝试恢复由自己关闭的 Wi-Fi，后续热点启动等待该恢复结束。能否直接开关 Wi-Fi 取决于 Android 版本和车机固件权限。上述连接稳定性与抗干扰改动仍需实车验收。

## 使用方法

1. 无线连接先通过蓝牙将 iPhone 与车机配对。
2. 视频流启动前，点击右上角「连接设置」按钮；也可用三指向下滑动打开设置。
3. 在「连接」配置 MFi 认证目标及有线/无线模式，按需调整音频、显示、窗口等分类。额外的单指边缘手势可在「启动与窗口」中开启。
4. 点击设置右上角 **重启 CarPlay** 保存并应用；无需滚动到底部。关闭设置会放弃未保存修改。
5. 使用所选认证方式所需的硬件、文件或服务，等待 CarPlay 建立连接。

## 音乐、导航声道与应用内音量

参考 DiPlay，新增「音乐音频通道（编号）」「导航音频通道（编号）」，可分别选择 0–20：0 自动沿用用途绑定；1–20 请求对应 Android/厂商 stream，14 在部分车机为导航通道，实际定义由固件决定。指定通道被拒绝或未初始化时释放并回退用途绑定。修改后点击「重启 CarPlay」保存应用，关闭设置放弃编辑。已有设置升级后编号默认 0，保持原有路由。

在「设置 → 音频 → 音乐与导航声道」分别选择输出用途：音乐默认「媒体声道」，导航默认「导航声道」。部分车机导航声道无声时，可把导航改绑到媒体声道。音质菜单中的声道数表示单声道/立体声，与输出绑定分开。这里请求的是 Android 音频用途，实际音频总线、扬声器与系统音量分组由车机固件决定。

「导航音量（应用内）」为 0%–200%，每档 5%；0% 静音，100% 保持原始音量，超过 100% 可能削波失真。应用对解码后的导航/提示音 PCM 做独立增益，在本机播放前生效，不调用系统音量接口。即使导航绑定媒体声道，也只调导航；音乐、通话、Siri 与麦克风增益保持各自处理。点击右上角「重启 CarPlay」保存应用，关闭设置放弃编辑。

高质量音乐流（102）与备用导航流（101）的默认类型分别识别为音乐、导航；通话、Siri 和显式类型优先。原有 AAOS「高级声道映射」仍控制普通流中 default/compatibility 的识别方式。手机已经混入音乐流的导航播报无法由应用单独分离。建议实车同时播放音乐并触发导航，分别测试 0%、50%、100% 和导航改绑媒体后的音量，确认音乐、通话及 Siri 不受影响。

## 媒体缓冲与低延迟档位

“设置 → 音频 → 主媒体缓冲（延迟调节）”支持 **0、20、50、100、150、200、250、300…1000 ms**（50 ms 后每档 50 ms）。升级保留原有已保存的缓冲值，新安装默认仍为 **100 ms**；选择不会立即改动正在运行的会话，点击右上角“重启 CarPlay”保存应用，关闭设置放弃编辑。

此值控制主媒体 PCM 的 AudioTrack 缓冲容量，不是声音与画面的同步偏移，也不是端到端延迟保证。较小容量可减少音频排队，但更易出现网络抖动时断续；先尝试 50 ms，再按实车效果选择 20 ms 或恢复较高值。Android 可能要求比请求值更大的最小缓冲，实际容量见音频日志的 `actualBufferFrames`。视频另有独立缓冲上限。0 ms 不做应用层预缓冲，使用系统最小 AudioTrack 容量，首个 PCM 采样帧写入后启动播放；Android 12+ 还尝试降低设备启动门槛，系统/驱动仍有最低延迟。

低容量的启动门槛和首批阻塞写入不会超过缓冲容量，避免 20 ms 轨道尚未开始消费就被写满卡住。缓冲配置同时覆盖 `media` 音频和已识别的 `102/default` 高质量音乐；通话、Siri 和导航角色继续使用各自路径。

## Wi-Fi 扫描抑制与信道策略

信道策略位于「设置 → 连接」，ADB 扫描抑制位于该页的「无线连接高级选项」。保存并重启 CarPlay 后应用。**信道请求和扫描抑制仅用于 Android 10 及以上的 Wi-Fi Direct**；Android 9 使用 LocalOnlyHotspot。

### 信道策略

| 策略 | 首次请求 |
| --- | --- |
| 系统自动（默认） | 系统选择 5 GHz 信道。 |
| 低信道优先 | 36。 |
| 高信道优先 | 149。 |
| 指定信道 | 36、40、44、48、149、153、157、161 或 165。 |

信道请求通过 Android 公开接口完成，**无需 ADB**。是否接受由固件、地区法规和 Wi-Fi 共存限制决定；系统也可能覆盖请求，以设置中“最近一次热点实际信道”和连接日志为准。策略不改变信道宽度，已有连接不会主动换信道。

“请求被拒绝时回退自动”默认开启，仅在系统明确拒绝创建指定信道分组时再尝试一次自动 5 GHz。忙碌、超时、权限拒绝和取消不会触发此信道回退；明确的信道拒绝也不会触发 Wi-Fi 关开恢复。自动热点仍由系统选信道；手动热点需要在车机系统热点设置中改信道，应用内填写信道不会替系统切换。

### 扫描抑制

“连接期间暂停系统自动搜网”**默认关闭**。它借鉴 [DiPlay 的实现](https://github.com/shihabal3amri/DiPlay/blob/1b1ac5f27986ce83b6d17fdd8c4d1be852fbbb70/shared/src/main/java/com/shilapi/xcertplay/network/WifiScanPause.kt)，通过已授权的本机 ADB 调用系统 Wi-Fi 自动加入开关，针对部分单射频车机后台搜网导致的丢包。

先停车，在系统中开启传统网络 ADB（本机 `127.0.0.1:5555`），点击“授权 / 检查本机 ADB”，允许系统调试确认框，再启用该选项。仅支持 TLS 配对码的无线调试暂不适用；后台连接和恢复不主动弹授权框。ADB 已授权仍不代表固件一定支持此开关。

应用会在更改前持久记录恢复标记，最后一个控制器关闭或报告连接失败后尝试恢复自动搜网；恢复失败每 30 秒重试，下次打开应用也会恢复。强制停止后不能立即运行恢复；若 ADB 已关闭，可重新开启 ADB 后打开应用，或重启车机。Android 13+ 能查询原状态时，会保留其他工具已关闭的自动加入；较老固件无法查询原值，应在原本允许自动加入的情况下使用。

暂停自动搜网可能影响车机自动连接手机热点，不能阻止其他应用的主动扫描，也不能消除外部射频干扰。建议先对比自动、36 和 149，再单独测试扫描抑制，记录实际信道及断联时间，判断哪项对本车有效。

## Web 渲染已移除

个人分支已删除网页服务器、TLS 证书实现、HTML、NanoHTTPD/WebSocket 依赖、远程音视频转发接口、Web 配置页和构建开关。原先仅在网页功能下启用的仪表预览浮窗一并删除。本地主屏的解码、触控、启动尺寸和音频声道仍保留。旧 `web_display_*` 偏好不再读取，不能改变本地显示或启用网络输出；原有 `xcertplay.webDisplay` 参数不再提供功能。Web 可选测试任务已退役，改为核对相关类和依赖确实缺席。

## 视频缓冲与 0 ms

「显示与视频 → 视频缓冲上限」提供兼容模式、**即时显示（0 ms）、20、50、100、150、200、250、300 ms**。默认兼容原有 8 帧输入队列和逐帧显示。选择较低档位后，按帧率限制解码前排队，并且仅在已有更新解码输出时跳过超时旧画面；不会直接丢弃压缩参考帧，最后一帧/静止画面仍立即显示。0 ms 不主动延迟，尽快显示最新已解码画面；它不是声画同步偏移，也不能消除系统/网络/解码器延迟。较小值可能影响流畅度，可从 50 ms 逐步尝试。与音频分开保存，右上角「重启 CarPlay」保存应用；关闭设置放弃。恢复显示默认值也恢复视频缓冲兼容模式。

## 固定 APK 签名

个人版 debug 与 release 使用同一把专用 RSA 私钥，公开证书 SHA-256 固定在 [`signing/personal-certificate.sha256`](signing/personal-certificate.sha256)。Gradle 打包前和 CI 上传前验证签名身份。**缺少私钥或证书不匹配时，正常构建直接失败，不会生成临时密钥或退回默认 debug 签名。**

GitHub Actions 使用仓库 Secret **`PERSONAL_ANDROID_SIGNING`**，值为离线签名备份中 `github-secret.txt` 的完整 JSON，包含 PKCS#12 的 Base64、密码和别名。在仓库 Settings → Secrets and variables → Actions 保存一次，后续复用同一份。私钥不在源码或公开下载产物中，备份应由维护者保管。

APK 签名私钥与下文 MFi 认证私钥用途不同，不能混用。

## 构建

### 环境要求

- 推荐 JDK 25，与 CI 保持一致；启动 Gradle 至少需要 JDK 17，daemon 由 toolchain 使用 Java 25。
- Gradle Wrapper 9.5.0、Android SDK Platform 37、Build Tools `36.0.0`、NDK `28.2.13676358`。
- CI 签名与校验脚本需要 Python 3。
- 实机验证需要 iPhone 和兼容车机；USB/CH341 或原生 I2C 认证还需要对应的 MFi 硬件。

### GitHub Actions

在 **Android Build → Run workflow** 选择 `personal-dev`，保持 `bootstrap_unsigned` 关闭。正常构建使用固定私钥，执行单元测试、Lint、打包与签名校验，上传：

| 产物 | 内容 |
| --- | --- |
| `personal-apks-<commit>` | 已签名 automotive APK 和包名/签名验证信息。 |
| `validation-reports-<commit>` | 单元测试、Lint 报告和设置布局截图。 |
| `unsigned-apks-<commit>` | 仅供显式初始化或无密钥 PR 验证的未签名输入，不能直接安装。 |

### 本地构建

先将以下环境变量指向离线备份中的同一把 PKCS#12 私钥，`ANDROID_KEYSTORE_PATH` 使用绝对路径：

| 环境变量 | 内容 |
| --- | --- |
| `ANDROID_KEYSTORE_PATH` | `xcertplay-personal.p12` 的绝对路径。 |
| `ANDROID_KEYSTORE_PASSWORD` | 密钥库密码。 |
| `ANDROID_KEY_ALIAS` | 备份中的别名 `xcertplay-personal`。 |
| `ANDROID_KEY_PASSWORD` | 私钥密码。 |

macOS / Linux：

```bash
./gradlew :shared:testDebugUnitTest :common:testDebugUnitTest :common:lintDebug :automotive:lintDebug :automotive:assembleDebug
```

Windows PowerShell：

```powershell
.\gradlew.bat :shared:testDebugUnitTest :common:testDebugUnitTest :common:lintDebug :automotive:lintDebug :automotive:assembleDebug
```

签名 release 构建同样需要上述固定私钥：

```bash
./gradlew :automotive:assembleRelease
```

无密钥时仅可显式添加 `-Pxcertplay.allowUnsigned=true` 生成未签名 debug 输入；CI 对应手动 `bootstrap_unsigned` 选项或无密钥 PR 验证。该选项不会生成新的签名密钥，也不会使未签名 APK 变得可安装。

## 验证状态与问题反馈

2026-10-21 的[界面/缓冲仅验证任务](https://github.com/whoyang233/xcertplay/actions/runs/39561641865)来自源码 `3b228560ebb64c98b69d7d0fc72c24344102cfe6`：**418 项测试全部通过，无失败、错误或跳过；Lint 无错误，保留 common 82 / automotive 3 条警告。** 覆盖实际设置页、视频所有档位的草稿/保存/重载、音频 0 ms 首帧启动、解码输出追赶与静止帧保留、单帧队列背压及 Web 类/依赖缺席。签名、APK 打包、APK 校验和上传步骤均明确跳过。[页面截图与配置说明](docs/UI_BUFFER_SETTINGS.zh-CN.md) · [完整验证报告和截图](https://github.com/whoyang233/xcertplay/actions/runs/39561641865/artifacts/12328316122)。截图为 Android 13 / API 33 的 1080P 测试预览，不能替代实车延迟或固件兼容验证。

以下为此前可下载 APK 的历史验证记录；本轮未生成新 APK。

2026-10-20 的[已验证构建](https://github.com/whoyang233/xcertplay/actions/runs/39354887281)来自提交 `5b319fa45bf87afd47b8b5646933492341aea95d`：**396 项单元测试通过，无失败、错误或跳过；Lint 无错误，仍有警告（common 83、automotive 3）。** 其中比亚迪来源/迁移 25 项，覆盖 CAN/CANFD、单位换算、非法/部分读数、固件目录缓存、失败恢复、时效、订阅、诊断隔离、退出取消及旧来源迁移；iAP2 测试核对了剩余电量与充电状态的实际参数编码。该轮新增 8 项执行用例，核对旧缓冲值保留、低档位保存/取消、大字体菜单、小容量启动及阻塞写入；既有通话弹窗、Opus、焦点和有线恢复回归继续通过。导航独立音量、音频绑定、麦克风、Wi-Fi 恢复及扫描抑制回归通过；设置布局测试覆盖多 DPI 的 1080P、150% 字体窄窗口和矮信道列表。[验证报告](https://github.com/whoyang233/xcertplay/actions/runs/39354887281/artifacts/12235570302)包含测试页面截图。实车 ADB、字段可用性、导航采用、安装和连接效果仍需验收。

上述历史构建只生成 automotive APK，已核验个人包名、固定签名、Provider/自定义权限及版本 1.4.2B / 1402；包含启动尺寸策略、触控适配、编号音频绑定和比亚迪 ADB 后端。旧车辆 JSON 资源与解析/Android Car 属性后端不包含在包中；网页页面、服务器、证书实现和 NanoHTTPD/NanoWSD 仍排除。当时可选网页任务通过 88 项 common 测试。当前源码已经完整移除该模块与可选任务；本轮只测试和生成界面截图，暂不生成 APK。

实机重点验证：非全屏快速切出/返回、分屏与旋转、设置保存/取消、首次热点失败后的恢复，以及不同信道与扫描抑制选项。反馈请附车机型号、Android/API、应用与 iOS 版本、热点模式、实际信道、复现步骤、断联时间及相关日志。

个人版通常的日志路径为：

```text
/sdcard/Android/data/com.whoyang233.xcertplay/files/logs/xcertplay.log
```

外部应用目录不可用时会回退到应用内部目录，以启动日志中的实际路径为准。Android 可能限制文件管理器读取此目录。请检查日志内容后，通过本仓库 [Issues](https://github.com/whoyang233/xcertplay/issues) 反馈。

转接板：[CH341-to-MFI](https://github.com/shilapi/ch341-to-mfi-chip)

## 本地 MFI 文件

在 「连接 → 认证方式」 中选择 「本地文件」，然后通过两个 「选择」
按钮使用 Android 系统文件选择器选择证书和私钥。当前支持 DER PKCS#7 证书（`.p7b`）
及与之匹配的、未加密 DER PKCS#8 私钥（`.pk8`）。应用会在开始连接手机前校验两者是否
匹配，并在 MFI 重连时重新读取文件。

建议把私钥放在受保护的位置。应用不会把证书或私钥复制到偏好设置，只会保存 Android
授予的持久读取权限和文档 URI。

## Remote MFI 功能

Remote MFi 客户端把远程服务当作一块 MFi 芯片远程调用，抑或是采用 BAA 认证，通过远程进行认证免去了本地连接 MFI 芯片进行认证的流程。

### 端点

| Method | Path | 用途 | Request body | Success response | 失败 response |
| --- | --- | --- | --- | --- | --- |
| `GET` | `/mfi/certificate` | 获取 MFI 芯片版本、证书类型和证书内容，客户端首次调用后缓存 | 无 | 证书 JSON | `{"detail":"..."}` |
| `POST` | `/mfi/sign` | 对 challenge 签名 | `{"challenge":"...","requestId":"..."}` | `{"signature":"..."}` | `{"detail":"..."}` |
| `POST` | `/mfi/reset` | 请求重置远程 MFI 芯片 | `{}` | `{"detail":""}` | `{"detail":"..."}` |

（可选）采用标准 Bearer Authentication 进行验证。

**上游记录仅测试了 BAA Authentication；个人版仍需按目标设备验证认证配置。**

## 工程结构

| 路径 | 用途 |
| --- | --- |
| `common/` | 共用宿主界面、中文设置、持久化和应用资源。 |
| `mobile/` | 一般 Android 车机目标。 |
| `automotive/` | 原生 Android Automotive OS 目标。 |
| `shared/` | CH341、I2C、MFi、iAP2、VPN、AirPlay、媒体及 Wi-Fi 实现。 |
| `ci/` | 固定签名配置读取、APK 包名与证书验证脚本。 |
| `signing/` | 公开证书指纹，不含私钥。 |

## 致谢与许可证

本项目基于 [shilapi/xcertplay](https://github.com/shilapi/xcertplay)。感谢 [LIVI](https://github.com/f-io/LIVI) 提供参考、[showcase](https://github.com/amineross/showcase) 提供 BAA 认证参考。

车辆数据读取另参考 DiPlay 提交 `6d3b8a8ce927cb4b115b8c3881f17619433e93b1`（0.2.21），见[车辆数据来源说明](docs/VEHICLE_PROFILES.zh-CN.md)。扫描抑制、ADB 协议和相关测试借鉴 [DiPlay](https://github.com/shihabal3amri/DiPlay) 提交 `1b1ac5f27986ce83b6d17fdd8c4d1be852fbbb70`，保留 GPL-3.0 来源说明。本次移植未引入其仪表控制或 AGPL 界面代码。

本项目采用 [GNU General Public License v3.0](LICENSE) 许可。

本轮通话弹窗租约/辅助进程与有线看门狗移植自 DiPlay `6d3b8a8ce927cb4b115b8c3881f17619433e93b1`（GPL-3.0），Opus 和音乐恢复按本项目结构适配，详见[迁移说明](docs/PERSONAL_DIFF_AND_DIPLAY.zh-CN.md)。
