# 车辆 JSON 配置说明与核对记录

本说明对应个人版 1.4.2B。JSON 是车辆属性的读取与换算配置，不是车机固件、车型刷写文件、CAN 控制程序或可执行脚本。导入不会给应用新增车辆权限，也不会自动开启车辆数据上报。

## 数据怎样到达 CarPlay

流程为：用户选择 JSON → 校验为设置草稿 → 点击「重启 CarPlay」保存 → 创建只读 Android Car 属性源 → 校验设备、属性、类型、权限和时效 → 单位换算 → 按启用项及手机订阅情况，通过 iAP2 向已连接的 iPhone 提供数据。

JSON 不用于保存实时车辆读数，当前内置示例也没有车辆电量或车架号字段。它描述从哪里读取数据，以及如何解释读数。当前内置后端仅调用固定的 `android.car.Car` / CarPropertyManager 发现和读取属性；配置不能指定 Java 类、反射方法、命令、脚本、URL 或车辆写入操作。`match.package` 仅检查某个包是否存在，不执行该包。

## 文件结构

仅接受 `schemaVersion: 2`。顶层只允许以下字段：

| 字段 | 含义 |
| --- | --- |
| `schemaVersion` | 配置格式版本，必须为 2。 |
| `id`、`name`、`notes` | 标识、显示名称和说明。 |
| `match` | 可选的 Android API 等级、服务包名存在条件、必需属性列表。API 等级为精确匹配。 |
| `signals` | 1–500 个读取项：属性 ID、区域、类型、数组下标、单位、线性换算、枚举、轮询周期等。 |
| `carplay` | 将读取项名称绑定到受支持的 CarPlay 字段。 |

最小示例就是当前内置的标准 Android Automotive 配置：

```json
{
  "schemaVersion": 2,
  "id": "standard-android",
  "name": "Standard Android Automotive",
  "signals": {
    "speed": { "property": "PERF_VEHICLE_SPEED" },
    "gear": { "property": "GEAR_SELECTION" },
    "outside-temperature": { "property": "ENV_OUTSIDE_TEMPERATURE" }
  },
  "carplay": {
    "wheelSpeed": "speed",
    "gear": "gear",
    "outsideTemperature": "outside-temperature"
  }
}
```

这个示例只映射车速、挡位和外温，没有映射电量。标准车速属性以 m/s 为单位；应用会推导其类型、方向和时效要求，不能再乘 3.6 做重复换算。车速必须和挡位成对映射。厂家私有属性可使用十六进制 ID，但必须来自目标固件的真实属性目录，不能套用猜测的比亚迪 ID。

## 能映射的内容

| CarPlay 字段 | 输出含义 |
| --- | --- |
| `wheelSpeed`、`gear` | 车速和 P/R/N/D，用于支持的定位辅助报告；不保证手机导航一定采用。 |
| `batteryPercent`、`electricRange` | 电量百分比和剩余续航 km。 |
| `outsideTemperature`、`barometricPressure` | 外温 °C 和气压 mbar。 |
| `passengerSeatOccupied` | 乘客座椅占用状态。 |
| `washerOn`、`wipeDuration`、`wipeWait` | 玻璃清洗状态及雨刮时序。 |

未知字段会拒绝导入。JSON 只支持已有类型、枚举和线性换算，不支持任意计算公式；例如把电池能量 Wh 除以容量得到百分比，需要已有百分比数据源或后端实现，不能只改字段名。单位维度不一致会被拒绝。

## 权限、兼容性与默认行为

- 车辆来源的车速、电量、外温、气压、座椅和雨刮上报默认关闭；已有用户选择会保留。Android GPS 定位选项与车辆属性来源独立。
- 普通 Android / 比亚迪 DiLink 的版本名不代表提供标准 Android Automotive 属性服务。没有 `android.car`、相应服务或读取权限时，会显示不可用，不伪造数据；本配置不会自动改走 ADB、CAN 或 BYD 私有服务。
- 支持的写入面仅是应用自身的设置和导入文件副本；车辆后端没有 `setProperty` 接口。
- 实时读取由功能开关、诊断模式及手机订阅控制，最多每 200 ms 执行 4 次属性 getter。诊断采样独立且最多 10 分钟，不向 iPhone 上报诊断数据。
- 车速要求源事件时间在 2 秒内。其他字段也有最大有效期；失联、读失败、权限拒绝、未知挡位、非法单位、越界及过期数据不会作为有效车辆数据上报。
- 默认拒绝非正常属性状态。`ignoreStatus: true` 只能逐项显式放宽状态检查，诊断仍显示原状态；它不能绕过读取权限、类型、数值范围或时效检查。

## 导入和保存

通过系统文件选择器导入；缺少文件选择器的车机可从应用外部目录的 `vehicle-profile.json` 导入。读取过程中限制为 1 MiB，拒绝重复 JSON 键、过深嵌套、非有限数字和未知配置项。导入只修改草稿，关闭设置放弃；点击「重启 CarPlay」才把已验证的导入副本原子保存到应用内部目录。删除外部原文件不影响已保存副本。非法导入或保存失败保留原配置；升级后已保存配置无效时，明确提示并回退标准配置。

## 本轮补强和验证范围

本轮让 `match` 不匹配的配置停止属性 getter，清除此前有效样本；文件导入、文档导入和已保存副本统一在读取过程中限制字节数。回归覆盖导入草稿/保存、非法文件不覆盖旧配置、超大输入、脚本/网络字段拒绝、重复键、深层嵌套、单位重复转换、SDK/服务/属性不匹配、权限与过期数据处理。

这些是源码审核和自动化验证范围，不能证明你的 DiLink 4.0 暴露了哪些车辆属性。实车需要查看「车辆数据」中的属性目录和诊断结果，再确定映射；当前没有把默认示例认定为已验证的比亚迪专用配置。

2026-10-19 验证：合并构建 `f18fe5f5470646b78bd1ed7099c517dae88e61b1` 的 342 项测试全部通过（无失败、错误或跳过），common/mobile Lint 无错误。配置导入 3 项、边界 3 项、引擎 8 项、配置选择 2 项测试均通过；[查看构建和完整报告](https://github.com/whoyang233/xcertplay/actions/runs/39216081855)。实车属性可用性与导航采用效果尚未验证。
