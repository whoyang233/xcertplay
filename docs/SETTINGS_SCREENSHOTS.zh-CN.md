# 设置页面完整截图

这是当前个人版设置的 1080P 测试环境预览，覆盖 8 个设置分类，并补充高级无线连接和音质子页面。每组按滚动顺序从上到下排列，相邻图片保留部分重叠。图片编号可用于指定下一步调整，例如「D2：导航音量移到更靠前」。

本轮根据标注调整卡片间距、ADB 按钮及状态占位，移除音乐/导航两个用途绑定选择（保留编号通道和导航增益），补充标识与外观说明；不打包 APK。预览版本为 1.4.2B，Android 13 / API 33，1920×1080 / 320 DPI。为展示条件项，测试模拟 automotive 高级声道设置和已安装的比亚迪通话组件；不代表实际车机组件或 ADB 能力已经验证。音质页使用明确的示例格式能力，不替代车机实际检测结果。

来源：源码 `47d6c78538027ce20dd7389d7252677573bfa875`，[仅验证任务](https://github.com/whoyang233/xcertplay/actions/runs/39567775549)。421 项测试通过，无失败、错误或跳过；签名、APK 打包与上传均跳过。

| 编号 | 页面 | 图片数量 |
| --- | --- | --- |
| A | [连接](#connection) | 2 |
| B | [连接：高级选项和诊断展开](#advanced) | 3 |
| C | [车辆数据](#vehicle) | 3 |
| D | [音频](#audio) | 4 |
| E | [显示与视频](#display) | 4 |
| F | [标识与外观](#identity) | 2 |
| G | [启动与窗口](#window) | 1 |
| H | [诊断](#diagnostics) | 3 |
| I | [关于](#about) | 1 |
| J | [音频：音质子页面（示例能力）](#quality) | 3 |

<a id="connection"></a>
## A · 连接

**A1**

![A1 连接](screenshots/settings-gallery/01-connection-01.png)

**A2**

![A2 连接](screenshots/settings-gallery/01-connection-02.png)


<a id="advanced"></a>
## B · 连接：高级选项和诊断展开

**B1**

![B1 连接：高级选项和诊断展开](screenshots/settings-gallery/02-connection-expanded-01.png)

**B2**

![B2 连接：高级选项和诊断展开](screenshots/settings-gallery/02-connection-expanded-02.png)

**B3**

![B3 连接：高级选项和诊断展开](screenshots/settings-gallery/02-connection-expanded-03.png)


<a id="vehicle"></a>
## C · 车辆数据

**C1**

![C1 车辆数据](screenshots/settings-gallery/03-vehicle-01.png)

**C2**

![C2 车辆数据](screenshots/settings-gallery/03-vehicle-02.png)

**C3**

![C3 车辆数据](screenshots/settings-gallery/03-vehicle-03.png)


<a id="audio"></a>
## D · 音频

**D1**

![D1 音频](screenshots/settings-gallery/04-audio-01.png)

**D2**

![D2 音频](screenshots/settings-gallery/04-audio-02.png)

**D3**

![D3 音频](screenshots/settings-gallery/04-audio-03.png)

**D4**

![D4 音频](screenshots/settings-gallery/04-audio-04.png)


<a id="display"></a>
## E · 显示与视频

**E1**

![E1 显示与视频](screenshots/settings-gallery/05-display-01.png)

**E2**

![E2 显示与视频](screenshots/settings-gallery/05-display-02.png)

**E3**

![E3 显示与视频](screenshots/settings-gallery/05-display-03.png)

**E4**

![E4 显示与视频](screenshots/settings-gallery/05-display-04.png)


<a id="identity"></a>
## F · 标识与外观

**F1**

![F1 标识与外观](screenshots/settings-gallery/06-identity-01.png)

**F2**

![F2 标识与外观](screenshots/settings-gallery/06-identity-02.png)


<a id="window"></a>
## G · 启动与窗口

**G1**

![G1 启动与窗口](screenshots/settings-gallery/07-window-01.png)


<a id="diagnostics"></a>
## H · 诊断

**H1**

![H1 诊断](screenshots/settings-gallery/08-diagnostics-01.png)

**H2**

![H2 诊断](screenshots/settings-gallery/08-diagnostics-02.png)

**H3**

![H3 诊断](screenshots/settings-gallery/08-diagnostics-03.png)


<a id="about"></a>
## I · 关于

**I1**

![I1 关于](screenshots/settings-gallery/09-about-01.png)


<a id="quality"></a>
## J · 音频：音质子页面（示例能力）

以下可选格式使用测试数据展示排版；实际采样率、声道和兼容模式以车机检测为准。

**J1**

![J1 音频：音质子页面（示例能力）](screenshots/settings-gallery/10-audio-quality-sample-01.png)

**J2**

![J2 音频：音质子页面（示例能力）](screenshots/settings-gallery/10-audio-quality-sample-02.png)

**J3**

![J3 音频：音质子页面（示例能力）](screenshots/settings-gallery/10-audio-quality-sample-03.png)

## K · 视频缓冲选项

![K1 视频缓冲选项](screenshots/ui-refresh/10-video-presets.png)

## L · 主媒体音频缓冲选项

![L1 音频缓冲选项](screenshots/ui-refresh/11-audio-presets.png)
