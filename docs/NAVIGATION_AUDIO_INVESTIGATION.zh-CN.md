# 导航静默后首字突响：上游对照与源码改进

核对日期：2026-11-11（北京时间）。本轮针对导航播报，报告的设置为车机导航音量 1、应用内导航音量 60%，厂商编号可能为 14 或 17，具体编号尚待实车确认。

## 上游证据

| 对象 | 查到的实现或记录 | 对个人版的意义 |
| --- | --- | --- |
| [DiPlay 主线 `615b32ce`](https://github.com/shihabal3amri/DiPlay/tree/615b32ce0b40cd0b99ce94ba23a512001553684f) | [#719](https://github.com/shihabal3amri/DiPlay/issues/719) 报告比亚迪 DiLink 3.0 的导航音量 1 仍过大，通道 14 的 `AudioTrack.setVolume()` 返回成功但听感不可靠。[`ab324016`](https://github.com/shihabal3amri/DiPlay/commit/ab324016a6187b8ec5d1b7ff43a6776279d26ab5) 改为逐块缩放 PCM。 | 保留个人版已经使用的 PCM 缩放，避免依赖厂商是否执行音轨音量命令。这条记录不能直接证明 DiLink 4.0 的首字突响根因相同。 |
| DiPlay 的 [NavigationAudioGain](https://github.com/shihabal3amri/DiPlay/blob/615b32ce0b40cd0b99ce94ba23a512001553684f/shared/src/main/java/com/shilapi/xcertplay/media/NavigationAudioGain.kt) | 导航独立衰减 -20 至 0 dB，其他流保持原样。 | 百分比和 dB 是不同刻度：60% 振幅约为 -4.44 dB，10% 约为 -20 dB。个人版已有 0%–200% 软件增益，无需照抄第二套音量滑块。 |
| DiPlay 的 [AudioRenderer](https://github.com/shihabal3amri/DiPlay/blob/615b32ce0b40cd0b99ce94ba23a512001553684f/shared/src/main/java/com/shilapi/xcertplay/media/AndroidMediaSink.kt#L2395) | PCM 衰减后做首次约 10 ms 淡入；淡入只在第一块 PCM 执行。记录实际使用的编号/自动回退、欠载、解码与写入状态。 | 数值诊断值得采用。首次淡入没有覆盖每次静默后的首字，也没有独立导航尖峰限幅，无法直接作为现成的根治补丁。 |
| DiPlay 的重新缓冲 | `maintainPlaybackBuffer()` 的欠载暂停恢复仅对 `AudioChannel.MEDIA` 开启，另外处理不足启动阈值的短尾包。 | 有助于音乐断续、积压和短尾音延迟；不把音乐重新缓冲策略套到导航，也不为本问题增加导航等待。 |
| [官方 xcertplay `1298fb35`](https://github.com/shilapi/xcertplay/tree/1298fb35e29ee30e7bd849cb0e751b52aa22a13f) | [首次 PCM 淡入](https://github.com/shilapi/xcertplay/blob/1298fb35e29ee30e7bd849cb0e751b52aa22a13f/shared/src/main/java/com/shilapi/xcertplay/media/AndroidMediaSink.kt#L1120) 也是一次性的约 10 ms；没有个人版的导航软件音量与持续尖峰保护。 | 官方主线没有可直接移植的“静默后首字防突响”方案。 |

DiPlay 的 #719 还记录了暂停音乐时，导航虽然一直请求通道 14，却从音乐扬声器播放的情况；厂商通道请求不等于车机最终扬声器与增益策略。这支持进行通道对照测试，尚不足以判断本车的具体原因。

## 在个人版源码中确认的保护缺口

此前导航从首个采样就应用保存的软件音量，并不存在已确认的“先 100%、再 60%”代码路径。但以下两点可以通过合成 PCM 验证：

1. 20 ms 淡入结束后，首个字剩余部分仍可完整输出。比如低声引导 40 ms 后出现 30000 的尖峰，60% 增益后的峰值仍为 18000。检查第一帧很小，不能代表整个首字平稳。
2. 静默识别要求连续 250 ms 的每个采样都不超过 32；80 LSB 的解码底噪就能阻止下一句重新触发保护。检测发生在音量缩放后，也会把某些小音量设置下的真实语音当成静默。

这些是可复现的软件缺口；是否就是实车所听到的全部原因，还需要输出日志与通道对照。

## 本轮源码改进

- 音频设置新增“导航尖峰保护”，默认开启，可关闭进行对照。与声道/导航音量一起保存，点击“重启 CarPlay”后应用；既有声道和音量值保留。
- 导航按原始 PCM 检测静默，再做保护与用户增益。5 ms 能量窗口容忍低电平底噪和孤立噪点，保留约 250 ms 静默后的重新保护，不因普通词间短停顿反复淡入。
- 保留 20 ms 开口淡入，另加持续的峰值抑制。保护在当前采样即生效，不等待后续音频；左右声道共用系数，不插入静音、不增设播放缓冲、不提高安静语音的音量。
- 尖峰上限为用户增益之前的 11626（约 -9 dBFS），10 ms 保持、80 ms 恢复时间常数。60% 软件增益时输出峰值不超过 6975；200% 时仍有削波余量。强峰后的短暂音量恢复会改变导航动态，可关闭保护对比。
- 音乐、通话和 Siri 的通道、增益与原有启动处理保持各自逻辑；手机混入音乐的导航无法独立处理。
- 日志保留首帧指标，并新增首字前 200 ms 的 `outputPeak` / `outputRms`、限幅记录、欠载数，以及采用的厂商编号或自动回退。只记录数值，不保留语音内容。

`audio navigation onset window=` 统计 AudioTrack **已接受**的 PCM，不代表 DAC、功放或扬声器的最终声压。`actualStream` 表示应用实际使用了编号构造还是自动回退，不表示厂商内部最终扬声器路由。

## 实车验证方法

在相同地图语音、相同系统音量与软件 60% 下，用“静默数秒 → 重复播报”的固定句子比较：

1. 记录当前厂商编号（14 或 17），确认有无突响；保存并重启 CarPlay。
2. 仅将导航编号改为 3（媒体），再重启 CarPlay 重复同一句。该步骤只用于定位，编号 3 可能采用媒体音量分组。
3. 若仅厂商编号异常，优先排查车机通道唤醒/路由增益；不能仅凭应用写入成功认定厂商通道正常。
4. 对比保护开/关与日志的 200 ms 输出峰值。如果 PCM 峰值已平稳而实车仍突响，继续检查车机播放链路，不再单纯拉长软件淡入。

本轮先做源码测试与 Release Lint，未生成新 APK。此前可下载的 `2026111003` 安装包不包含本轮尖峰保护与整字统计。

## 验证结果

[源码验证任务](https://github.com/whoyang233/xcertplay/actions/runs/42939550014)基于 `f503776ea59171ba73b5ca5899e14cc7522902f6`：485 项 Android 测试通过（shared 366、common 119），无失败、错误或跳过；6 项构建编号测试通过；Release Lint 无错误（common 85、automotive 3 条警告）。本轮新增 12 项执行用例，覆盖 20 ms 后的尖峰、底噪/稀疏噪点、低音量识别、多个采样率和声道、极端音量、恢复、拆包/偏移、首字统计、真实 AudioTrack 写入与开关持久化；既有布局用例还验证开关操作及设置值保留。

[测试报告与设置截图 ZIP](https://github.com/whoyang233/xcertplay/actions/runs/42939550014/artifacts/13820875350)包含 `settings-layout/1080p-audio-routing.png`。签名密钥读取、APK 组装、安装包验证与上传均跳过；没有新的可安装构建。
