package com.shilapi.xcertplay

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.shilapi.xcertplay.vehicle.profile.VehicleProfileParser
import java.io.ByteArrayInputStream
import java.io.File
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class VehicleProfileImportTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val json get() = File("../shared/src/main/assets/vehicle-profiles/standard-android.json").readText()
    private val repository get() = VehicleProfileRepository(context)

    @Before fun reset() {
        context.getSharedPreferences("xcertplay_airplay", Context.MODE_PRIVATE).edit().clear().commit()
        File(context.filesDir,"vehicle-profile-active.json").delete()
    }

    @Test fun importingOnlyChangesTheDraftAndSavePersistsTheValidatedCopy() {
        val file=File(context.cacheDir,"vehicle-profile.json")
        val imported=json.replace("Standard Android Automotive","测试车辆")
        file.writeText(imported)
        val draft=repository.importFile(file)
        assertEquals(imported,draft)
        assertEquals(VehicleProfileRepository.BUNDLED_PREFIX+VehicleProfileRepository.DEFAULT_BUNDLED,repository.load().source)
        repository.save(VehicleProfileRepository.Selection(draft,VehicleProfileRepository.IMPORTED))
        file.delete()
        assertEquals(imported,repository.load().text)
        assertEquals(VehicleProfileRepository.IMPORTED,repository.load().source)
    }

    @Test fun rejectedImportAndRejectedSaveLeaveTheActiveProfileUntouched() {
        repository.save(VehicleProfileRepository.Selection(json,VehicleProfileRepository.IMPORTED))
        val bad=json.replace("\"schemaVersion\": 2","\"schemaVersion\": 1")
        val file=File(context.cacheDir,"bad.json").apply { writeText(bad) }
        assertThrows(IllegalArgumentException::class.java) { repository.importFile(file) }
        assertThrows(IllegalArgumentException::class.java) {
            repository.save(VehicleProfileRepository.Selection(bad,VehicleProfileRepository.IMPORTED))
        }
        assertEquals(json,repository.load().text)
    }

    @Test fun byteLimitAppliesWhileReadingEvenWhenMetadataCouldClaimASmallerFile() {
        val bytes=json.toByteArray()+ByteArray(1_048_576) { ' '.code.toByte() }
        val stream=ByteArrayInputStream(bytes)
        assertThrows(IllegalArgumentException::class.java) { VehicleProfileRepository.readProfile(stream) }
        val valid=VehicleProfileRepository.readProfile(ByteArrayInputStream(json.toByteArray()))
        assertEquals("standard-android",VehicleProfileParser.parse(valid).id)
    }
}
