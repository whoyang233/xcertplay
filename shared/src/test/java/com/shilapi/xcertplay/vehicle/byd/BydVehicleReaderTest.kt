package com.shilapi.xcertplay.vehicle.byd

import com.shilapi.xcertplay.vehicle.VehicleGear
import org.junit.Assert.*
import org.junit.Test

internal class FakeBydLink : BydAdbLink {
    var protocol = "CANFD"
    var accessible = true
    var cancelled = false
    var reads = mutableListOf<String>()
    var failures = mutableSetOf<String>()
    var values = mutableMapOf<Int, Int>()
    var fallback: ((String) -> String?)? = null
    override var isReady = true
    override val status get() = if (accessible) "已连接" else "不可用"
    override fun connect(): Boolean { isReady = accessible && !cancelled; return isReady }
    override fun command(command: String, timeoutMillis: Int): String? {
        reads += command
        if (command in failures) { isReady = false; return null }
        fallback?.let { return it(command) }
        if (command.startsWith("getprop")) return protocol
        val id = command.substringAfterLast(' ').toIntOrNull() ?: return "unsupported"
        return values[id]?.let(::parcel) ?: "Result: Parcel(ffffffff 00000000 '........')"
    }
    fun float(id: Int, value: Float) { values[id] = value.toRawBits() }
    override fun cancel() { cancelled = true; isReady = false }
    override fun close() = cancel()
    companion object {
        fun parcel(value: Int) = "Result: Parcel(00000000 ${value.toUInt().toString(16).padStart(8, '0')} '........')"
    }
}

class BydVehicleReaderTest {
    @Test fun parcelRequiresAnActualSuccessfulBinderReply() {
        assertEquals(-1, BydParcel.value(FakeBydLink.parcel(-1)))
        for (reply in listOf(null, "Service autoservice does not exist.", "", "Parcel(00000001 42)", "Parcel(00000000)", "Parcel(ffffffff 00000001 '........')"))
            assertNull(reply, BydParcel.value(reply))
    }

    @Test fun canFdReadingsConvertUnitsWithoutInventingCapacity() {
        val link = FakeBydLink().apply {
            float(-1807745016, 72f); values[555745336] = 2
            float(1246777400, 60.5f); values[1246765118] = 260
            float(882901008, 42.25f); values[876609560] = 1
        }
        val reader = BydVehicleReader(link)
        assertTrue(reader.prepare())
        val motion = reader.motion(1000)!!
        assertEquals(20.0, motion.speedMetersPerSecond, 0.00001)
        assertEquals(VehicleGear.REVERSE, motion.gear)
        val battery = reader.battery().snapshot()
        assertEquals(60.5, battery.batteryPercent)
        assertEquals(260.0, battery.electricRangeKm)
        assertEquals(42250L, battery.currentChargeWh)
        assertEquals(true, battery.charging)
        assertTrue(link.reads.filter { it.startsWith("service") }.all { it.startsWith("service call autoservice ") })
    }

    @Test fun canUsesItsOwnAddressesAndOmitsUnverifiedRemainingEnergy() {
        val link = FakeBydLink().apply {
            protocol = "CAN"; float(-1176502256, 36f); values[555745336] = 6
            float(1033543720, 45f); values[1033203771] = 100; values[876611608] = 0
        }
        val reader = BydVehicleReader(link); reader.prepare()
        assertEquals(VehicleGear.DRIVE, reader.motion(1000)!!.gear)
        val status = reader.battery().snapshot()
        assertEquals(45.0, status.batteryPercent)
        assertEquals(false, status.charging)
        assertNull(status.currentChargeWh)
        assertFalse(link.reads.any { "882901008" in it })
    }

    @Test fun unknownProtocolDoesNotGuessFirmwareOrIssueVehicleGetters() {
        val link = FakeBydLink().apply { protocol = "UNKNOWN" }
        val reader = BydVehicleReader(link); reader.prepare()
        assertNull(reader.motion(1000)); assertNull(reader.battery().snapshot().batteryPercent)
        assertFalse(link.reads.any { it.startsWith("service") })
    }

    @Test fun liveFirmwareCatalogAddressesWorkEvenWithoutARecognizedProtocol() {
        val link = FakeBydLink().apply { protocol = ""; float(99, 18f); values[100] = 4 }
        val reader = BydVehicleReader(link, mapOf(BydField.SPEED to BydAddress(300,99,7), BydField.GEAR to BydAddress(301,100,5)))
        reader.prepare()
        assertEquals(5.0, reader.motion(1000)!!.speedMetersPerSecond, 0.00001)
        assertTrue("service call autoservice 7 i32 300 i32 99" in link.reads)
    }

    @Test fun malformedAndOutOfRangeValuesStayUnknownButZeroAndFalseRemainValid() {
        val link = FakeBydLink().apply {
            float(-1807745016, Float.NaN); values[555745336] = 255
            float(1246777400, 101f); values[1246765118] = -1
            float(882901008, Float.POSITIVE_INFINITY); values[876609560] = 255
        }
        val reader = BydVehicleReader(link); reader.prepare()
        assertNull(reader.motion(0)); assertEquals(BydBattery(null,null,null,255).snapshot(), reader.battery().snapshot())
        link.float(1246777400,0f); link.values[1246765118] = 0; link.float(882901008,0f); link.values[876609560] = 0
        assertEquals(0.0, reader.battery().snapshot().batteryPercent)
        assertEquals(0L, reader.battery().snapshot().currentChargeWh)
        assertEquals(false, reader.battery().snapshot().charging)
    }

    @Test fun gearIsRefreshedOncePerSecondAndFailureRemovesItsOldValue() {
        val link = FakeBydLink().apply { float(-1807745016, 10f); values[555745336] = 4 }
        val reader = BydVehicleReader(link); reader.prepare()
        assertNotNull(reader.motion(0)); link.values[555745336] = 255
        assertNotNull(reader.motion(999)); assertNull(reader.motion(1000))
        assertNull(reader.motion(1250))
    }

    @Test fun allEightTyreValuesAreReadForDiagnosticsWithBounds() {
        val link = FakeBydLink().apply {
            listOf(-1728052956,-1728052952,-1728052948,-1728052944).forEach { values[it] = 250 }
            listOf(1246797848,1246797860,1246797872,1246797884).forEach { values[it] = 31 }
            values[-1728052952] = 900; values[1246797872] = 999
        }
        val text = BydVehicleReader(link).tyres()
        assertEquals(8, link.reads.size)
        assertTrue(text.contains("左前：250 kPa / 31 °C"))
        assertTrue(text.contains("右前：— / 31 °C"))
        assertTrue(text.contains("左后：250 kPa / —"))
    }
}
