package com.shilapi.xcertplay.vehicle.byd

import android.content.Context
import android.os.Build
import android.os.SystemClock
import com.shilapi.xcertplay.adb.AdbKeys
import com.shilapi.xcertplay.adb.LocalAdb
import com.shilapi.xcertplay.transport.Iap2VehicleStatus
import com.shilapi.xcertplay.transport.VehicleSpeedReading
import com.shilapi.xcertplay.transport.VehicleStatusSnapshot
import com.shilapi.xcertplay.vehicle.VehicleDataConfig
import com.shilapi.xcertplay.vehicle.VehicleDataFeature
import com.shilapi.xcertplay.vehicle.source.*
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class BydAdbVehicleDataEntry : VehicleDataEntry {
    override val id = VehicleDataEntries.DEFAULT_ID
    override val displayName = "比亚迪 ADB（只读）"
    override fun canProvide(feature: VehicleDataFeature) = !feature.fromVehicle ||
        feature == VehicleDataFeature.WHEEL_SPEED || feature == VehicleDataFeature.EV_STATUS
    override fun create(request: VehicleDataSourceRequest): VehicleDataSource {
        val app = request.context.applicationContext
        return BydAdbVehicleSource(request.config, request.diagnostic, LocalBydAdbLink(app),
            SystemClock::elapsedRealtime, readerFactory = { link ->
                BydVehicleReader(link, BydReadCatalog.loadOrDetect(app, link, request.diagnostic))
            })
    }
}

