package com.shilapi.xcertplay.network

import android.net.wifi.p2p.WifiP2pConfig
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29], manifest = Config.NONE)
class WifiP2pChannelConfigTest {
    private fun field(config: WifiP2pConfig, name: String) = config.javaClass.getField(name).getInt(config)
    private val credentials = WifiP2pCredentials("DIRECT-ab-xcertplay", "abcdefgh12345678")

    @Test fun automaticModeRequestsFiveGhzWithoutAnExactFrequency() {
        val config = wifiP2pGroupConfig(credentials, 0)
        assertEquals(WifiP2pConfig.GROUP_OWNER_BAND_5GHZ, field(config, "groupOwnerBand"))
        assertEquals(credentials.ssid, config.javaClass.getField("networkName").get(config))
        assertEquals(credentials.passphrase, config.javaClass.getField("passphrase").get(config))
    }

    @Test fun fixedModeRequestsFrequencyWithoutConflictingBandSetting() {
        WifiChannelPolicy.fixedChannels.forEach { channel ->
            val config = wifiP2pGroupConfig(credentials, channel)
            assertEquals(5000 + channel * 5, field(config, "groupOwnerBand"))
        }
    }

    @Test fun unsupportedOrDfsChannelIsRejectedBeforeCallingTheFramework() {
        listOf(-1, 6, 52, 100).forEach {
            assertThrows(IllegalArgumentException::class.java) { wifiP2pGroupConfig(credentials, it) }
        }
    }
}
