package com.shilapi.xcertplay.update

import java.io.ByteArrayInputStream
import java.net.HttpURLConnection
import java.net.URL
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], manifest = Config.NONE)
class UpdateHttpClientTest {
    private val release = UpdateCatalog.release("xcertplay-1.4.2B-release-2026111002.apk")!!
    private class Connection(val bytes: ByteArray, val length: Long = bytes.size.toLong(), val code: Int = 200,
        val location: String? = null) : HttpURLConnection(URL(UpdateCatalog.BASE_URL)) {
        var closed = false
        override fun getInputStream() = ByteArrayInputStream(bytes)
        override fun getResponseCode() = code
        override fun getContentLengthLong() = length
        override fun getHeaderField(name: String?) = if (name == "Location") location else null
        override fun disconnect() { closed = true }
        override fun usingProxy() = false
        override fun connect() = Unit
    }

    @Test fun readsOnlyTheVersionCatalogAndClosesItsConnection() {
        val body = "{\"paths\":[{\"path_type\":\"File\",\"name\":\"${release.fileName}\"}]}"
        val connection = Connection(body.toByteArray())
        assertEquals(listOf(release), UpdateHttpClient { connection }.catalog())
        assertTrue(connection.closed)
    }

    @Test fun oversizedOrIncompleteCatalogsAreRejectedInsteadOfReportingCurrent() {
        for (connection in listOf(Connection(ByteArray(1), (UpdateCatalog.MAX_CATALOG_BYTES + 1).toLong()),
            Connection(ByteArray(UpdateCatalog.MAX_CATALOG_BYTES + 1)), Connection("{\"paths\":".toByteArray()))) {
            assertThrows(Exception::class.java) { UpdateHttpClient { connection }.catalog() }
            assertTrue(connection.closed)
        }
    }

    @Test fun catalogRedirectsCannotDowngradeHttpsChangeHostsOrEscapeTheReleaseDirectory() {
        for (url in listOf("http://apk.frp.whoyang.cn/xcertplay/release/?json", "https://evil.example/",
            UpdateCatalog.BASE_URL + "../secret", UpdateCatalog.BASE_URL + "%2e%2e/secret")) {
            var opens = 0
            val connection = Connection(ByteArray(0), code = 302, location = url)
            assertThrows(Exception::class.java) { UpdateHttpClient { opens++; connection }.catalog() }
            assertEquals(2, opens)
            assertTrue(connection.closed)
        }
    }

    @Test fun catalogFallsBackToTheActualNoscriptDirectoryIfJsonIsUnavailable() {
        val client = UpdateHttpClient { address ->
            if (address.endsWith("?json")) Connection(ByteArray(0), code = 404)
            else Connection(("<title>Index of /xcertplay/release</title><a href=\"${release.fileName}\">APK</a>").toByteArray())
        }
        assertEquals(listOf(release), client.catalog())
    }
}
