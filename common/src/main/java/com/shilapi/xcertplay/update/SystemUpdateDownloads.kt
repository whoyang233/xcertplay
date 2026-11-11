package com.shilapi.xcertplay.update

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.os.ParcelFileDescriptor
import java.io.File
import java.io.InputStream

internal enum class SystemDownloadPhase { WAITING, RUNNING, PAUSED, COMPLETE, FAILED, MISSING }
internal data class SystemDownloadStatus(val phase: SystemDownloadPhase, val progress: Int? = null, val reason: Int? = null)

internal interface UpdateDownloads {
    fun enqueue(release: UpdateRelease): Long
    fun status(id: Long): SystemDownloadStatus
    fun copyCompleted(id: Long, release: UpdateRelease, directory: File): File
    fun remove(id: Long)
}

/** Android owns the network transfer and notification, independently of the host Activity/VPN. */
internal class SystemUpdateDownloads(private val context: Context) : UpdateDownloads {
    private val manager get() = checkNotNull(context.getSystemService(DownloadManager::class.java))

    override fun enqueue(release: UpdateRelease): Long {
        require(UpdateCatalog.release(release.fileName) != null)
        val directory = File(checkNotNull(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)), "updates")
        check(directory.isDirectory || directory.mkdirs())
        val destination = File(directory, release.fileName)
        if (destination.exists()) check(destination.delete())
        val request = DownloadManager.Request(Uri.parse(release.url))
            .setTitle(release.fileName)
            .setDescription("xcertplay · ${release.versionName} · ${release.buildId}")
            .setMimeType("application/vnd.android.package-archive")
            .setDestinationInExternalFilesDir(context, Environment.DIRECTORY_DOWNLOADS, "updates/" + release.fileName)
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setAllowedOverMetered(true)
        return manager.enqueue(request).also { check(it >= 0) }
    }

    override fun status(id: Long): SystemDownloadStatus {
        manager.query(DownloadManager.Query().setFilterById(id))?.use { cursor ->
            if (!cursor.moveToFirst()) return SystemDownloadStatus(SystemDownloadPhase.MISSING)
            val status = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
            val total = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
            val received = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
            val reason = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON))
            if (total > UpdateCatalog.MAX_APK_BYTES || received > UpdateCatalog.MAX_APK_BYTES) {
                manager.remove(id)
                return SystemDownloadStatus(SystemDownloadPhase.FAILED, reason = DownloadManager.ERROR_CANNOT_RESUME)
            }
            val progress = if (total > 0) (received.coerceAtLeast(0) * 100 / total).coerceIn(0, 100).toInt() else null
            return SystemDownloadStatus(when (status) {
                DownloadManager.STATUS_PENDING -> SystemDownloadPhase.WAITING
                DownloadManager.STATUS_RUNNING -> SystemDownloadPhase.RUNNING
                DownloadManager.STATUS_PAUSED -> SystemDownloadPhase.PAUSED
                DownloadManager.STATUS_SUCCESSFUL -> SystemDownloadPhase.COMPLETE
                DownloadManager.STATUS_FAILED -> SystemDownloadPhase.FAILED
                else -> SystemDownloadPhase.MISSING
            }, progress, reason.takeIf { it != 0 })
        }
        return SystemDownloadStatus(SystemDownloadPhase.MISSING)
    }

    override fun copyCompleted(id: Long, release: UpdateRelease, directory: File): File {
        check(status(id).phase == SystemDownloadPhase.COMPLETE)
        return ParcelFileDescriptor.AutoCloseInputStream(manager.openDownloadedFile(id)).use { input ->
            copyVerifiedDownload(input, release, directory)
        }
    }

    override fun remove(id: Long) { manager.remove(id) }

    companion object {
        fun listIntent() = Intent(DownloadManager.ACTION_VIEW_DOWNLOADS)
        fun browserIntent(release: UpdateRelease) = Intent(Intent.ACTION_VIEW, Uri.parse(release.url))

        /** Copy only a complete bounded native download into the private FileProvider directory. */
        internal fun copyVerifiedDownload(input: InputStream, release: UpdateRelease, directory: File): File {
            require(UpdateCatalog.release(release.fileName) != null)
            check(directory.isDirectory || directory.mkdirs())
            val partial = File(directory, release.fileName + ".part")
            val destination = File(directory, release.fileName)
            try {
                var bytes = 0L
                partial.outputStream().buffered().use { output ->
                    val buffer = ByteArray(16384)
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        if (Thread.currentThread().isInterrupted) throw InterruptedException()
                        bytes += count
                        require(bytes <= UpdateCatalog.MAX_APK_BYTES && (release.size == null || bytes <= release.size))
                        output.write(buffer, 0, count)
                    }
                }
                require(bytes > 0 && (release.size == null || bytes == release.size))
                if (destination.exists()) check(destination.delete())
                check(partial.renameTo(destination))
                return destination
            } finally { partial.delete() }
        }
    }
}

/** Lives only for this Android process: Activity recreation/reconnect is not another launch. */
internal class StartupUpdateGate {
    private val claimed = java.util.concurrent.atomic.AtomicBoolean(false)
    fun claim(): Boolean = claimed.compareAndSet(false, true)
}
internal object ProcessUpdateStartup { val gate = StartupUpdateGate() }
