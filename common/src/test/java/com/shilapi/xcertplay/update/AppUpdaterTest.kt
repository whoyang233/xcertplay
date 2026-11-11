package com.shilapi.xcertplay.update

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.ApplicationInfo
import androidx.test.core.app.ApplicationProvider
import java.io.File
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], manifest = Config.NONE)
class AppUpdaterTest {
    private val context get() = ApplicationProvider.getApplicationContext<Context>()
    private val latest = UpdateCatalog.release("xcertplay-1.4.2B-release-2026111003.apk")!!
    private class Transport(val read: () -> List<UpdateRelease>) : UpdateTransport {
        override fun catalog() = read()
    }
    private fun visibleUpdater(scope: CoroutineScope, transport: UpdateTransport,
        gate: StartupUpdateGate = StartupUpdateGate(), downloads: UpdateDownloads = SystemUpdateDownloads(context),
        verify: (Context, File, UpdateRelease, InstalledUpdateVersion) -> Unit = UpdateApkVerifier::verify) =
        AppUpdater(context, scope, transport, downloads, gate, verify, pollMillis = 10).also { it.onHostResumed() }
    @Before fun installIdentity() {
        context.getSharedPreferences("xcertplay_updates", Context.MODE_PRIVATE).edit().clear().commit()
        shadowOf(context.packageManager).installPackage(PackageInfo().apply {
            packageName = context.packageName
            versionName = "1.4.2B"
            setLongVersionCode(2026111002L)
            applicationInfo = ApplicationInfo().apply { packageName = context.packageName }
        })
    }
    private fun await(updater: AppUpdater, phase: UpdatePhase) = runBlocking {
        withTimeout(3000) { updater.state.first { it.phase == phase } }
    }
    private fun completedCheck(updater: AppUpdater, scope: CoroutineScope) {
        await(updater, UpdatePhase.AVAILABLE)
        runBlocking { withTimeout(3000) { scope.coroutineContext[Job]!!.children.toList().joinAll() } }
    }
    private class NativeDownloads : UpdateDownloads {
        val enqueues = AtomicInteger()
        val removals = AtomicInteger()
        @Volatile var current = SystemDownloadStatus(SystemDownloadPhase.RUNNING, 40)
        var failEnqueue = false
        var copied: File? = null
        override fun enqueue(release: UpdateRelease): Long {
            enqueues.incrementAndGet()
            if (failEnqueue) throw IOException("Native service disabled")
            return 42L
        }
        override fun status(id: Long) = current
        override fun copyCompleted(id: Long, release: UpdateRelease, directory: File): File {
            directory.mkdirs()
            return File(directory, release.fileName).also { it.writeBytes(byteArrayOf(1, 2, 3)); copied = it }
        }
        override fun remove(id: Long) { removals.incrementAndGet() }
    }

    @Test fun buildNumberIsReadAsTheRequestedUnformattedTenDigits() {
        assertEquals("2026111002", InstalledUpdateVersion.read(context).buildId)
        assertNull(InstalledUpdateVersion("1.4.2B", 1402).buildId)
    }

