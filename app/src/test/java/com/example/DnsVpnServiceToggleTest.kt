package com.example

import android.app.AlarmManager
import android.app.Service
import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import com.example.service.DnsVpnService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
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
    fun `startVpn handles START_STICKY, survives onTaskRemoved with relaunch alarm, and stopVpn shuts down cleanly`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val controller = Robolectric.buildService(DnsVpnService::class.java)
        val service = controller.create().get()

        val startIntent = Intent(context, DnsVpnService::class.java).apply {
            action = DnsVpnService.ACTION_START
            putExtra(DnsVpnService.EXTRA_SERVER_NAME, "AdGuard DNS")
            putExtra(DnsVpnService.EXTRA_PRIMARY_IPV4, "94.140.14.14")
        }
        val startMode = service.onStartCommand(startIntent, 0, 1)
        assertEquals(Service.START_STICKY, startMode)
        assertTrue(DnsVpnService.isShouldStayActive(context))

        // Swiping away from Recent Apps invokes onTaskRemoved — service must schedule revival & stay alive
        service.onTaskRemoved(Intent(Intent.ACTION_MAIN))
        assertTrue(DnsVpnService.isShouldStayActive(context))
        assertFalse(shadowOf(service).isStoppedBySelf)

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        assertNotNull(shadowOf(alarmManager).nextScheduledAlarm)

        // System START_STICKY restart with null intent and START_FLAG_RETRY restores START_STICKY
        val restartMode = service.onStartCommand(null, Service.START_FLAG_RETRY, 2)
        assertEquals(Service.START_STICKY, restartMode)

        // Manual stop clears persisted active flag, returns START_NOT_STICKY on ACTION_STOP, and stops cleanly
        val stopMode = service.onStartCommand(
            Intent(context, DnsVpnService::class.java).apply { action = DnsVpnService.ACTION_STOP },
            0,
            3
        )
        assertEquals(Service.START_NOT_STICKY, stopMode)
        assertFalse(DnsVpnService.isShouldStayActive(context))
        assertFalse(DnsVpnService.isRunning.value)
        assertEquals("Disconnected", DnsVpnService.activeServerName.value)
        assertTrue(shadowOf(service).isStoppedBySelf)

        controller.destroy()
    }
}
