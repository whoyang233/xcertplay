<div align="center">
  <img src="https://raw.githubusercontent.com/shilapi/xcertplay/refs/heads/master/asset/xcertplay_small.png" width="180" height="180" alt="xcertplay icon" />
<h1><strong><font size="6">xcertplay</font></strong></h1>
  <a href="README.md">English</a> | <a href="README.zh-CN.md">中文</a>
  <p>xcertplay 是面向 Android 车机的 CarPlay 接收端项目。支持通过 CH341 I2C 桥接到 MFi 芯片，亦可通过板载 I2C 控制器直连，支持 CarPlay 有线和无线连接。</p>
</div>

## 个人版安装标识与固定签名

个人分支的 mobile / automotive 都使用独立应用 ID **`com.whoyang233.xcertplay`**，桌面名称为 **xcertplay 个人版**。可与上游 `com.shilapi.xcertplay` 同时安装，应用数据、授权和配对记录相互独立；首次安装个人版需重新设置、授权和配对。连接 CarPlay 时使用其中一个应用，两个应用不能同时独占同一 Wi-Fi/USB/VPN 资源。mobile 与 automotive 是同一应用的两种安装变体，按车机系统选用。

从此版本开始，个人版 debug 和 release 均使用同一把专用 RSA 私钥。公开证书 SHA-256 固定在 `signing/personal-certificate.sha256`，Gradle 打包前与 CI 上传前均验证该指纹。**缺少签名或证书不匹配时，正常构建直接失败，不会生成临时私钥或退回 Android 随机 debug 签名。** `1.4.0B` 对应上游 `1.4.0`；后续保持同包名、同私钥即可覆盖更新并保留个人版数据。

GitHub Actions 只需一个仓库 Secret：`PERSONAL_ANDROID_SIGNING`，值为随离线签名备份提供的 `github-secret.txt` 全文（JSON，内含 PKCS#12 的 Base64、密码与别名）。在仓库 Settings → Secrets and variables → Actions 中保存一次，后续构建复用同一份。不要重新生成私钥或把私钥文件提交到 Git。密钥及配置备份仅交付给仓库持有人；仓库仅记录公开指纹和读取逻辑。

本地构建设置 `ANDROID_KEYSTORE_PATH`、`ANDROID_KEYSTORE_PASSWORD`、`ANDROID_KEY_ALIAS`、`ANDROID_KEY_PASSWORD`，均指向备份的同一私钥；路径为绝对路径。仅在首次接入 Secrets 前或无私钥的 PR 验证时，显式 `-Pxcertplay.allowUnsigned=true` 可生成未签名 debug 输入；CI 的手动 `bootstrap_unsigned` 选项也是这一用途，产物明确标记 `unsigned-apks`，不能直接安装。正常自动构建和 release 始终要求固定私钥；最终交付的安装包必须与固定指纹匹配。

## 个人测试版：扫描抑制与信道策略

在「设置 → 连接」中新增以下选项，保存并重启 CarPlay 后应用；版本仍对应上游 1.4.0，应用显示 1.4.0B。

