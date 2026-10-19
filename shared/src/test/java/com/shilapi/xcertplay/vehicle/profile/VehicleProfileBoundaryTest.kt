package com.shilapi.xcertplay.vehicle.profile

import java.io.File
import org.junit.Assert.*
import org.junit.Test

class VehicleProfileBoundaryTest {
    private val standard get() = File("src/main/assets/vehicle-profiles/standard-android.json").readText()

    @Test fun executableAndNetworkConfigurationKeysAreRejectedAtEverySchemaLevel() {
        for (key in listOf("command","shell","script","url","className","setProperty")) {
            for (input in listOf(
                standard.replace("\"schemaVersion\": 2", "\"schemaVersion\": 2, \"$key\": \"anything\""),
                standard.replace("\"property\": \"PERF_VEHICLE_SPEED\"", "\"property\": \"PERF_VEHICLE_SPEED\", \"$key\": \"anything\""),
            )) assertTrue("Unexpectedly accepted $key",runCatching { VehicleProfileParser.parse(input) }.isFailure)
        }
    }

    @Test fun oversizedDeepDuplicateAndNonfiniteInputsAreRejected() {
        val invalid=listOf(
            standard+" ".repeat(1_048_577),
            "{\"notes\":"+"[".repeat(26)+"0"+"]".repeat(26)+"}",
            standard.replace("\"speed\": {", "\"speed\": {}, \"speed\": {"),
            standard.replace("\"property\": \"PERF_VEHICLE_SPEED\"", "\"property\": \"PERF_VEHICLE_SPEED\", \"scale\": 1e999"),
        )
        invalid.forEach { assertTrue(runCatching { VehicleProfileParser.parse(it) }.isFailure) }
    }

    @Test fun standardSpeedIsMetersPerSecondAndCannotBeConvertedTwice() {
        val speed=VehicleProfileParser.parse(standard).signal(CarPlayTarget.WHEEL_SPEED)!!
        assertEquals(10.0,VehicleNormalizer.normalize(speed,10f).number)
        assertEquals("m/s",speed.unit)
        assertTrue(runCatching { VehicleProfileParser.parse(standard.replace(
            "\"property\": \"PERF_VEHICLE_SPEED\"", "\"property\": \"PERF_VEHICLE_SPEED\", \"unit\": \"km/h\", \"scale\": 3.6")) }.isFailure)
    }
}