/** One source owns one cancellable ADB link; neither the UI nor iAP2 performs shell I/O. */
internal class BydAdbVehicleSource(
    private val config: VehicleDataConfig,
    private val diagnostic: Boolean,
    private val link: BydAdbLink,
    private val now: () -> Long,
    private val readerFactory: (BydAdbLink) -> BydVehicleReader = { BydVehicleReader(it) },
    private val autoSchedule: Boolean = true,
) : VehicleDataSource {
    private val worker = Executors.newSingleThreadScheduledExecutor { task ->
        Thread(task, "xcertplay-byd-read").apply { isDaemon = true }
    }
    private val ready = Object()
    @Volatile private var closed = false
    @Volatile private var started = false
    @Volatile private var speedRequested = false
    @Volatile private var subscription = false
    @Volatile private var preflightUntil = 0L
    @Volatile private var motion: VehicleSpeedReading? = null
    @Volatile private var battery: Pair<Long, VehicleStatusSnapshot>? = null
    @Volatile private var detail = "尚未采样"
    private var reader: BydVehicleReader? = null
    private var batteryAt = Long.MIN_VALUE
    private var tyreAt = Long.MIN_VALUE
    private var diagnosticUntil = 0L
    private var tyreText = ""

    @Synchronized override fun start(): Boolean {
        if (closed) return false
        if (started) return true
        if (!diagnostic && config.enabled.none { it.fromVehicle }) return false
        started = true
        preflightUntil = now() + 2_000
        diagnosticUntil = now() + 600_000
        if (autoSchedule) worker.scheduleWithFixedDelay({
            try { tick() } catch (_: Exception) {
                motion = null; battery = null; detail = "读取失败；等待重试"
                synchronized(ready) { ready.notifyAll() }
            }
        }, 0, 250, TimeUnit.MILLISECONDS)
        return true
    }

    /** Deterministic worker step, also used by fake-ADB lifecycle and freshness tests. */
    internal fun tick() {
        if (!started || closed) return
        val time = now()
        val diagnosing = diagnostic && time < diagnosticUntil
        if (diagnostic && !diagnosing) { close(); return }
        val speedWanted = diagnosing || config.isEnabled(VehicleDataFeature.WHEEL_SPEED) && speedRequested
        val batteryWanted = diagnosing || config.isEnabled(VehicleDataFeature.EV_STATUS) && (subscription || time < preflightUntil)
        if (!speedWanted && !batteryWanted) return
        if (!link.connect()) {
            motion = null; battery = null; detail = "ADB：${link.status}；仅在车辆页面主动点击授权才会弹窗"
            synchronized(ready) { ready.notifyAll() }; return
        }
        val source = reader ?: readerFactory(link).also { reader = it }
        if (!source.prepare() || closed) { motion = null; battery = null; return }
        if (speedWanted) {
            val before = now()
            val value = source.motion(before)
            val after = now()
            // The read interval is bounded; this is an ADB observation time, not a claimed CAN timestamp.
            motion = value?.takeIf { after - before in 0..2_000 && link.isReady }?.let {
                VehicleSpeedReading((before + after) / 2, it.speedMetersPerSecond, it.gear)
            }
        }
        if (batteryWanted && (batteryAt == Long.MIN_VALUE || now() - batteryAt >= 30_000)) {
            val before = now()
            val value = source.battery().snapshot()
            val after = now()
            batteryAt = after
            battery = value.takeIf { after - before in 0..8_000 && link.isReady && Iap2VehicleStatus.capabilities(it).isNotEmpty() }
                ?.let { after to it }
        }
        if (diagnosing && (tyreAt == Long.MIN_VALUE || now() - tyreAt >= 10_000)) {
            // Slower, unmapped tyre reads never run during normal CarPlay forwarding.
            tyreText = source.tyres()
            tyreAt = now()
        }
        if (!link.isReady) { motion = null; battery = null }
        detail = "ADB：${link.status}\n车辆协议：${source.protocol ?: "未知（需检测固件字段）"}\n" +
            source.lastValues.entries.joinToString("\n") { (key, value) -> "$key：$value" } +
            if (diagnosing) "\n胎压/胎温（仅诊断，不发送至 CarPlay）：\n$tyreText" else ""
        synchronized(ready) { ready.notifyAll() }
    }

    override fun snapshot(): VehicleStatusSnapshot? = if (closed || diagnostic ||
        !config.isEnabled(VehicleDataFeature.EV_STATUS)) null else
        battery?.takeIf { now() - it.first in 0..90_000 }?.second

    override fun latestSpeed(): VehicleSpeedReading? = if (closed || diagnostic ||
        !config.isEnabled(VehicleDataFeature.WHEEL_SPEED)) null else
        motion?.takeIf { now() - it.elapsedMillis in 0..2_000 }

    private fun <T> await(timeout: Long, value: () -> T?): T? {
        val deadline = System.nanoTime() + timeout.coerceIn(0, 5_000) * 1_000_000
        synchronized(ready) {
            while (!closed && value() == null) {
                val remaining = (deadline - System.nanoTime()) / 1_000_000
                if (remaining <= 0) break
                ready.wait(remaining)
            }
        }
        return value()
    }
    override fun awaitSnapshot(timeoutMillis: Long): VehicleStatusSnapshot? {
        if (diagnostic || closed || !config.isEnabled(VehicleDataFeature.EV_STATUS)) return null
        preflightUntil = now() + timeoutMillis.coerceIn(0, 5_000)
        return await(timeoutMillis, ::snapshot)
    }
    override fun awaitSpeed(timeoutMillis: Long): VehicleSpeedReading? {
        if (diagnostic || closed || !config.isEnabled(VehicleDataFeature.WHEEL_SPEED)) return null
        val previous = speedRequested
        speedRequested = true
        return try { await(timeoutMillis, ::latestSpeed) } finally { speedRequested = previous }
    }
    override fun requestSpeed(enabled: Boolean) { speedRequested = enabled && !closed }
    override fun onSubscriptionChanged(active: Boolean) {
        subscription = active && !closed
        if (!active) preflightUntil = 0
    }
    override fun diagnostics() = detail + if (closed) "\n采样已停止" else ""
    override fun close() {
        closed = true; subscription = false; speedRequested = false
        motion = null; battery = null
        link.cancel() // Interrupt I/O immediately, even if the worker holds LocalAdb's monitor.
        worker.shutdownNow()
        synchronized(ready) { ready.notifyAll() }
    }
}

