package com.shilapi.xcertplay.update

import org.json.JSONObject
import java.net.URI
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Base64

internal data class UpdateRelease(val versionName: String, val buildId: Long, val fileName: String, val size: Long? = null) : Comparable<UpdateRelease> {
    val url: String get() = UpdateCatalog.BASE_URL + fileName
    override fun compareTo(other: UpdateRelease): Int {
        val a = UpdateCatalog.versionParts(versionName)
        val b = UpdateCatalog.versionParts(other.versionName)
        for (i in a.indices) if (a[i] != b[i]) return a[i].compareTo(b[i])
        return buildId.compareTo(other.buildId)
    }
    fun newerThan(version: String, code: Long): Boolean =
        buildId > code && compareTo(UpdateRelease(version, code, "")) > 0
}

/** Only filenames, never executable page content or arbitrary links, are taken from the catalog. */
internal object UpdateCatalog {
    const val BASE_URL = "https://apk.frp.whoyang.cn/xcertplay/release/"
    const val MAX_CATALOG_BYTES = 512 * 1024
    const val MAX_APK_BYTES = 128L * 1024 * 1024
    private val name = Regex("xcertplay-([0-9]{1,4}\\.[0-9]{1,4}\\.[0-9]{1,4}B)-release-([0-9]{10})\\.apk")

    fun validBuildId(value: String): Boolean = runCatching {
        require(value.matches(Regex("[0-9]{10}")))
        LocalDate.parse(value.take(8), DateTimeFormatter.BASIC_ISO_DATE)
        value.takeLast(2).toInt() in 1..99 && value.toLong() in 2000010101L..2100000000L
    }.getOrDefault(false)

    fun versionParts(version: String): List<Int> {
        val parts = version.removeSuffix("B").split('.')
        require(parts.size == 3 && parts.all { it.matches(Regex("[0-9]{1,4}")) })
        return parts.map(String::toInt)
    }

    fun release(file: String, size: Long? = null): UpdateRelease? {
        val match = name.matchEntire(file) ?: return null
        val build = match.groupValues[2]
        if (!validBuildId(build) || (size != null && size !in 1..MAX_APK_BYTES)) return null
        return UpdateRelease(match.groupValues[1], build.toLong(), file, size)
    }

    fun parse(text: String): List<UpdateRelease> {
        require(text.toByteArray(Charsets.UTF_8).size <= MAX_CATALOG_BYTES) { "Catalog too large" }
        val body = text.trim()
        if (body.startsWith("{")) {
            var depth = 0
            var quoted = false
            var escaped = false
            for (character in body) {
                if (quoted) {
                    if (escaped) escaped = false
                    else if (character == '\\') escaped = true
                    else if (character == '"') quoted = false
                } else when (character) {
                    '"' -> quoted = true
                    '{', '[' -> { depth++; require(depth <= 16) { "Catalog nesting too deep" } }
                    '}', ']' -> depth--
                }
            }
            val paths = JSONObject(body).getJSONArray("paths")
            require(paths.length() <= 2000)
            return (0 until paths.length()).mapNotNull { i ->
                val item = paths.getJSONObject(i)
                if (item.optString("path_type") != "File") null
                else release(item.optString("name"), if (item.has("size")) item.getLong("size") else null)
            }.distinctBy { it.fileName }
        }
        val embedded = Regex("<template\\s+id=[\"']index-data[\"']>([A-Za-z0-9+/=\\s]+)</template>").find(body)
        if (embedded != null) return parse(String(Base64.getDecoder().decode(embedded.groupValues[1].filterNot(Char::isWhitespace)), Charsets.UTF_8))
        require(Regex("(?i)(Index of |class=[\"']paths-table)").containsMatchIn(body)) { "Not a directory listing" }
        return Regex("(?i)href=[\"']([^\"']+)[\"']").findAll(body).mapNotNull { link ->
            val uri = runCatching { URI(BASE_URL).resolve(link.groupValues[1]) }.getOrNull() ?: return@mapNotNull null
            if (uri.scheme != "https" || uri.host != "apk.frp.whoyang.cn" || uri.port !in listOf(-1, 443) || uri.rawQuery != null || uri.rawFragment != null) return@mapNotNull null
            val file = uri.rawPath.removePrefix(URI(BASE_URL).path)
            release(file)
        }.distinctBy { it.fileName }.toList()
    }

    fun latest(releases: List<UpdateRelease>, installedVersion: String, installedCode: Long): UpdateRelease? =
        releases.filter { it.newerThan(installedVersion, installedCode) }.maxOrNull()
}
