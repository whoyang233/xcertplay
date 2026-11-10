package com.shilapi.xcertplay.update

import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL

internal interface UpdateTransport {
    fun catalog(): List<UpdateRelease>
    fun download(release: UpdateRelease, directory: File, progress: (Int?) -> Unit): File
}

internal class UpdateHttpClient(
    private val connectionFactory: (String) -> HttpURLConnection = { URL(it).openConnection() as HttpURLConnection },
) : UpdateTransport {
    override fun catalog(): List<UpdateRelease> {
        return try { UpdateCatalog.parse(readText(UpdateCatalog.BASE_URL + "?json")) }
        catch (first: Exception) {
            try { UpdateCatalog.parse(readText(UpdateCatalog.BASE_URL + "?noscript")) }
            catch (second: Exception) { throw IOException("Update catalog unavailable", second) }
        }
    }

    override fun download(release: UpdateRelease, directory: File, progress: (Int?) -> Unit): File {
        require(UpdateCatalog.release(release.fileName) != null)
        check(directory.isDirectory || directory.mkdirs())
        val target = File(directory, release.fileName)
        val partial = File(directory, release.fileName + ".part")
        val connection = open(release.url)
        try {
            val length = connection.contentLengthLong
            if (length > UpdateCatalog.MAX_APK_BYTES || length == 0L) throw IOException("Invalid APK length")
            if (release.size != null && length > 0 && length != release.size) throw IOException("APK length changed")
            var received = 0L
            partial.outputStream().buffered().use { out ->
                connection.inputStream.use { input ->
                    val buffer = ByteArray(16384)
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        received += count
                        if (received > UpdateCatalog.MAX_APK_BYTES) throw IOException("APK too large")
                        val percent = if (length > 0) (received * 100 / length).coerceAtMost(100).toInt() else -1
                        // The callback also checks cancellation, even when the percentage is unchanged.
                        progress(if (percent >= 0) percent else null)
                        out.write(buffer, 0, count)
                    }
                }
            }
            if (received <= 0 || (length > 0 && received != length) || (release.size != null && received != release.size)) throw IOException("Incomplete APK")
            if (target.exists() && !target.delete()) throw IOException("Could not replace cached APK")
            if (!partial.renameTo(target)) throw IOException("Could not complete APK download")
            return target
        } finally {
            connection.disconnect()
            partial.delete()
        }
    }

    private fun readText(url: String): String {
        val connection = open(url)
        try {
            if (connection.contentLengthLong > UpdateCatalog.MAX_CATALOG_BYTES) throw IOException("Catalog too large")
            return connection.inputStream.use { input ->
                val out = ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    if (out.size() + count > UpdateCatalog.MAX_CATALOG_BYTES) throw IOException("Catalog too large")
                    out.write(buffer, 0, count)
                }
                out.toString("UTF-8")
            }
        } finally { connection.disconnect() }
    }

    private fun open(address: String): HttpURLConnection {
        var next = address
        repeat(4) { attempt ->
            val uri = URI(next)
            if (uri.scheme != "https" || uri.host != "apk.frp.whoyang.cn" || uri.port !in listOf(-1, 443) ||
                !uri.normalize().rawPath.startsWith("/xcertplay/release/") || uri.rawPath != uri.path ||
                uri.rawPath != uri.normalize().rawPath || uri.rawPath.contains('\\') ||
                uri.userInfo != null || uri.fragment != null) throw IOException("Invalid update URL")
            val connection = connectionFactory(next)
            connection.connectTimeout = 5000
            connection.readTimeout = 15000
            connection.instanceFollowRedirects = false
            connection.useCaches = false
            connection.setRequestProperty("Cache-Control", "no-cache")
            connection.setRequestProperty("Accept", "application/json,text/html,application/vnd.android.package-archive")
            try {
                val code = connection.responseCode
                if (code in 200..299) return connection
                if (code in listOf(301, 302, 303, 307, 308) && attempt < 3) {
                    val location = connection.getHeaderField("Location") ?: throw IOException("Missing redirect")
                    next = uri.resolve(location).toString()
                } else throw IOException("Update HTTP $code")
            } catch (error: Exception) { connection.disconnect(); throw error }
            connection.disconnect()
        }
        throw IOException("Too many redirects")
    }
}
