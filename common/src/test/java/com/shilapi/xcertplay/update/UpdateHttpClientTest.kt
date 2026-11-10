package com.shilapi.xcertplay.update

import java.io.ByteArrayInputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.CancellationException
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], manifest = Config.NONE)
class UpdateHttpClientTest {
    @get:Rule val temp = TemporaryFolder()
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

    @Test fun downloadCompletesOnlyAfterMatchingLengthAndClosesTheConnection() {
        val connection = Connection(byteArrayOf(1, 2, 3, 4))
        val out = UpdateHttpClient { connection }.download(release.copy(size = 4), temp.root) {}
        assertArrayEquals(connection.bytes, out.readBytes())
        assertTrue(connection.closed)
        assertFalse(File(temp.root, release.fileName + ".part").exists())
    }

    @Test fun oversizedTruncatedAndCancelledDownloadsNeverLeaveAnInstallableFile() {
        for (connection in listOf(Connection(byteArrayOf(1), UpdateCatalog.MAX_APK_BYTES + 1),
            Connection(byteArrayOf(1), 10), Connection(ByteArray(40000)))) {
            val dir = temp.newFolder()
            assertThrows(Exception::class.java) {
                UpdateHttpClient { connection }.download(release, dir) {
                    if (connection.bytes.size == 40000) throw CancellationException()
                }
            }
            assertTrue(connection.closed)
            assertTrue(dir.listFiles()!!.isEmpty())
        }
    }

    @Test fun redirectsCannotDowngradeHttpsChangeHostsOrEscapeTheReleaseDirectory() {
        for (url in listOf("http://apk.frp.whoyang.cn/xcertplay/release/x.apk", "https://evil.example/x.apk",
            UpdateCatalog.BASE_URL + "../secret.apk", UpdateCatalog.BASE_URL + "%2e%2e/secret.apk")) {
            var opens = 0
            val connection = Connection(ByteArray(0), code = 302, location = url)
            assertThrows(Exception::class.java) {
                UpdateHttpClient { opens++; connection }.download(release, temp.newFolder()) {}
            }
            assertEquals(1, opens)
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
