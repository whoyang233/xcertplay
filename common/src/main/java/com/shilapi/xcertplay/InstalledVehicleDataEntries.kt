package com.shilapi.xcertplay

import com.shilapi.xcertplay.vehicle.source.VehicleDataEntry
import com.shilapi.xcertplay.vehicle.byd.BydAdbVehicleDataEntry

/**
 * The personal build ships one read-only BYD ADB backend. No imported JSON is executed or read.
 */
internal object InstalledVehicleDataEntries {
    fun all(): List<VehicleDataEntry> = listOf(
        BydAdbVehicleDataEntry(),
    )
}
