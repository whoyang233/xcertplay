// Read addresses and decoding adapted from shihabal3amri/DiPlay 6d3b8a8 (GPL-3.0).
// Personal integration: fixed read commands, partial readings, strict validity and no vehicle writes.
package com.shilapi.xcertplay.vehicle.byd

import com.shilapi.xcertplay.transport.VehicleStatusSnapshot
import com.shilapi.xcertplay.vehicle.VehicleGear
import kotlin.math.roundToLong

/** No caller-supplied shell fragments or write operations are accepted. */
internal interface BydAdbLink : AutoCloseable {
    val status: String
    val isReady: Boolean
    fun connect(): Boolean
    fun command(command: String, timeoutMillis: Int = 1500): String?
    fun cancel()
}

internal enum class BydField(val symbol: String, val device: Int, val transaction: Int,
                            val canId: Int?, val canFdId: Int) {
    SPEED("Speed.SPEED_AUTO_SPEED", 1013, 7, -1176502256, -1807745016),
    GEAR("Gearbox.GEARBOX_AUTO_MODE_TYPE", 1011, 5, 555745336, 555745336),
    SOC("Statistic.STATISTIC_ELEC_PERCENTAGE", 1014, 7, 1033543720, 1246777400),
    RANGE("Statistic.STATISTIC_ELEC_DRIVING_RANGE", 1014, 5, 1033203771, 1246765118),
    ENERGY("Power.POWER_BATTERY_REMAIN_ELECTRICITY", 1005, 7, null, 882901008),
    BMS("Charging.CHARGING_BATTERRY_DEVICE_STATE", 1009, 5, 876611608, 876609560);

    val deviceName get() = when (this) {
        SPEED -> "SPEED"; GEAR -> "GEARBOX"; SOC, RANGE -> "STATISTIC"; ENERGY -> "POWER"; BMS -> "CHARGING"
    }
    val label get() = when (this) {
        SPEED -> "车速（km/h）"; GEAR -> "挡位代码"; SOC -> "电量（%）"; RANGE -> "续航（km）"
        ENERGY -> "剩余电量（kWh）"; BMS -> "充电状态代码"
    }
}

internal data class BydAddress(val device: Int, val fid: Int, val transaction: Int) {
    fun command() = "service call autoservice $transaction i32 $device i32 $fid"
}

internal data class BydMotion(val speedMetersPerSecond: Double, val gear: VehicleGear)
internal data class BydBattery(val percent: Double?, val rangeKm: Double?, val energyKwh: Double?, val bms: Int?) {
    fun snapshot() = VehicleStatusSnapshot(
        batteryPercent = percent, electricRangeKm = rangeKm,
        currentChargeWh = energyKwh?.let { (it * 1000).roundToLong() },
        // Unknown vendor states are omitted instead of being reported as not charging.
        charging = when (bms) { 0 -> false; 1 -> true; else -> null },
    )
}

internal object BydParcel {
    /** A successful Binder Parcel has a zero exception code before its numeric value. */
    fun value(text: String?): Int? {
        val body = text?.substringAfter("Parcel(", "")?.substringBefore('\'') ?: return null
        val words = body.trim().split(Regex("\\s+")).mapNotNull {
            it.takeIf { word -> word.matches(Regex("[0-9a-fA-F]{8}")) }?.toLongOrNull(16)
        }
        return if (words.size >= 2 && words[0] == 0L) words[1].toInt() else null
    }
}

/** Worker-thread only. Protocol/firmware addresses identify getters, never CarPlay field ids. */
internal class BydVehicleReader(private val link: BydAdbLink,
                                private val catalog: Map<BydField, BydAddress> = emptyMap()) {
    var protocol: String? = null; private set
    var lastValues: Map<String, String> = emptyMap(); private set
    private var prepared = false
    private var gear: VehicleGear? = null
    private var gearAt = Long.MIN_VALUE

    fun prepare(): Boolean {
        if (!link.connect()) { prepared = false; gear = null; return false }
        if (!prepared) {
            protocol = link.command("getprop ro.car.protocol")?.trim()?.takeIf { it.isNotEmpty() }
                ?: link.command("getprop sys.car.protocol")?.trim()
            prepared = true
        }
        return true
    }

    private fun address(field: BydField): BydAddress? = catalog[field] ?: when (protocol) {
        "CAN" -> field.canId?.let { BydAddress(field.device, it, field.transaction) }
        "CANFD" -> BydAddress(field.device, field.canFdId, field.transaction)
        else -> null // An unknown firmware/protocol must not be guessed from the Android version.
    }

    private fun raw(field: BydField): Int? {
        val address = address(field)
        val value = address?.let { BydParcel.value(link.command(it.command())) }
        lastValues = lastValues + (field.label to (value?.let {
            if (field in setOf(BydField.SPEED, BydField.SOC, BydField.ENERGY))
                java.lang.Float.intBitsToFloat(it).toString() else it.toString()
        } ?: "不可用"))
        return value
    }

    fun motion(now: Long): BydMotion? {
        if (gearAt == Long.MIN_VALUE || now - gearAt !in 0..999) {
            gearAt = now
            gear = when (raw(BydField.GEAR)) {
                1 -> VehicleGear.PARK; 2 -> VehicleGear.REVERSE; 3 -> VehicleGear.NEUTRAL
                4, 5, 6 -> VehicleGear.DRIVE
                else -> null
            }
        }
        val kmh = raw(BydField.SPEED)?.let { java.lang.Float.intBitsToFloat(it).toDouble() }
            ?.takeIf { it.isFinite() && it in 0.0..300.0 } ?: return null
        return gear?.let { BydMotion(kmh / 3.6, it) }
    }

    fun battery(): BydBattery {
        fun float(field: BydField, range: ClosedFloatingPointRange<Double>) = raw(field)
            ?.let { java.lang.Float.intBitsToFloat(it).toDouble() }?.takeIf { it.isFinite() && it in range }
        val percent = float(BydField.SOC, 0.0..100.0)
        val range = raw(BydField.RANGE)?.takeIf { it in 0..3000 }?.toDouble()
        val energy = float(BydField.ENERGY, 0.0..300.0)
        val bms = raw(BydField.BMS)?.takeIf { it in 0..255 }
        return BydBattery(percent, range, energy, bms)
    }

    /** The eight tyre measurements have no implemented iAP2 target; diagnostics only. */
    fun tyres(): String {
        val pressure = listOf(-1728052956, -1728052952, -1728052948, -1728052944).map { id ->
            BydParcel.value(link.command(BydAddress(1016, id, 5).command()))?.takeIf { it in 50..600 }
        }
        // Instrument temperature addresses are verified only on CAN-FD firmware.
        val temperature = if (protocol == "CANFD" && pressure.any { it != null })
            listOf(1246797848, 1246797860, 1246797872, 1246797884).map { id ->
                BydParcel.value(link.command(BydAddress(1007, id, 5).command()))?.takeIf { it in -50..150 }
            } else List(4) { null }
        return listOf("左前", "右前", "左后", "右后").indices.joinToString("\n") { i ->
            "${listOf("左前", "右前", "左后", "右后")[i]}：${pressure[i]?.let { "$it kPa" } ?: "—"} / ${temperature[i]?.let { "$it °C" } ?: "—"}"
        }
    }
}
