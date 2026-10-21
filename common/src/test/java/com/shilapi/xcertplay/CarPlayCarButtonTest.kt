package com.shilapi.xcertplay

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import com.shilapi.xcertplay.airplay.*
import com.shilapi.xcertplay.orchestration.*
import com.shilapi.xcertplay.transport.Iap2IdentificationConfig
import java.net.Socket
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.util.ReflectionHelpers

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28, 33])
class CarPlayCarButtonTest {
    @Test fun activeCarButtonOpensHomeAndPreservesTheControllerAndMediaSession() = fixture { f ->
        f.listener.onHostUiRequested(f.session)
        val intent = f.context.launched.single()
        assertEquals(Intent.ACTION_MAIN, intent.action)
        assertTrue(intent.categories.contains(Intent.CATEGORY_HOME))
        assertTrue(intent.flags and Intent.FLAG_ACTIVITY_NEW_TASK != 0)
        assertEquals(1, f.notifications)
        f.assertSessionAlive()
    }

    @Test fun missingLauncherDoesNotTearDownCarPlay() = fixture { f ->
        f.context.missingLauncher = true
        f.listener.onHostUiRequested(f.session)
        assertEquals(1, f.notifications)
        assertTrue(f.logs.any { it.contains("could not open") })
        f.assertSessionAlive()
    }

    @Test fun replacedAndClosedSessionRequestsCannotOpenHome() = fixture { f ->
        val other = f.newSession()
        try {
            f.listener.onSessionActive(other)
            f.listener.onHostUiRequested(f.session)
            assertTrue(f.context.launched.isEmpty())
            assertEquals(0, f.notifications)
            f.controller.close()
            f.listener.onHostUiRequested(other)
            assertTrue(f.context.launched.isEmpty())
            assertEquals(0, f.notifications)
        } finally { other.close() }
    }

    private class LaunchContext(base: Context) : ContextWrapper(base) {
        val launched = mutableListOf<Intent>()
        var missingLauncher = false
        override fun getApplicationContext(): Context = this
        override fun startActivity(intent: Intent) {
            if (missingLauncher) throw ActivityNotFoundException("No home activity")
            launched += intent
        }
    }

    private class Fixture {
        val context = LaunchContext(RuntimeEnvironment.getApplication())
        val logs = mutableListOf<String>()
        var notifications = 0
        private val config = AirPlayConfig("test", "02:00:00:00:00:02", "02:00:00:00:00:01", "950.7.1",
            AirPlayDisplayConfig(1920, 1080))
        private val identity = AirPlayIdentity.generate()
        private val media = object : AirPlayMediaHandler {}
        val session = newSession()
        val controller = CarPlayController(context,
            CarPlayRuntimeConfig(mfiTarget = MfiTarget.I2C, linuxI2cPath = "/dev/i2c-1",
                identification = Iap2IdentificationConfig("test", "test", "test", "test", "1", "1", 3)),
            config, identity, PairingStore(), object : AirPlaySessionListener {
                override fun onHostUiRequested(session: AirPlaySession) { notifications++ }
                override fun onDebugLog(message: String) { logs += message }
            }, media, {})
        val listener: AirPlaySessionListener = ReflectionHelpers.getField(controller, "sessionListener")
        init { listener.onSessionActive(session) }
        fun newSession() = AirPlaySession(Socket(), config, identity, PairingStore(), null,
            object : AirPlaySessionListener {}, media)
        fun assertSessionAlive() {
            assertSame(session, ReflectionHelpers.getField(controller, "activeSession"))
            assertFalse(ReflectionHelpers.getField<Boolean>(controller, "closed"))
            val closed = ReflectionHelpers.getField<java.util.concurrent.atomic.AtomicBoolean>(session, "closed")
            assertFalse(closed.get())
        }
    }

    private fun fixture(test: (Fixture) -> Unit) {
        val f = Fixture()
        try { test(f) } finally {
            f.controller.close()
            assertTrue(f.controller.awaitClosed(5_000))
            f.session.close()
        }
    }
}