    @Test fun automaticChecksDefaultOnAndCanBeDisabledWithoutDisablingManualChecks() {
        val calls = AtomicInteger()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val updater = visibleUpdater(scope, Transport { calls.incrementAndGet(); listOf(latest) })
            assertTrue(updater.state.value.automatic)
            updater.setAutomatic(false)
            updater.check(automatic = true)
            assertEquals(0, calls.get())
            updater.check()
            val found = await(updater, UpdatePhase.AVAILABLE)
            assertEquals(1, calls.get())
            assertTrue(found.showPrompt)
            assertEquals(latest, found.release)
            updater.dismissPrompt()
            assertFalse(updater.state.value.showPrompt)
            assertFalse(visibleUpdater(scope, Transport { error("No automatic request") }).state.value.automatic)
        } finally { scope.cancel() }
    }

    @Test fun turningOffAutomaticCheckingCancelsItsPendingPromptAndDuplicateChecksAreCoalesced() {
        val entered = CountDownLatch(1)
        val finish = CountDownLatch(1)
        val calls = AtomicInteger()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val updater = visibleUpdater(scope, Transport {
                calls.incrementAndGet(); entered.countDown(); finish.await(2, TimeUnit.SECONDS); listOf(latest)
            })
            updater.check(automatic = true)
            assertTrue(entered.await(2, TimeUnit.SECONDS))
            updater.check()
            assertEquals(1, calls.get())
            updater.setAutomatic(false)
            finish.countDown()
            runBlocking { withTimeout(3000) { scope.coroutineContext[Job]!!.children.toList().joinAll() } }
            assertEquals(UpdatePhase.IDLE, updater.state.value.phase)
            assertFalse(updater.state.value.showPrompt)
        } finally { finish.countDown(); scope.cancel() }
    }

    @Test fun networkFailureIsNeverReportedAsAlreadyUpToDate() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val updater = visibleUpdater(scope, Transport { throw IOException("Offline") })
            updater.check()
            assertFalse(await(updater, UpdatePhase.NETWORK_ERROR).showPrompt)
        } finally { scope.cancel() }
    }

    @Test fun installingRequiresMatchingPackageBuildReleaseAndThePinnedExistingSigner() {
        val pin = UpdateApkVerifier.PINNED_CERTIFICATE
        val current = InstalledUpdateVersion("1.4.2B", 2026111002L)
        fun verify(pkg: String = "com.whoyang233.xcertplay", version: String = "1.4.2B",
            code: Long = latest.buildId, debug: Boolean = false, signers: Set<String> = setOf(pin),
            installedSigners: Set<String> = setOf(pin)) = UpdateApkVerifier.validate(pkg, version, code, debug,
            signers, installedSigners, "com.whoyang233.xcertplay", latest, current)
        verify()
        assertThrows(IllegalArgumentException::class.java) { verify(pkg = "com.shilapi.xcertplay") }
        assertThrows(IllegalArgumentException::class.java) { verify(code = 1402) }
        assertThrows(IllegalArgumentException::class.java) { verify(version = "1.4.1B") }
        assertThrows(IllegalArgumentException::class.java) { verify(debug = true) }
        assertThrows(IllegalArgumentException::class.java) { verify(signers = setOf("other")) }
        assertThrows(IllegalArgumentException::class.java) { verify(installedSigners = setOf("other")) }
        assertThrows(IllegalArgumentException::class.java) { verify(signers = setOf(pin, "other")) }
    }

    @Test fun oneAutomaticPromptPerProcessSurvivesAppSwitchingReconnectsAndActivityRecreation() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val gate = StartupUpdateGate()
        val calls = AtomicInteger()
        val http = Transport { calls.incrementAndGet(); listOf(latest) }
        try {
            val updater = visibleUpdater(scope, http, gate)
            updater.check(automatic = true)
            completedCheck(updater, scope)
            assertTrue(updater.state.value.showPrompt)
            repeat(3) { updater.onHostPaused(); updater.onHostResumed(); updater.check(automatic = true) }
            assertEquals(1, calls.get())
            assertFalse(updater.state.value.showPrompt)
            visibleUpdater(scope, http, gate).check(automatic = true)
            assertEquals(1, calls.get())
            updater.check() // Explicit manual checking remains available.
            completedCheck(updater, scope)
            assertEquals(2, calls.get())
            assertTrue(updater.state.value.showPrompt)
            val nextProcess = visibleUpdater(scope, http, StartupUpdateGate())
            nextProcess.check(automatic = true)
            completedCheck(nextProcess, scope)
            assertEquals(3, calls.get())
        } finally { scope.cancel() }
    }

    @Test fun checkFinishingAfterAnAppSwitchDoesNotDisplayAPromptOnReturn() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val entered = CountDownLatch(1)
        val finish = CountDownLatch(1)
        try {
            val updater = visibleUpdater(scope, Transport { entered.countDown(); finish.await(2, TimeUnit.SECONDS); listOf(latest) })
            updater.check(automatic = true)
            assertTrue(entered.await(2, TimeUnit.SECONDS))
            updater.onHostPaused()
            updater.onHostResumed()
            finish.countDown()
            completedCheck(updater, scope)
            assertFalse(updater.state.value.showPrompt)
        } finally { finish.countDown(); scope.cancel() }
    }

    @Test fun enablingAutomaticCheckingLaterDoesNotTurnAResumeIntoASecondStartup() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val calls = AtomicInteger()
        try {
            val updater = visibleUpdater(scope, Transport { calls.incrementAndGet(); listOf(latest) })
            updater.setAutomatic(false)
            updater.check(automatic = true)
            updater.setAutomatic(true)
            updater.onHostPaused(); updater.onHostResumed(); updater.check(automatic = true)
            assertEquals(0, calls.get())
            updater.check()
            completedCheck(updater, scope)
            assertEquals(1, calls.get())
        } finally { scope.cancel() }
    }

    @Test fun nativeDownloadContinuesInBackgroundAndCompletionNeverAutomaticallyInstalls() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val native = NativeDownloads()
        val verifications = AtomicInteger()
        try {
            val updater = visibleUpdater(scope, Transport { listOf(latest) }, downloads = native,
                verify = { _, _, _, _ -> verifications.incrementAndGet() })
            updater.check(); completedCheck(updater, scope)
            updater.startSystemDownload()
            runBlocking { withTimeout(3000) { updater.state.first { it.downloadId == 42L } } }
            updater.onHostPaused()
            updater.setAutomatic(false)
            native.current = SystemDownloadStatus(SystemDownloadPhase.COMPLETE, 100)
            assertFalse(await(updater, UpdatePhase.READY).showPrompt)
            assertEquals(0, native.removals.get())
            assertEquals(1, verifications.get())
            updater.onHostResumed(); updater.check(automatic = true)
            assertEquals(UpdatePhase.READY, updater.state.value.phase)
            var installs = 0
            updater.retryInstall { installs++ }
            assertEquals(1, installs) // Only this explicit action asks for installation.
        } finally { scope.cancel() }
    }

    @Test fun aNewProcessReattachesToThePersistedNativeTaskWithoutDuplicatingTheDownload() {
        val firstScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val secondScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val native = NativeDownloads()
        try {
            val first = visibleUpdater(firstScope, Transport { listOf(latest) }, downloads = native)
            first.check(); completedCheck(first, firstScope)
            first.startSystemDownload()
            runBlocking { withTimeout(3000) { first.state.first { it.downloadId == 42L } } }
            firstScope.cancel()
            val restored = visibleUpdater(secondScope, Transport { error("Must not recheck while downloading") },
                downloads = native, verify = { _, _, _, _ -> })
            restored.check(automatic = true)
            assertEquals(1, native.enqueues.get())
            native.current = SystemDownloadStatus(SystemDownloadPhase.COMPLETE, 100)
            assertFalse(await(restored, UpdatePhase.READY).showPrompt)
            assertEquals(1, native.enqueues.get())
            assertEquals(0, native.removals.get())
        } finally { firstScope.cancel(); secondScope.cancel() }
    }

    @Test fun invalidNativeApkIsDeletedAndNeverBecomesInstallable() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val native = NativeDownloads().apply { current = SystemDownloadStatus(SystemDownloadPhase.COMPLETE, 100) }
        try {
            val updater = visibleUpdater(scope, Transport { listOf(latest) }, downloads = native,
                verify = { _, _, _, _ -> throw IllegalArgumentException("Wrong signature") })
            updater.check(); completedCheck(updater, scope)
            updater.startSystemDownload()
            await(updater, UpdatePhase.INVALID_APK)
            assertFalse(native.copied!!.exists())
            assertEquals(1, native.removals.get())
            updater.retryInstall { fail("Invalid APK must never launch an installer") }
        } finally { scope.cancel() }
    }

    @Test fun disabledSystemServiceOffersAReportedDownloadErrorAndBrowserIntent() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val native = NativeDownloads().apply { failEnqueue = true }
        try {
            val updater = visibleUpdater(scope, Transport { listOf(latest) }, downloads = native)
            updater.check(); completedCheck(updater, scope)
            updater.startSystemDownload()
            await(updater, UpdatePhase.DOWNLOAD_ERROR)
            assertFalse(updater.state.value.showPrompt)
            assertEquals(latest.url, SystemUpdateDownloads.browserIntent(latest).data.toString())
            assertEquals(1, native.enqueues.get())
        } finally { scope.cancel() }
    }
}
