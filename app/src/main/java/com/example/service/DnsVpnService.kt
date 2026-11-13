package com.example.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress

class DnsVpnService : VpnService() {

    private var vpnInterface: ParcelFileDescriptor? = null
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    @OptIn(ExperimentalCoroutinesApi::class)
    private val dnsForwardDispatcher = Dispatchers.IO.limitedParallelism(16)
    private var tunnelJob: Job? = null
    private val outWriteLock = Any()

    companion object {
        private const val TAG = "DnsVpnService"
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
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Unable to start DnsVpnService", e)
                _isRunning.value = false
                _activeServerName.value = "Disconnected"
            }
        }

        fun stopVpn(context: Context) {
            try {
                context.stopService(Intent(context, DnsVpnService::class.java))
            } catch (e: Exception) {
                Log.w(TAG, "stopService fallback", e)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                serviceScope.launch {
                    disconnectVpn()
                    stopForegroundCompat()
                    stopSelf()
                }
            }
            ACTION_START -> {
                val serverName = intent.getStringExtra(EXTRA_SERVER_NAME) ?: "DNS Pulse"
                val pIpv4 = intent.getStringExtra(EXTRA_PRIMARY_IPV4)?.takeIf { it.isNotBlank() } ?: "94.140.14.14"
                val sIpv4 = intent.getStringExtra(EXTRA_SECONDARY_IPV4).orEmpty()
                val pIpv6 = intent.getStringExtra(EXTRA_PRIMARY_IPV6).orEmpty()
                val sIpv6 = intent.getStringExtra(EXTRA_SECONDARY_IPV6).orEmpty()

                // Must enter foreground immediately on Android 8+ / 14+ with specialUse type
                try {
                    startForegroundCompat(buildNotification(serverName))
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to enter foreground mode", e)
                    _isRunning.value = false
                    _activeServerName.value = "Disconnected"
                    stopSelf()
                    return START_NOT_STICKY
                }

                // Run VPN establishment & packet loop off the Main Thread to avoid UI freezes / ANRs
                val previousJob = tunnelJob
                previousJob?.cancel()
                closeVpnInterfaceOnly()

                tunnelJob = serviceScope.launch {
                    previousJob?.join()
                    connectVpnAsync(serverName, pIpv4, sIpv4, pIpv6, sIpv6)
                }
            }
        }
        return START_NOT_STICKY
    }

    private fun startForegroundCompat(notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun stopForegroundCompat() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                stopForeground(STOP_FOREGROUND_REMOVE)
            } else {
                @Suppress("DEPRECATION")
                stopForeground(true)
            }
        } catch (e: Exception) {
            Log.w(TAG, "stopForegroundCompat ignored", e)
        }
    }

    private suspend fun connectVpnAsync(
        serverName: String,
        pIpv4: String,
        sIpv4: String,
        pIpv6: String,
        sIpv6: String
    ) {
        closeVpnInterfaceOnly()
        var established: ParcelFileDescriptor? = null

        try {
            val builder = Builder().apply {
                setSession("DNS Pulse ($serverName)")
                setMtu(1500)
                addAddress("10.233.1.2", 30)
                addDnsServer(pIpv4)
                addRoute(pIpv4, 32)

                if (sIpv4.isNotBlank()) {
                    addDnsServer(sIpv4)
                    addRoute(sIpv4, 32)
                }

                if (pIpv6.isNotBlank()) {
                    try {
                        addAddress("fd00:1:fd00:1::2", 126)
                        addDnsServer(pIpv6)
                        addRoute(pIpv6, 128)
                        if (sIpv6.isNotBlank()) {
                            addDnsServer(sIpv6)
                            addRoute(sIpv6, 128)
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "IPv6 routing optional skip", e)
                    }
                }

                try {
                    addDisallowedApplication(packageName)
                } catch (e: Exception) {
                    Log.w(TAG, "Cannot disallow self package", e)
                }
            }

            established = builder.establish()
            if (established == null) {
                Log.e(TAG, "Failed to establish VPN interface (null)")
                _isRunning.value = false
                _activeServerName.value = "Disconnected"
                stopForegroundCompat()
                stopSelf()
                return
            }

            vpnInterface = established
            _isRunning.value = true
            _activeServerName.value = serverName

            runDnsTunnelLoop(established, pIpv4)
        } catch (e: CancellationException) {
            if (vpnInterface === established) {
                closeVpnInterfaceOnly()
            } else {
                try {
                    established?.close()
                } catch (_: Exception) {}
            }
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Error establishing/running VPN", e)
            if (vpnInterface === established || vpnInterface == null) {
                disconnectVpn()
                stopForegroundCompat()
                stopSelf()
            } else {
                try {
                    established?.close()
                } catch (_: Exception) {}
            }
        }
    }

    private suspend fun runDnsTunnelLoop(pfd: ParcelFileDescriptor, fallbackIpv4Dns: String) {
        val inStream = FileInputStream(pfd.fileDescriptor)
        val outStream = FileOutputStream(pfd.fileDescriptor)
        val packet = ByteArray(32767)

        try {
            while (currentCoroutineContext().isActive && _isRunning.value && vpnInterface === pfd) {
                val length = try {
                    inStream.read(packet)
                } catch (_: Exception) {
                    break
                }
                if (length <= 0) continue

                val version = (packet[0].toInt() and 0xF0) ushr 4
                when (version) {
                    4 -> handleIpv4Packet(packet, length, outStream, fallbackIpv4Dns)
                    6 -> handleIpv6Packet(packet, length, outStream, fallbackIpv4Dns)
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.d(TAG, "Tunnel loop ended", e)
        }
    }

    private fun handleIpv4Packet(
        packet: ByteArray,
        length: Int,
        outStream: FileOutputStream,
        fallbackIpv4Dns: String
    ) {
        if (length < 28) return
        val ihl = (packet[0].toInt() and 0x0F) * 4
        if (ihl < 20 || length < ihl + 8) return
        val protocol = packet[9].toInt() and 0xFF
        if (protocol != 17) return // UDP only

        val dstPort = ((packet[ihl + 2].toInt() and 0xFF) shl 8) or (packet[ihl + 3].toInt() and 0xFF)
        if (dstPort != 53) return

        val srcIp = packet.copyOfRange(12, 16)
        val dstIp = packet.copyOfRange(16, 20)
        val srcPort = ((packet[ihl].toInt() and 0xFF) shl 8) or (packet[ihl + 1].toInt() and 0xFF)
        val udpPayloadOffset = ihl + 8
        val dnsQueryLen = length - udpPayloadOffset
        if (dnsQueryLen <= 0) return

        val dnsQuery = packet.copyOfRange(udpPayloadOffset, length)

        serviceScope.launch(dnsForwardDispatcher) {
            val responseData = forwardDnsUdp(dnsQuery, dstIp, fallbackIpv4Dns) ?: return@launch
            val responsePacket = DnsPacketBuilder.buildIpv4UdpResponse(
                clientIp = srcIp,
                clientPort = srcPort,
                serverIp = dstIp,
                serverPort = 53,
                dnsPayload = responseData
            )
            synchronized(outWriteLock) {
                try {
                    outStream.write(responsePacket)
                } catch (_: Exception) {}
            }
        }
    }

    private fun handleIpv6Packet(
        packet: ByteArray,
        length: Int,
        outStream: FileOutputStream,
        fallbackIpv4Dns: String
    ) {
        if (length < 48) return
        val nextHeader = packet[6].toInt() and 0xFF
        if (nextHeader != 17) return // UDP only

        val dstPort = ((packet[42].toInt() and 0xFF) shl 8) or (packet[43].toInt() and 0xFF)
        if (dstPort != 53) return

        val srcIp = packet.copyOfRange(8, 24)
        val dstIp = packet.copyOfRange(24, 40)
        val srcPort = ((packet[40].toInt() and 0xFF) shl 8) or (packet[41].toInt() and 0xFF)
        val dnsQueryLen = length - 48
        if (dnsQueryLen <= 0) return

        val dnsQuery = packet.copyOfRange(48, length)

        serviceScope.launch(dnsForwardDispatcher) {
            val responseData = forwardDnsUdp(dnsQuery, dstIp, fallbackIpv4Dns) ?: return@launch
            val responsePacket = DnsPacketBuilder.buildIpv6UdpResponse(
                clientIp = srcIp,
                clientPort = srcPort,
                serverIp = dstIp,
                serverPort = 53,
                dnsPayload = responseData
            )
            synchronized(outWriteLock) {
                try {
                    outStream.write(responsePacket)
                } catch (_: Exception) {}
            }
        }
    }

    private fun forwardDnsUdp(
        dnsQuery: ByteArray,
        targetIpBytes: ByteArray,
        fallbackIpv4Dns: String
    ): ByteArray? {
        return try {
            val targetAddr = InetAddress.getByAddress(targetIpBytes)
            forwardToAddress(dnsQuery, targetAddr)
        } catch (_: Exception) {
            try {
                forwardToAddress(dnsQuery, InetAddress.getByName(fallbackIpv4Dns))
            } catch (_: Exception) {
                null
            }
        }
    }

    private fun forwardToAddress(dnsQuery: ByteArray, address: InetAddress): ByteArray? {
        DatagramSocket().use { socket ->
            if (!protect(socket)) {
                Log.w(TAG, "protect(socket) failed")
            }
            socket.soTimeout = 4000
            val outPacket = DatagramPacket(dnsQuery, dnsQuery.size, address, 53)
            socket.send(outPacket)

            val respBuffer = ByteArray(4096)
            val inPacket = DatagramPacket(respBuffer, respBuffer.size)
            socket.receive(inPacket)
            return respBuffer.copyOfRange(0, inPacket.length)
        }
    }

    private fun closeVpnInterfaceOnly() {
        try {
            vpnInterface?.close()
        } catch (e: Exception) {
            Log.e(TAG, "Error closing vpnInterface", e)
        }
        vpnInterface = null
    }

    private fun disconnectVpn() {
        _isRunning.value = false
        _activeServerName.value = "Disconnected"
        tunnelJob?.cancel()
        tunnelJob = null
        closeVpnInterfaceOnly()
    }

    override fun onDestroy() {
        disconnectVpn()
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onRevoke() {
        disconnectVpn()
        stopForegroundCompat()
        stopSelf()
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

object DnsPacketBuilder {

    fun buildIpv4UdpResponse(
        clientIp: ByteArray,
        clientPort: Int,
        serverIp: ByteArray,
        serverPort: Int,
        dnsPayload: ByteArray
    ): ByteArray {
        val udpLength = 8 + dnsPayload.size
        val totalLength = 20 + udpLength
        val packet = ByteArray(totalLength)

        // IPv4 Header (20 bytes)
        packet[0] = 0x45.toByte() // Version 4, IHL 5 (20 bytes)
        packet[1] = 0x00
        writeShort(packet, 2, totalLength)
        writeShort(packet, 4, 0) // Identification
        writeShort(packet, 6, 0x4000) // Don't fragment
        packet[8] = 64 // TTL
        packet[9] = 17 // UDP
        writeShort(packet, 10, 0) // Checksum placeholder
        System.arraycopy(serverIp, 0, packet, 12, 4) // Src IP = DNS Server
        System.arraycopy(clientIp, 0, packet, 16, 4) // Dst IP = Client

        val ipChecksum = onesComplementChecksum(packet, 0, 20)
        writeShort(packet, 10, ipChecksum)

        // UDP Header (8 bytes)
        writeShort(packet, 20, serverPort)
        writeShort(packet, 22, clientPort)
        writeShort(packet, 24, udpLength)
        writeShort(packet, 26, 0) // Checksum placeholder
        System.arraycopy(dnsPayload, 0, packet, 28, dnsPayload.size)

        val udpChecksum = computeIpv4UdpChecksum(serverIp, clientIp, packet, 20, udpLength)
        writeShort(packet, 26, if (udpChecksum == 0) 0xFFFF else udpChecksum)

        return packet
    }

    fun buildIpv6UdpResponse(
        clientIp: ByteArray,
        clientPort: Int,
        serverIp: ByteArray,
        serverPort: Int,
        dnsPayload: ByteArray
    ): ByteArray {
        val udpLength = 8 + dnsPayload.size
        val totalLength = 40 + udpLength
        val packet = ByteArray(totalLength)

        // IPv6 Header (40 bytes)
        packet[0] = 0x60.toByte() // Version 6
        packet[1] = 0x00
        packet[2] = 0x00
        packet[3] = 0x00
        writeShort(packet, 4, udpLength) // Payload length
        packet[6] = 17 // Next Header = UDP
        packet[7] = 64 // Hop Limit
        System.arraycopy(serverIp, 0, packet, 8, 16)
        System.arraycopy(clientIp, 0, packet, 24, 16)

        // UDP Header (8 bytes)
        writeShort(packet, 40, serverPort)
        writeShort(packet, 42, clientPort)
        writeShort(packet, 44, udpLength)
        writeShort(packet, 46, 0)
        System.arraycopy(dnsPayload, 0, packet, 48, dnsPayload.size)

        val udpChecksum = computeIpv6UdpChecksum(serverIp, clientIp, packet, 40, udpLength)
        writeShort(packet, 46, if (udpChecksum == 0) 0xFFFF else udpChecksum)

        return packet
    }

    private fun computeIpv4UdpChecksum(
        srcIp: ByteArray,
        dstIp: ByteArray,
        packet: ByteArray,
        udpOffset: Int,
        udpLength: Int
    ): Int {
        val pseudo = ByteArray(12 + udpLength)
        System.arraycopy(srcIp, 0, pseudo, 0, 4)
        System.arraycopy(dstIp, 0, pseudo, 4, 4)
        pseudo[8] = 0
        pseudo[9] = 17
        writeShort(pseudo, 10, udpLength)
        System.arraycopy(packet, udpOffset, pseudo, 12, udpLength)
        return onesComplementChecksum(pseudo, 0, pseudo.size)
    }

    private fun computeIpv6UdpChecksum(
        srcIp: ByteArray,
        dstIp: ByteArray,
        packet: ByteArray,
        udpOffset: Int,
        udpLength: Int
    ): Int {
        val pseudo = ByteArray(40 + udpLength)
        System.arraycopy(srcIp, 0, pseudo, 0, 16)
        System.arraycopy(dstIp, 0, pseudo, 16, 16)
        pseudo[32] = ((udpLength ushr 24) and 0xFF).toByte()
        pseudo[33] = ((udpLength ushr 16) and 0xFF).toByte()
        pseudo[34] = ((udpLength ushr 8) and 0xFF).toByte()
        pseudo[35] = (udpLength and 0xFF).toByte()
        pseudo[39] = 17
        System.arraycopy(packet, udpOffset, pseudo, 40, udpLength)
        return onesComplementChecksum(pseudo, 0, pseudo.size)
    }

    internal fun onesComplementChecksum(data: ByteArray, offset: Int, length: Int): Int {
        var sum = 0
        var i = offset
        val end = offset + length
        while (i + 1 < end) {
            val word = ((data[i].toInt() and 0xFF) shl 8) or (data[i + 1].toInt() and 0xFF)
            sum += word
            i += 2
        }
        if (i < end) {
            sum += (data[i].toInt() and 0xFF) shl 8
        }
        while ((sum ushr 16) != 0) {
            sum = (sum and 0xFFFF) + (sum ushr 16)
        }
        return sum.inv() and 0xFFFF
    }

    private fun writeShort(buf: ByteArray, offset: Int, value: Int) {
        buf[offset] = ((value ushr 8) and 0xFF).toByte()
        buf[offset + 1] = (value and 0xFF).toByte()
    }
}
