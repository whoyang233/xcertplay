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
        override fun download(release: UpdateRelease, directory: File, progress: (Int?) -> Unit): File = error("Not requested")
    }
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

    @Test fun buildNumberIsReadAsTheRequestedUnformattedTenDigits() {
        assertEquals("2026111002", InstalledUpdateVersion.read(context).buildId)
        assertNull(InstalledUpdateVersion("1.4.2B", 1402).buildId)
    }

    @Test fun automaticChecksDefaultOnAndCanBeDisabledWithoutDisablingManualChecks() {
        val calls = AtomicInteger()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val updater = AppUpdater(context, scope, Transport { calls.incrementAndGet(); listOf(latest) })
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
            assertFalse(AppUpdater(context, scope, Transport { error("No automatic request") }).state.value.automatic)
        } finally { scope.cancel() }
    }

    @Test fun turningOffAutomaticCheckingCancelsItsPendingPromptAndDuplicateChecksAreCoalesced() {
        val entered = CountDownLatch(1)
        val finish = CountDownLatch(1)
        val calls = AtomicInteger()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val updater = AppUpdater(context, scope, Transport {
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
            val updater = AppUpdater(context, scope, Transport { throw IOException("Offline") })
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
}