private class LocalBydAdbLink(private val context: Context) : BydAdbLink {
    @Volatile private var client: LocalAdb? = null
    @Volatile private var cancelled = false
    @Volatile override var status = "未连接"; private set
    @Volatile override var isReady = false; private set
    private var retryAt = 0L
    override fun connect(): Boolean {
        if (cancelled || SystemClock.elapsedRealtime() < retryAt) return false
        val adb = client ?: LocalAdb(AdbKeys.load(context)).also { client = it }
        if (cancelled) { adb.cancelPendingOperations(); return false }
        val access = adb.connect(mayAsk = false)
        isReady = access == LocalAdb.Access.READY
        status = when (access) {
            LocalAdb.Access.READY -> "已连接"
            LocalAdb.Access.NOT_APPROVED -> "尚未授权"
            LocalAdb.Access.UNSUPPORTED -> "仅支持传统网络 ADB，无法使用 TLS 配对模式"
            else -> "不可用，请检查车机网络 ADB（127.0.0.1:5555）"
        }
        if (access != LocalAdb.Access.READY) retryAt = SystemClock.elapsedRealtime() + 30_000
        return access == LocalAdb.Access.READY
    }
    override fun command(command: String, timeoutMillis: Int): String? {
        if (!connect()) return null
        return client?.shell(command, timeoutMillis).also {
            if (it == null) { isReady = false; status = "读取失败"; retryAt = SystemClock.elapsedRealtime() + 30_000 }
        }
    }
    override fun cancel() { cancelled = true; isReady = false; client?.cancelPendingOperations() }
    override fun close() { cancel() }
}

/** Only address metadata is cached, tied to the actual firmware. Live values are never persisted. */
internal object BydReadCatalog {
    fun loadOrDetect(context: Context, link: BydAdbLink, detect: Boolean): Map<BydField, BydAddress> {
        val prefs = context.getSharedPreferences("byd_read_catalog", Context.MODE_PRIVATE)
        val firmware = "${Build.FINGERPRINT}|${Build.DISPLAY}"
        val saved = if (prefs.getString("firmware", null) == firmware) BydField.entries.mapNotNull { field ->
            if (!prefs.contains(field.name)) null else field to BydAddress(prefs.getInt("${field.name}_device", field.device), prefs.getInt(field.name, 0), field.transaction)
        }.toMap() else emptyMap()
        if (!detect) return saved
        val apk = "'" + context.packageCodePath.replace("'", "'\"'\"'") + "'"
        val result = link.command("CLASSPATH=$apk app_process /system/bin ${BydCatalogProbeMain::class.java.name}", 15_000)
        if (result == null || BydCatalogProbeMain.HEADER !in result.lines()) return saved
        val symbols = result.lineSequence().mapNotNull { line ->
            val parts = line.trim().split('=')
            if (parts.size == 2) parts[1].toIntOrNull()?.let { parts[0] to it } else null
        }.toMap()
        val found = BydField.entries.mapNotNull { field ->
            (symbols[field.symbol] ?: if (field == BydField.BMS) symbols["Charging.CHARGING_BATTERY_DEVICE_STATE"] else null)
                ?.let { field to BydAddress(symbols["BYDAutoConstants.BYDAUTO_DEVICE_${field.deviceName}"] ?: field.device, it, field.transaction) }
        }.toMap()
        // Incomplete/failed detection never discards known addresses on the same firmware.
        val merged = saved + found
        if (found.isNotEmpty()) prefs.edit().apply {
            if (prefs.getString("firmware", null) != firmware) clear()
            putString("firmware", firmware)
            merged.forEach { (field, address) -> putInt(field.name, address.fid); putInt("${field.name}_device", address.device) }
        }.apply()
        return merged
    }
}
