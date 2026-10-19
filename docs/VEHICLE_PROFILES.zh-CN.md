# 比亚迪车辆数据：ADB 读取与 CarPlay 上报

本说明对应个人版 1.4.2B 的比亚迪 ADB 数据源。文件名保留以兼容旧链接；旧 JSON 导入、标准 Android Car 属性后端、内置标准配置、CAR_* 权限和 android.car 共享库声明均已移除。旧 JSON 不再读取，无需导入车型配置。

## 来源与数据流

车辆控制器/传感器 → 比亚迪车机 autoservice → 已授权本机网络 ADB → 固定字段读取、校验、换算 → 现有 iAP2 CarPlay 上报。

保留原来的标准化数据含义，替换实际读取后端。CarPlay 接收的是 iAP2 VehicleStatusUpdate 和定位辅助 PASCD，不是 JSON；厂商属性编号也不是 CarPlay 参数编号。

| 读取项 | 来源 | CarPlay 处理 |
| --- | --- | --- |
| 车速、挡位 | SPEED / GEARBOX | km/h 转 m/s，1=P、2=R、3=N、4/5/6=D，通过 PASCD 提供定位辅助。 |
| 电量百分比 | STATISTIC | batteryPercent，参数 24，按协议乘 1000 编码。 |
| 剩余续航 | STATISTIC，km | electricRangeKm，参数 11，按手机请求发送。 |
| 剩余电量 | POWER，kWh | currentChargeWh，转 Wh，参数 22。 |
| 充电状态 | CHARGING，BMS | charging，参数 25；1=true、0=false，其他代码不推测。 |
| 四轮胎压、四轮胎温 | TYRE / INSTRUMENT，kPa/°C | 共 8 个量，仅诊断显示，暂无已实现的 CarPlay 映射。 |

不推算满电容量或满电续航，不把未知值当作 0。未引入外温、气压、座椅或雨刮的比亚迪读取适配，不将胎压等填进无关字段。

## 协议、地址与兼容

根据 `ro.car.protocol`，缺失时检查 `sys.car.protocol`，选择 DiPlay 已有的 CAN/CANFD 地址。CAN 没有已验证的剩余电量 kWh 地址，省略此项；胎温仅使用已验证的 CANFD 地址，CAN 固件不尝试套用；未知协议不按 Android 版本猜测地址。

主动点击实时诊断时，通过 `app_process` 只读获取车机 BYD SDK 六个已知字段及设备号，不扫描任意类、不写车辆、不驻留服务。地址元数据按 `Build.FINGERPRINT|Build.DISPLAY` 缓存；实时值不落盘。同一固件不完整检测保留已知地址，另一固件不复用旧地址。缓存地址也须实时成功读取并校验后才能上报。

以上是软件实现范围，不代表你的 DiLink 4.0 已验证支持。实际字段数量取决于固件提供的 autoservice 接口及 ADB 授权，以诊断结果为准。

## 授权与使用

1. 车机开启传统网络 ADB；当前使用本机 `127.0.0.1:5555`，不支持 Android 无线调试 TLS 配对模式。
2. 「设置 → 车辆数据」点击 ADB 授权，批准本应用密钥；与 Wi-Fi 扫描抑制复用同一应用内密钥。
3. 点击实时诊断，确认协议、基本读数和胎压/胎温；初次固件目录检测可能需要数秒。
4. 按需开启车速/挡位、电池/续航上报，点击右上角「重启 CarPlay」保存并连接。

车辆上报默认关闭。旧 JSON/Android Car 开关不会自动启用新 ADB 来源；GPS 选择保留。明确保存的新 ADB 开关在后续升级保留。GPS 位置开关独立。诊断由按钮主动触发，最多 10 分钟，关闭设置或连接 CarPlay 前停止，不向 iPhone 上报。

## 运行边界

- 后台只使用已批准密钥，不主动弹框；仅前台授权按钮申请批准。
- 车辆命令均固定且只读，没有车辆控制或用户输入的 shell/脚本入口。
- 独立工作线程执行 I/O，iAP2 仅取缓存；退出立即取消等待中的 socket。
- 车速最多约每 250 ms，挡位约每秒，电池约每 30 秒读取。除连接前有界探测外，常规读取由启用项和手机订阅控制。
- 车速采用 ADB 读取区间的观测时间，不冒称 CAN 原始事件时间。读取超过 2 秒或观测超过 2 秒不输出；电池批次超过 8 秒或缓存超过 90 秒不输出。时钟倒退和 ADB 失败使样本失效。
- 非法 Parcel、非有限数字、越界、未知枚举和读取错误不当作有效读数。
- 胎压/胎温每 10 秒仅在诊断中读取，不占用正常 CarPlay 车速采样。
- 上报不保证 Apple 地图或其他导航采用，也不代表完整 EV Routing 或 CarPlay Ultra。

## 来源与验证

只读地址、CAN/CANFD 电池读取、车速换算、胎压/胎温及窄范围固件探测参考 [DiPlay 0.2.21](https://github.com/shihabal3amri/DiPlay/tree/6d3b8a8ce927cb4b115b8c3881f17619433e93b1) 的 BydVehicleFields、BydBattery、BydWheelSpeed、BydTyres 和 Byd13CatalogProbeMain，保留 GPL-3.0 来源说明。个人版另行实现订阅、时效、取消、迁移及 xcertplay 协议对接，未引入其车辆控制或 AGPL 界面代码。

自动化回归覆盖协议、单位、非法/部分读数、错误恢复、时效、订阅、诊断隔离、取消、固件缓存、旧设置迁移及 iAP2 编码；实车 ADB、属性和导航采用效果仍需验收。

2026-10-19 验证：[构建 39267352871](https://github.com/whoyang233/xcertplay/actions/runs/39267352871)，源码 `e1039070440e4cfa658fd55fb928dbacee4ea960`，350 项测试全通过，其中 BYD 来源/迁移 25 项；可选网页兼容 76 项通过；Lint 无错误，仍有警告。automotive APK 已核验新来源存在、旧配置后端与权限移除，固定签名一致。实车效果未验证。
