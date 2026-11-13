package com.example

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import com.example.service.DnsVpnService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class DnsVpnServiceToggleTest {

    @Test
    fun `startVpn then stopVpn triggers service shutdown cleanly`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val controller = Robolectric.buildService(DnsVpnService::class.java)
        val service = controller.create().get()

        // Send ACTION_START (in Robolectric Builder.establish() returns null cleanly without crash)
        val startIntent = Intent(context, DnsVpnService::class.java).apply {
            action = DnsVpnService.ACTION_START
            putExtra(DnsVpnService.EXTRA_SERVER_NAME, "AdGuard DNS")
            putExtra(DnsVpnService.EXTRA_PRIMARY_IPV4, "94.140.14.14")
        }
        service.onStartCommand(startIntent, 0, 1)

        // Calling stopVpn must reset running state and request service shutdown cleanly
        DnsVpnService.stopVpn(context)

        assertFalse(DnsVpnService.isRunning.value)
        assertEquals("Disconnected", DnsVpnService.activeServerName.value)
        assertFalse(shadowOf(service).isStoppedBySelf.not())

        controller.destroy()
    }
}
