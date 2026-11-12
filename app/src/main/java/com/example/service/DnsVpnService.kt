package com.example.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.nio.channels.DatagramChannel

class DnsVpnService : VpnService() {

    private var vpnInterface: ParcelFileDescriptor? = null
    private val serviceScope = CoroutineScope(Dispatchers.IO + Job())
    private var tunnelJob: Job? = null

    companion object {
        const val ACTION_START = "com.pulse.dnspulse.action.START"
        const val ACTION_STOP = "com.pulse.dnspulse.action.STOP"
        const val EXTRA_PRIMARY_IPV4 = "extra_primary_ipv4"
        const val EXTRA_SECONDARY_IPV4 = "extra_secondary_ipv4"
        const val EXTRA_PRIMARY_IPV6 = "extra_primary_ipv6"
        const val EXTRA_SECONDARY_IPV6 = "extra_secondary_ipv6"
        const val EXTRA_SERVER_NAME = "extra_server_name"

        private const val NOTIFICATION_CHANNEL_ID = "dns_pulse_vpn_channel"
        private const val NOTIFICATION_ID = 1001

        private val _isRunning = MutableStateFlow(false)
        val isRunning = _isRunning.asStateFlow()

        private val _activeServerName = MutableStateFlow("Disconnected")
        val activeServerName = _activeServerName.asStateFlow()

        fun startVpn(
            context: Context,
            serverName: String,
            primaryIpv4: String,
            secondaryIpv4: String = "",
            primaryIpv6: String = "",
            secondaryIpv6: String = ""
        ) {
            val intent = Intent(context, DnsVpnService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_SERVER_NAME, serverName)
                putExtra(EXTRA_PRIMARY_IPV4, primaryIpv4)
                putExtra(EXTRA_SECONDARY_IPV4, secondaryIpv4)
                putExtra(EXTRA_PRIMARY_IPV6, primaryIpv6)
                putExtra(EXTRA_SECONDARY_IPV6, secondaryIpv6)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stopVpn(context: Context) {
            val intent = Intent(context, DnsVpnService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                disconnectVpn()
                stopSelf()
            }
            ACTION_START -> {
                val serverName = intent.getStringExtra(EXTRA_SERVER_NAME) ?: "DNS Pulse"
                val pIpv4 = intent.getStringExtra(EXTRA_PRIMARY_IPV4) ?: "94.140.14.14"
                val sIpv4 = intent.getStringExtra(EXTRA_SECONDARY_IPV4) ?: ""
                val pIpv6 = intent.getStringExtra(EXTRA_PRIMARY_IPV6) ?: ""
                val sIpv6 = intent.getStringExtra(EXTRA_SECONDARY_IPV6) ?: ""

                startForeground(NOTIFICATION_ID, buildNotification(serverName))
                connectVpn(serverName, pIpv4, sIpv4, pIpv6, sIpv6)
            }
        }
        return START_NOT_STICKY
    }

    private fun connectVpn(
        serverName: String,
        pIpv4: String,
        sIpv4: String,
        pIpv6: String,
        sIpv6: String
    ) {
        disconnectVpn()

        try {
            val builder = Builder().apply {
                setSession("DNS Pulse ($serverName)")
                setMtu(1500)
                // Local virtual IP inside VPN
                addAddress("10.233.1.2", 30)

                // Add configured DNS servers
                if (pIpv4.isNotBlank()) addDnsServer(pIpv4)
                if (sIpv4.isNotBlank()) addDnsServer(sIpv4)
                if (pIpv6.isNotBlank()) {
                    try {
                        addAddress("fd00:1:fd00:1::2", 126)
                        addDnsServer(pIpv6)
                        if (sIpv6.isNotBlank()) addDnsServer(sIpv6)
                    } catch (e: Exception) {
                        Log.w("DnsVpnService", "IPv6 routing optional skip", e)
                    }
                }

                // Route only DNS queries (port 53) to our local VPN interface by routing the DNS IP addresses
                if (pIpv4.isNotBlank()) addRoute(pIpv4, 32)
                if (sIpv4.isNotBlank()) addRoute(sIpv4, 32)
                if (pIpv6.isNotBlank()) {
                    try {
                        addRoute(pIpv6, 128)
                        if (sIpv6.isNotBlank()) addRoute(sIpv6, 128)
                    } catch (_: Exception) {}
                }

                // Disallow VPN looping back on itself
                try {
                    addDisallowedApplication(packageName)
                } catch (e: Exception) {
                    Log.w("DnsVpnService", "Cannot disallow self package", e)
                }
            }

            vpnInterface = builder.establish()
            if (vpnInterface != null) {
                _isRunning.value = true
                _activeServerName.value = serverName
                startDnsForwarding(pIpv4)
            } else {
                Log.e("DnsVpnService", "Failed to establish VPN interface (null)")
                stopSelf()
            }
        } catch (e: Exception) {
            Log.e("DnsVpnService", "Error connecting VPN", e)
            disconnectVpn()
            stopSelf()
        }
    }

    private fun startDnsForwarding(targetDnsIp: String) {
        val pfd = vpnInterface ?: return
        tunnelJob = serviceScope.launch {
            val inStream = FileInputStream(pfd.fileDescriptor)
            val outStream = FileOutputStream(pfd.fileDescriptor)
            val packet = ByteArray(32767)

            var forwardSocket: DatagramChannel? = null
            try {
                forwardSocket = DatagramChannel.open()
                protect(forwardSocket.socket())
                forwardSocket.connect(InetSocketAddress(targetDnsIp, 53))
                forwardSocket.configureBlocking(false)
            } catch (e: Exception) {
                Log.w("DnsVpnService", "Failed to open UDP forward channel", e)
            }

            try {
                while (isActive && _isRunning.value) {
                    val length = try {
                        inStream.read(packet)
                    } catch (e: Exception) {
                        break
                    }
                    if (length <= 0) continue

                    // Parse IP packet to check for UDP DNS payload
                    // IP Version: 4
                    val version = (packet[0].toInt() and 0xF0) ushr 4
                    if (version == 4 && length > 28) {
                        val headerLen = (packet[0].toInt() and 0x0F) * 4
                        val protocol = packet[9].toInt() and 0xFF
                        if (protocol == 17) { // UDP
                            val udpHeaderStart = headerLen
                            val dstPort = ((packet[udpHeaderStart + 2].toInt() and 0xFF) shl 8) or
                                    (packet[udpHeaderStart + 3].toInt() and 0xFF)
                            if (dstPort == 53 && forwardSocket != null && forwardSocket.isConnected) {
                                val udpPayloadStart = udpHeaderStart + 8
                                val udpPayloadLen = length - udpPayloadStart
                                if (udpPayloadLen > 0) {
                                    val sendBuf = ByteBuffer.wrap(packet, udpPayloadStart, udpPayloadLen)
                                    try {
                                        forwardSocket.write(sendBuf)
                                    } catch (_: Exception) {}
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.d("DnsVpnService", "Tunnel read ended", e)
            } finally {
                try { forwardSocket?.close() } catch (_: Exception) {}
            }
        }
    }

    private fun disconnectVpn() {
        tunnelJob?.cancel()
        tunnelJob = null
        try {
            vpnInterface?.close()
        } catch (e: Exception) {
            Log.e("DnsVpnService", "Error closing vpnInterface", e)
        }
        vpnInterface = null
        _isRunning.value = false
        _activeServerName.value = "Disconnected"
    }

    override fun onDestroy() {
        disconnectVpn()
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onRevoke() {
        disconnectVpn()
        super.onRevoke()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "DNS Pulse Active Protection",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows live status of active DNS Pulse VPN tunnel"
                setShowBadge(false)
            }
            val nm = getSystemService(NotificationManager::class.java)
            nm?.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(serverName: String): Notification {
        val launchIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            launchIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val stopIntent = Intent(this, DnsVpnService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            1,
            stopIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("DNS Pulse Protection Active")
            .setContentText("Routing DNS queries via $serverName")
            .setSmallIcon(android.R.drawable.ic_lock_idle_lock)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                "Disconnect",
                stopPendingIntent
            )
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }
}
