// Narrow read-only firmware catalogue probe adapted from DiPlay 6d3b8a8 (GPL-3.0).
package com.shilapi.xcertplay.vehicle.byd

import java.lang.reflect.Modifier

/** Only reads six known SDK symbols under shell uid. No Binder writes or arbitrary reflection. */
object BydCatalogProbeMain {
    const val HEADER = "XCERTPLAY_BYD_READ_CATALOG_V1"
    @JvmStatic fun main(args: Array<String>) {
        val wanted = BydField.entries.map { it.symbol }.toSet() + "Charging.CHARGING_BATTERY_DEVICE_STATE" +
            BydField.entries.map { "BYDAutoConstants.BYDAUTO_DEVICE_${it.deviceName}" }
        println(HEADER)
        fun scan(type: Class<*>, seen: MutableSet<String>) {
            if (!seen.add(type.name)) return
            runCatching { type.declaredFields.toList() }.getOrDefault(emptyList()).forEach { field ->
                val key = "${type.simpleName}.${field.name}"
                if (key in wanted && Modifier.isStatic(field.modifiers) && field.type == Int::class.javaPrimitiveType)
                    runCatching { field.isAccessible = true; println("$key=${field.getInt(null)}") }
            }
            runCatching { type.declaredClasses.toList() }.getOrDefault(emptyList()).forEach { scan(it, seen) }
        }
        for (name in listOf("android.hardware.bydauto.BYDAutoFeatureIds", "android.hardware.bydauto.BYDAutoConstants"))
            runCatching { Class.forName(name) }.getOrNull()?.let { scan(it, hashSetOf()) }
    }
}
