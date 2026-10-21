# BYD CarPlay 车标与返回桌面

参考 DiPlay 源码 `143f4674b60b564a16baae5110b3b90fb7277631`：

- [AirPlayIconSizing.kt](https://github.com/shihabal3amri/DiPlay/blob/143f4674b60b564a16baae5110b3b90fb7277631/common/src/main/java/com/shilapi/xcertplay/AirPlayIconSizing.kt)：自定义正方形图标最大 192×192；保留较小图像，拒绝非正方形和损坏内容。保留 GPL-3.0 来源与注释。
- [CarPlayController.kt](https://github.com/shihabal3amri/DiPlay/blob/143f4674b60b564a16baae5110b3b90fb7277631/shared/src/main/java/com/shilapi/xcertplay/orchestration/CarPlayController.kt)：收到车机 UI 请求时，通过 `ACTION_MAIN` / `CATEGORY_HOME` / `FLAG_ACTIVITY_NEW_TASK` 打开车机默认桌面，保持会话运行。个人版额外忽略过期或已关闭会话的请求。
- [ic_car_home.png](https://github.com/shihabal3amri/DiPlay/blob/143f4674b60b564a16baae5110b3b90fb7277631/common/src/main/res/raw/ic_car_home.png)：使用上游 BYD 车标，原始 SHA-256 为 `797309966b8bad38528a6d34e2811e0d050ae219ad963256742f6a97ed7b2e61`。商标及图像权益归各自权利人，使用不表示 BYD 认可或认证；代码许可不改变商标权益。

设置位于「标识与外观」，仍使用个人版 Compose 界面，没有引入 DiPlay 的 AGPL 设置界面。默认名称为 BYD；旧空名称采用 BYD，有自定义名称和图标的保持原值。恢复默认使用内置 BYD 图标。图标与名称修改沿用原有重启保存、关闭放弃规则；图像原稿保持存储，在预览和发送时统一缩小。

点击 CarPlay 的车标返回的是车机配置的默认 Android Home，不硬编码比亚迪包名；没有 Home 处理器或系统拒绝打开时记录日志并保持 CarPlay 会话。此项无需本机 ADB，不启动车辆写入、仪表输出或导航箭头功能，不修改安卓桌面应用图标。

本次同时打包此前累计源码修改：设置布局、音视频 0 ms 与低缓冲档位、Web 渲染完整移除等。仅构建 automotive 车载 APK，维持个人包名、版本 1.4.2B / 1402 及固定签名。

验证构建：[任务 39572068305](https://github.com/whoyang233/xcertplay/actions/runs/39572068305)，源码 `1f69a4a6e7a6adc242c5378ccd7ba2902d8e642b`。431 项测试通过，Lint 无错误；单个 automotive APK 固定签名校验通过，安装包 47,755,047 字节。新增用例包含 Android 9/13 的返回桌面、桌面不可用和旧会话拒绝，以及图标缩放/默认资源/名称迁移。

仅发布既有已验证安装包的 CI 模式会再次校验构建结果、测试/Lint 报告、APK 签名及包名版本，然后上传到个人测试版 Release；不读取签名私钥或重新打包。