- **连接期间暂停系统自动搜网**：默认关闭，仅 Wi-Fi Direct 使用。借鉴 [DiPlay 的实现](https://github.com/shihabal3amri/DiPlay/blob/1b1ac5f27986ce83b6d17fdd8c4d1be852fbbb70/shared/src/main/java/com/shilapi/xcertplay/network/WifiScanPause.kt)，通过已授权的本机 ADB 调用系统 Wi-Fi 自动加入开关，针对部分单射频车机后台搜网导致的丢包。不会关闭 Wi-Fi，也不能阻止所有扫描或消除外部射频干扰。可能影响车机自动连接其他热点。
- **本机 ADB 授权检查**：先停车，在车机系统中开启传统网络 ADB（127.0.0.1:5555），点击授权并允许系统调试确认框。仅支持 TLS 配对码的无线调试暂不支持。后台连接和恢复只使用已授权密钥，不主动弹授权框；ADB 已授权不代表固件一定支持暂停扫描。
- **恢复机制**：更改前先持久记录恢复标记；最后一个控制器关闭后恢复，失败每 30 秒重试，下次打开应用也会恢复。应用被强制停止时无法运行恢复代码；可再次打开应用恢复。若 ADB 被关闭导致恢复失败，可恢复 ADB 后再次打开应用，或重启车机。Android 13+ 能查询原状态时，不恢复其他工具已关闭的自动加入；较老固件不能查询原值，因此此选项应在原本允许自动加入的情况下使用。
- **信道策略**：系统自动 5 GHz（默认）、低信道优先（36）、高信道优先（149）、指定非 DFS 5 GHz 信道（36/40/44/48/149/153/157/161/165）。通过 Android 公开的组工作频率接口请求，无需 ADB。是否允许由固件和地区法规决定，系统可能覆盖请求，以最近一次热点实际信道和日志为准。选择这些信道不保证比自动更快或更稳定，也不改变信道宽度。
- **回退自动**：默认开启，仅在指定信道被平台明确拒绝时重建一次自动 5 GHz 分组。忙碌、超时、权限拒绝和取消不会触发该信道回退；明确的信道拒绝也不会触发原有 Wi-Fi 关开恢复。已有连接不会主动换信道，需重启 CarPlay 应用策略。
- **模式限制**：自动热点仍由系统选信道；手动热点要在车机系统热点设置中改信道。原手动信道输入不是射频控制命令。

建议先只改信道策略进行同一路线的 A/B 测试，再单独启用扫描抑制。记录实际信道、车机/Android 版本、断联时间和连接日志，区分系统搜网、外部拥塞、DFS/共存限制和供电问题。云端单元测试不能代替实车抗干扰测试。

本次移植的网络扫描、ADB 协议及相关测试来自 DiPlay 上述提交，按 GPL-3.0 保留来源；未引入其仪表控制功能或 AGPL 界面代码。本仓库的 GPL-3.0 许可证继续适用。

## Features

- 面向 Android 和 Android Automotive OS 的 CarPlay 主机应用。
- 支持 CH341 桥接 MFI 芯片、原生 `/dev/i2c-N` 设备连接的 MFI 芯片、本地证书/私钥文件和 Remote MFI 认证（API 见下）。
- 支持 CarPlay 有线或无线连接。
- 支持触发 CarPlay Ultra （未测试/未完成的协议栈，但是确实可以在 iPhone 上触发 CarPlay Ultra 的提示）。
- 支持语音、导航、音乐多通道音频输出并 mapping 至 Android 的对应通道。
- 支持动态 Activity resize ，并自动重新握手至新的分辨率。
- 支持主屏和仪表的独立网页输出（浏览器直接解码 H.264/H.265，主屏支持触控和音频）。
- 支持车机位置回传。
- 支持 Android 9 (API 28) 。

## 使用方法

### 个人开发分支 `personal-dev`

测试包沿用上游基础版本号和 versionCode，在 debug 的版本名后增加 `B`，当前为 `1.4.0B`。首页和“关于”显示实际安装版本；“关于”同时显示当前 Android 版本和 API 等级。

- 界面默认使用简体中文。设置页按分类显示，右上角固定“重启 CarPlay”和“退出应用”；窄窗口使用“重启”“退出”的短标签。点击重启会校验、保存并应用当前设置，关闭设置则放弃未保存的修改。
- 非全屏显示时，按设置预留顶部和底部系统栏空间，避免切换应用时的临时系统栏变化触发分辨率重连；后台或失焦期间不会应用排队中的尺寸变化。真正的分屏、旋转和设置变更仍按实际尺寸处理。
- “连接 → 启动失败时重置 Wi-Fi”默认开启，仅对自动热点生效。首次热点启动失败后释放旧资源，尝试关闭再开启 Wi-Fi，并用新的热点实例重试一次。系统拒绝切换 Wi-Fi 时仅重建热点；手动热点模式不会被自动重置。
- 取消启动或退出时，已由应用关闭的 Wi-Fi 会先尝试恢复，下一次热点启动等待该恢复结束。此功能能否直接切换 Wi-Fi 取决于 Android 版本和车机固件权限。
- GitHub Actions 的分支调试构建无需配置上游的发布签名密钥；缺少密钥时使用标准 debug 签名。构建产物位于对应运行的 `debug-apks-<commit>`，测试和 lint 报告单独上传。标签发布仍要求发布签名密钥。

实机验收建议：显示顶部和底部系统栏，连续快速切出/返回应用并观察是否保持原会话；再验证分屏、旋转、设置页滚动和取消编辑，以及热点首次失败后的恢复。云端单元测试不能替代 iPhone 和车机测试。

1. 通过蓝牙将 iPhone 与车机配对。
2. CarPlay 视频流尚未启动时，点击右下角的设置按钮；也可以用三指向下滑动打开设置页面。
3. 确认所有设置均已按需配置。
   如需启用另一种手势，打开 `More gestures to Settings page`：单指从屏幕左侧 1/8 区域的上 1/4 开始，沿左侧下滑，在下 1/4 区域抬起。
4. 滑动到底部，选择 `Save & Reconnect`。
5. 按照你选择的方式连接 MFi 芯片。
6. 等待连接完成，然后开始使用。

## 网页显示

1. 在设置的 `Web displays` 中开启 `Main display` 和/或 `Instrument display`，填写分辨率，点击 `Save & Reconnect`。
2. 浏览器设备与 Android 接入同一局域网，打开设置里显示的地址（`https://<IP>:8080/main` 或 `/cluster`，可复制）。打开的页面在 CarPlay 重连期间保持连接。
3. 证书为自签名，每个浏览器首次打开会提示不安全，选择继续访问即可。
4. 主屏网页：支持双指以内触控；打开后手机音频转到网页播放，点一下屏幕即可出声（浏览器限制）。麦克风仍使用 Android。
5. 仪表网页仅显示，需要 iPhone 提供第二屏视频流。

建议使用较新的 Chrome / Edge / Safari；浏览器不支持 H.265 时，在设置中关闭 `HEVC (H.265)`。

## 当前进度

他运转👍，已在车机/手机平台测试，如果出现部分车机不适配的情况欢迎 issue （并附上你的 log ，位于 `/sdcard/Android/data/com.shilapi.xcertplay/files/logs/xcertplay.log`）

转接板：[CH341-to-MFI](https://github.com/shilapi/ch341-to-mfi-chip)

## 本地 MFI 文件

在 `MFI certificate & signing target` 中选择 `Local files`，然后通过两个 `Choose`
按钮使用 Android 系统文件选择器选择证书和私钥。当前支持 DER PKCS#7 证书（`.p7b`）
及与之匹配的、未加密 DER PKCS#8 私钥（`.pk8`）。应用会在开始连接手机前校验两者是否
匹配，并在 MFI 重连时重新读取文件。

建议把私钥放在受保护的位置。应用不会把证书或私钥复制到偏好设置，只会保存 Android
授予的持久读取权限和文档 URI。

## 工程结构

| 路径 | 用途 |
| --- | --- |
| `common/` | 两个目标共用的 CarPlay 宿主界面、设置、持久化和应用资源。 |
| `mobile/` | 使用共享 CarPlay 主机界面的 Android 应用。 |
| `automotive/` | 使用共享主机界面并支持高级音频通道映射的 Android Automotive OS 应用。 |
| `shared/` | Car App Library 代码，以及 CH341、I2C、MFi、iPhone、iAP2、NCM、VPN、AirPlay 和媒体实现。 |

## Remote MFI 功能

Remote MFi 客户端把远程服务当作一块 MFi 芯片远程调用，抑或是采用 BAA 认证，通过远程进行认证免去了本地连接 MFI 芯片进行认证的流程。

### 端点

| Method | Path | 用途 | Request body | Success response | 失败 response |
| --- | --- | --- | --- | --- | --- |
| `GET` | `/mfi/certificate` | 获取 MFI 芯片版本、证书类型和证书内容，客户端首次调用后缓存 | 无 | 证书 JSON | `{"detail":"..."}` |
| `POST` | `/mfi/sign` | 对 challenge 签名 | `{"challenge":"...","requestId":"..."}` | `{"signature":"..."}` | `{"detail":"..."}` |
| `POST` | `/mfi/reset` | 请求重置远程 MFI 芯片 | `{}` | `{"detail":""}` | `{"detail":"..."}` |

（可选）采用标准 Bearer Authentication 进行验证。

**当前仅测试了 BAA Authentication**

## 环境要求

- 启动 Gradle 需要 JDK 17 或更高版本；daemon 通过 Gradle toolchain 解析 Java 25。
- Android SDK Platform 37。
- Android 9（API 28）或更高版本。
  在 Android 9 上不可用 Wi-Fi P2P 5 GHz 模式，应用会改用 LocalOnlyHotspot。
- Android NDK `28.2.13676358`。
- 硬件验证需要支持 USB Host/OTG 的 Android 设备以及 MFi 硬件。

## 构建

在 Windows PowerShell 中：

```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
.\gradlew.bat :shared:testDebugUnitTest :common:testDebugUnitTest :common:lintDebug :mobile:lintDebug :automotive:lintDebug :mobile:assembleDebug :automotive:assembleDebug
```

在 macOS 或 Linux 中：

```bash
./gradlew :shared:testDebugUnitTest :common:testDebugUnitTest :common:lintDebug :mobile:lintDebug :automotive:lintDebug :mobile:assembleDebug :automotive:assembleDebug
```

构建未签名 release APK：

```powershell
.\gradlew.bat :mobile:assembleRelease :automotive:assembleRelease
```

## 致谢

感谢 [LIVI](https://github.com/f-io/LIVI) 项目为本项目提供了重要参考。
感谢 [showcase](https://github.com/amineross/showcase) 项目为本项目的 BAA 认证提供重要参考。

## 许可证

本项目采用 [GNU General Public License v3.0](LICENSE) 许可。
