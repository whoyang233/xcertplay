package com.shilapi.xcertplay.update

import android.app.DownloadManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import java.io.ByteArrayInputStream
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28, 33], manifest = Config.NONE)
class SystemUpdateDownloadsTest {
    @get:Rule val temp = TemporaryFolder()
    private val context get() = ApplicationProvider.getApplicationContext<Context>()
    private val release = UpdateCatalog.release("xcertplay-1.4.2B-release-2026111103.apk")!!

    @Test fun submitsAnOwnedApkTransferToAndroidWithNativeProgressAndCompletionNotification() {
        val native = SystemUpdateDownloads(context)
        val id = native.enqueue(release)
        val manager = context.getSystemService(DownloadManager::class.java)
        val request = shadowOf(shadowOf(manager).getRequest(id))
        assertEquals(release.url, request.uri.toString())
        assertEquals(release.fileName, request.title.toString())
        assertEquals("application/vnd.android.package-archive", request.mimeType)
        assertEquals(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED, request.notificationVisibility)
        assertTrue(request.allowedOverMetered)
        assertTrue(request.destination.path!!.endsWith("/updates/" + release.fileName))
        request.setStatus(DownloadManager.STATUS_RUNNING)
        request.setTotalSize(100)
        request.setBytesSoFar(40)
        assertEquals(SystemDownloadStatus(SystemDownloadPhase.RUNNING, 40), native.status(id))
        assertEquals(DownloadManager.ACTION_VIEW_DOWNLOADS, SystemUpdateDownloads.listIntent().action)
    }

    @Test fun noticesRemovedFailedOrOversizedNativeTransfersWithoutReenqueuingThem() {
        val native = SystemUpdateDownloads(context)
        val id = native.enqueue(release)
        val manager = context.getSystemService(DownloadManager::class.java)
        val request = shadowOf(shadowOf(manager).getRequest(id))
        request.setStatus(DownloadManager.STATUS_FAILED)
        assertEquals(SystemDownloadPhase.FAILED, native.status(id).phase)
        request.setStatus(DownloadManager.STATUS_RUNNING)
        request.setTotalSize(UpdateCatalog.MAX_APK_BYTES + 1)
        assertEquals(SystemDownloadPhase.FAILED, native.status(id).phase)
        assertEquals(SystemDownloadPhase.MISSING, native.status(id).phase)
        assertEquals(0, shadowOf(manager).requestCount)
    }

    @Test fun verifiesNativeCopyLengthAndLeavesNoPartialOrTruncatedInstallableFile() {
        val bytes = byteArrayOf(1, 2, 3, 4)
        val complete = SystemUpdateDownloads.copyVerifiedDownload(ByteArrayInputStream(bytes), release.copy(size = 4), temp.newFolder())
        assertArrayEquals(bytes, complete.readBytes())
        for (body in listOf(ByteArray(0), byteArrayOf(1), ByteArray(5))) {
            val directory = temp.newFolder()
            assertThrows(Exception::class.java) {
                SystemUpdateDownloads.copyVerifiedDownload(ByteArrayInputStream(body), release.copy(size = 4), directory)
            }
            assertTrue(directory.listFiles()!!.isEmpty())
        }
    }
}
