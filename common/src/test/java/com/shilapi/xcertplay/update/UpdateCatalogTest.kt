package com.shilapi.xcertplay.update

import java.util.Base64
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], manifest = Config.NONE)
class UpdateCatalogTest {
    private fun file(build: String, version: String = "1.4.2B") = "xcertplay-$version-release-$build.apk"

    @Test fun recognizesTheActualDufsDirectoryAndChoosesTheNewestSameVersionBuild() {
        val json = """{"paths":[{"path_type":"File","name":"${file("2026111001")}","size":36943952},
            {"path_type":"Dir","name":"${file("2026111099")}"},
            {"path_type":"File","name":"${file("2026111003")}","size":37000000}]}"""
        val releases = UpdateCatalog.parse(json)
        assertEquals(2, releases.size)
        val latest = UpdateCatalog.latest(releases, "1.4.2B", 2026111002)!!
        assertEquals(2026111003L, latest.buildId)
        assertEquals(UpdateCatalog.BASE_URL + file("2026111003"), latest.url)
    }

    @Test fun embeddedDufsAndNoscriptLinksAgreeWithoutFollowingExternalUrls() {
        val json = """{"paths":[{"path_type":"File","name":"${file("2026111002")}"}]}"""
        val encoded = Base64.getEncoder().encodeToString(json.toByteArray())
        val embedded = "<template id=\"index-data\">$encoded</template>"
        val html = "<title>Index of /xcertplay/release</title>" +
            "<a href=\"${file("2026111002")}\">ok</a>" +
            "<a href=\"https://evil.example/${file("2026111099")}\">bad</a>" +
            "<a href=\"../${file("2026111098")}\">bad</a>"
        assertEquals(UpdateCatalog.parse(embedded), UpdateCatalog.parse(html))
    }

    @Test fun filenamesRequireReleasePersonalVersionRealDateAndOneToNinetyNine() {
        for (bad in listOf(file("2026023001"), file("2026111000"), file("20261110100"),
            "../" + file("2026111001"), file("2026111001").replace("release", "debug"),
            file("2026111001").replace("1.4.2B", "1.4.2"), file("2026111001") + "?x=1")) {
            assertNull(bad, UpdateCatalog.release(bad))
        }
        assertNotNull(UpdateCatalog.release(file("2028022901")))
        assertNull(UpdateCatalog.release(file("2026111001"), UpdateCatalog.MAX_APK_BYTES + 1))
    }

    @Test fun sortingIsNumericAndDoesNotOfferADowngradeOrAnOlderAndroidVersionCode() {
        val releases = listOf(UpdateCatalog.release(file("2026111002", "1.4.10B"))!!,
            UpdateCatalog.release(file("2026111099", "1.4.2B"))!!)
        assertEquals("1.4.10B", UpdateCatalog.latest(releases, "1.4.2B", 1402)!!.versionName)
        assertNull(UpdateCatalog.latest(releases, "1.5.0B", 2026111001))
        assertNull(UpdateCatalog.latest(releases, "1.4.10B", 2026111003))
        assertNull(UpdateCatalog.latest(emptyList(), "1.4.2B", 1402))
    }

    @Test fun unsupportedPagesAndOversizedOrDeepJsonFailInsteadOfReportingUpToDate() {
        for (body in listOf("<html>Please log in</html>", "x".repeat(UpdateCatalog.MAX_CATALOG_BYTES + 1),
            "{\"x\":" + "[".repeat(20) + "0" + "]".repeat(20) + "}")) {
            assertThrows(Exception::class.java) { UpdateCatalog.parse(body) }
        }
    }
}
