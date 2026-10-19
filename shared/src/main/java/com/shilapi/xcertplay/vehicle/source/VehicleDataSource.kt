package com.shilapi.xcertplay.vehicle.source

import android.content.Context
import com.shilapi.xcertplay.transport.VehicleSpeedSource
import com.shilapi.xcertplay.transport.VehicleStatusProvider
import com.shilapi.xcertplay.vehicle.VehicleDataConfig
import com.shilapi.xcertplay.vehicle.VehicleDataFeature

/**
 * What CarPlay reads vehicle data from for one session. Vendor service reads stay behind this;
 * CarPlay sees only normalized EV status
 * and wheel speed. A source that has no speed returns null from [latestSpeed].
 */
interface VehicleDataSource : VehicleStatusProvider, VehicleSpeedSource {
    /** Starts reading off the iAP2/UI threads. False means nothing to read; the caller still closes it. */
    fun start(): Boolean
    /** Read-only text for the settings page. It is never sent to the phone. */
    fun diagnostics(): String
}

/**
 * Everything an entry may use to build one source. A [diagnostic] source reads every field it
 * knows for the settings page and must report nothing to the phone.
 */
class VehicleDataSourceRequest(
    /** Application context; a source outlives the Activity and must not capture it. */
    val context: Context,
    val config: VehicleDataConfig,
    val diagnostic: Boolean = false,
)

/**
 * One selectable way of reading the car; the selected entry's [id] is kept in settings.
 */
interface VehicleDataEntry {
    /** Stable settings key; never reuse an id for a different backend. */
    val id: String
    val displayName: String
    /** Settings disables a feature switch the entry can never feed; phone-side features are always available. */
    fun canProvide(feature: VehicleDataFeature): Boolean = !feature.fromVehicle
    fun create(request: VehicleDataSourceRequest): VehicleDataSource
}

object VehicleDataEntries {
    /** A new id deliberately separates ADB consent from old JSON/Android Car settings. */
    const val DEFAULT_ID = "byd-adb"

    /** The saved entry, or the first one when the saved id is no longer installed. */
    fun select(entries: List<VehicleDataEntry>, id: String): VehicleDataEntry {
        require(entries.isNotEmpty() && entries.map { it.id }.distinct().size == entries.size) { "Vehicle data entries need unique ids" }
        return entries.firstOrNull { it.id == id } ?: entries.first()
    }
}
