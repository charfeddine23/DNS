package com.example.service

import android.app.AlarmManager
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
import android.os.SystemClock
import android.system.ErrnoException
import android.system.Os
import android.system.OsConstants
import android.system.StructPollfd
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
import kotlinx.coroutines.withTimeoutOrNull
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress

class DnsVpnService : VpnService() {

    @Volatile
    private var vpnInterface: ParcelFileDescriptor? = null
    @Volatile
    private var wakeupPipeRead: ParcelFileDescriptor? = null
    @Volatile
    private var wakeupPipeWrite: ParcelFileDescriptor? = null
    @Volatile
    private var currentServerKey: String? = null
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    @OptIn(ExperimentalCoroutinesApi::class)
    private val dnsForwardDispatcher = Dispatchers.IO.limitedParallelism(16)
    private var tunnelJob: Job? = null
    private val outWriteLock = Any()

    data class PersistedVpnConfig(
        val serverName: String,
        val primaryIpv4: String,
        val secondaryIpv4: String,
        val primaryIpv6: String,
        val secondaryIpv6: String
    ) {
        fun key(): String = "$serverName|$primaryIpv4|$secondaryIpv4|$primaryIpv6|$secondaryIpv6"
    }

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
        private const val KEEP_ALIVE_REQUEST_CODE = 1002

        private const val VPN_PREFS_NAME = "dns_pulse_vpn_state"
        private const val KEY_SHOULD_STAY_ACTIVE = "should_stay_active"
        private const val KEY_SERVER_NAME = "server_name"
        private const val KEY_PRIMARY_IPV4 = "primary_ipv4"
        private const val KEY_SECONDARY_IPV4 = "secondary_ipv4"
        private const val KEY_PRIMARY_IPV6 = "primary_ipv6"
        private const val KEY_SECONDARY_IPV6 = "secondary_ipv6"

        @Volatile
        private var activeInstance: DnsVpnService? = null

        private val _isRunning = MutableStateFlow(false)
        val isRunning = _isRunning.asStateFlow()

        private val _activeServerName = MutableStateFlow("Disconnected")
        val activeServerName = _activeServerName.asStateFlow()

        internal fun persistActiveConfig(
            context: Context,
            active: Boolean,
            serverName: String,
            primaryIpv4: String,
            secondaryIpv4: String,
            primaryIpv6: String,
            secondaryIpv6: String
        ) {
            context.applicationContext
                .getSharedPreferences(VPN_PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(KEY_SHOULD_STAY_ACTIVE, active)
                .putString(KEY_SERVER_NAME, serverName)
                .putString(KEY_PRIMARY_IPV4, primaryIpv4)
                .putString(KEY_SECONDARY_IPV4, secondaryIpv4)
                .putString(KEY_PRIMARY_IPV6, primaryIpv6)
                .putString(KEY_SECONDARY_IPV6, secondaryIpv6)
                .commit()
        }

        internal fun setShouldStayActive(context: Context, active: Boolean) {
            context.applicationContext
                .getSharedPreferences(VPN_PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(KEY_SHOULD_STAY_ACTIVE, active)
                .commit()
        }

        fun isShouldStayActive(context: Context): Boolean {
            return context.applicationContext
                .getSharedPreferences(VPN_PREFS_NAME, Context.MODE_PRIVATE)
                .getBoolean(KEY_SHOULD_STAY_ACTIVE, false)
        }

        fun restoreLastKnownConfig(context: Context): PersistedVpnConfig {
            val prefs = context.applicationContext.getSharedPreferences(VPN_PREFS_NAME, Context.MODE_PRIVATE)
            return PersistedVpnConfig(
                serverName = prefs.getString(KEY_SERVER_NAME, null)?.takeIf { it.isNotBlank() } ?: "AdGuard DNS",
                primaryIpv4 = prefs.getString(KEY_PRIMARY_IPV4, null)?.takeIf { it.isNotBlank() } ?: "94.140.14.14",
                secondaryIpv4 = prefs.getString(KEY_SECONDARY_IPV4, null).orEmpty(),
                primaryIpv6 = prefs.getString(KEY_PRIMARY_IPV6, null).orEmpty(),
                secondaryIpv6 = prefs.getString(KEY_SECONDARY_IPV6, null).orEmpty()
            )
        }

        private fun buildStartIntent(context: Context, config: PersistedVpnConfig): Intent {
            return Intent(context, DnsVpnService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_SERVER_NAME, config.serverName)
                putExtra(EXTRA_PRIMARY_IPV4, config.primaryIpv4)
                putExtra(EXTRA_SECONDARY_IPV4, config.secondaryIpv4)
                putExtra(EXTRA_PRIMARY_IPV6, config.primaryIpv6)
                putExtra(EXTRA_SECONDARY_IPV6, config.secondaryIpv6)
            }
        }

        private fun cancelKeepAliveAlarm(context: Context) {
            try {
                val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
                val intent = Intent(context, DnsVpnService::class.java).apply { action = ACTION_START }
                val pi = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    PendingIntent.getForegroundService(
                        context,
                        KEEP_ALIVE_REQUEST_CODE,
                        intent,
                        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_NO_CREATE
                    )
                } else {
                    PendingIntent.getService(
                        context,
                        KEEP_ALIVE_REQUEST_CODE,
                        intent,
                        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_NO_CREATE
                    )
                }
                if (pi != null) {
                    alarmManager.cancel(pi)
                    pi.cancel()
                }
            } catch (_: Exception) {}
        }

        fun startVpn(
            context: Context,
            serverName: String,
            primaryIpv4: String,
            secondaryIpv4: String = "",
            primaryIpv6: String = "",
            secondaryIpv6: String = ""
        ) {
            val config = PersistedVpnConfig(
                serverName = serverName,
                primaryIpv4 = primaryIpv4,
                secondaryIpv4 = secondaryIpv4,
                primaryIpv6 = primaryIpv6,
                secondaryIpv6 = secondaryIpv6
            )
            persistActiveConfig(
                context = context,
                active = true,
                serverName = serverName,
                primaryIpv4 = primaryIpv4,
                secondaryIpv4 = secondaryIpv4,
                primaryIpv6 = primaryIpv6,
                secondaryIpv6 = secondaryIpv6
            )
            val intent = buildStartIntent(context, config)
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Unable to start DnsVpnService", e)
                setShouldStayActive(context, false)
                _isRunning.value = false
                _activeServerName.value = "Disconnected"
            }
        }

        fun stopVpn(context: Context) {
            setShouldStayActive(context, false)
            cancelKeepAliveAlarm(context.applicationContext)

            // Update state immediately for responsive UI
            _isRunning.value = false
            _activeServerName.value = "Disconnected"

            // Directly tear down active tunnel & foreground notification (stopService alone
            // does not destroy a VpnService while the system holds a VpnService binding)
            val instance = activeInstance
            if (instance != null) {
                instance.requestShutdown()
            } else {
                try {
                    val stopIntent = Intent(context, DnsVpnService::class.java).apply {
                        action = ACTION_STOP
                    }
                    context.startService(stopIntent)
                } catch (_: Exception) {
                    try {
                        context.stopService(Intent(context, DnsVpnService::class.java))
                    } catch (e: Exception) {
                        Log.w(TAG, "stopService fallback", e)
                    }
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        activeInstance = this
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        activeInstance = this
        when (intent?.action) {
            ACTION_STOP -> {
                setShouldStayActive(this, false)
                cancelKeepAliveAlarm(this)
                requestShutdown()
                return START_NOT_STICKY
            }
            ACTION_START -> {
                val serverName = intent.getStringExtra(EXTRA_SERVER_NAME) ?: "DNS Pulse"
                val pIpv4 = intent.getStringExtra(EXTRA_PRIMARY_IPV4)?.takeIf { it.isNotBlank() } ?: "94.140.14.14"
                val sIpv4 = intent.getStringExtra(EXTRA_SECONDARY_IPV4).orEmpty()
                val pIpv6 = intent.getStringExtra(EXTRA_PRIMARY_IPV6).orEmpty()
                val sIpv6 = intent.getStringExtra(EXTRA_SECONDARY_IPV6).orEmpty()

                val config = PersistedVpnConfig(serverName, pIpv4, sIpv4, pIpv6, sIpv6)
                return startOrKeepTunnel(config)
            }
            else -> {
                // System restarted service after task removal / process kill (START_STICKY with null intent)
                if (isShouldStayActive(this)) {
                    val restored = restoreLastKnownConfig(this)
                    return startOrKeepTunnel(restored)
                } else {
                    stopForegroundCompat()
                    stopSelf()
                    return START_NOT_STICKY
                }
            }
        }
    }

    private fun startOrKeepTunnel(config: PersistedVpnConfig): Int {
        persistActiveConfig(
            context = this,
            active = true,
            serverName = config.serverName,
            primaryIpv4 = config.primaryIpv4,
            secondaryIpv4 = config.secondaryIpv4,
            primaryIpv6 = config.primaryIpv6,
            secondaryIpv6 = config.secondaryIpv6
        )

        // Must enter foreground immediately on Android 8+ / 14+ with specialUse type
        try {
            startForegroundCompat(buildNotification(config.serverName))
        } catch (e: Exception) {
            Log.e(TAG, "Failed to enter foreground mode", e)
            setShouldStayActive(this, false)
            _isRunning.value = false
            _activeServerName.value = "Disconnected"
            stopSelf()
            return START_NOT_STICKY
        }

        // If the exact same tunnel is already live (e.g., triggered by keep-alive check), keep it untouched
        if (vpnInterface != null && _isRunning.value && currentServerKey == config.key()) {
            return START_STICKY
        }

        currentServerKey = config.key()

        // Signal previous tunnel loop to wake up and exit cleanly off Main Thread
        val previousJob = tunnelJob
        previousJob?.cancel()
        signalWakeupPipe()
        closeVpnInterfaceOnly()

        tunnelJob = serviceScope.launch {
            withTimeoutOrNull(400) {
                previousJob?.join()
            }
            connectVpnAsync(
                config.serverName,
                config.primaryIpv4,
                config.secondaryIpv4,
                config.primaryIpv6,
                config.secondaryIpv6
            )
        }
        return START_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        // Never tear down the VPN when the user swipes the app away from Recent Apps.
        // Schedule a rapid foreground-service revival safeguard on OEM ROMs that force-stop tasks on swipe.
        if (isShouldStayActive(this)) {
            try {
                val config = restoreLastKnownConfig(this)
                val restartIntent = buildStartIntent(applicationContext, config)
                val pi = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    PendingIntent.getForegroundService(
                        applicationContext,
                        KEEP_ALIVE_REQUEST_CODE,
                        restartIntent,
                        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                    )
                } else {
                    PendingIntent.getService(
                        applicationContext,
                        KEEP_ALIVE_REQUEST_CODE,
                        restartIntent,
                        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                    )
                }
                val alarmManager = getSystemService(Context.ALARM_SERVICE) as? AlarmManager
                alarmManager?.set(
                    AlarmManager.ELAPSED_REALTIME,
                    SystemClock.elapsedRealtime() + 800L,
                    pi
                )
            } catch (e: Exception) {
                Log.w(TAG, "onTaskRemoved keep-alive schedule skipped", e)
            }
        }
    }

    internal fun requestShutdown() {
        setShouldStayActive(this, false)
        cancelKeepAliveAlarm(this)
        currentServerKey = null
        disconnectVpn()
        stopForegroundCompat()
        stopSelf()
        try {
            applicationContext.stopService(Intent(applicationContext, DnsVpnService::class.java))
        } catch (_: Exception) {}
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
        closeWakeupPipe()
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

            val pipe = try {
                ParcelFileDescriptor.createPipe()
            } catch (_: Exception) {
                null
            }
            wakeupPipeRead = pipe?.get(0)
            wakeupPipeWrite = pipe?.get(1)

            vpnInterface = established
            _isRunning.value = true
            _activeServerName.value = serverName

            runDnsTunnelLoop(established, wakeupPipeRead, pIpv4)
        } catch (e: CancellationException) {
            if (vpnInterface === established) {
                closeVpnInterfaceOnly()
                closeWakeupPipe()
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

    private suspend fun runDnsTunnelLoop(
        pfd: ParcelFileDescriptor,
        wakeReadPfd: ParcelFileDescriptor?,
        fallbackIpv4Dns: String
    ) {
        val inStream = FileInputStream(pfd.fileDescriptor)
        val outStream = FileOutputStream(pfd.fileDescriptor)
        val packet = ByteArray(32767)

        try {
            if (wakeReadPfd != null) {
                val tunPoll = StructPollfd().apply {
                    fd = pfd.fileDescriptor
                    events = OsConstants.POLLIN.toShort()
                }
                val wakePoll = StructPollfd().apply {
                    fd = wakeReadPfd.fileDescriptor
                    events = OsConstants.POLLIN.toShort()
                }
                val pollFds = arrayOf(tunPoll, wakePoll)

                while (currentCoroutineContext().isActive && _isRunning.value && vpnInterface === pfd) {
                    tunPoll.revents = 0
                    wakePoll.revents = 0
                    val ready = try {
                        Os.poll(pollFds, -1)
                    } catch (e: ErrnoException) {
                        if (e.errno == OsConstants.EINTR) continue
                        break
                    } catch (_: Exception) {
                        break
                    }

                    if (!currentCoroutineContext().isActive || !_isRunning.value || vpnInterface !== pfd) {
                        break
                    }

                    val wakeEvents = wakePoll.revents.toInt()
                    if ((wakeEvents and (OsConstants.POLLIN or OsConstants.POLLHUP or OsConstants.POLLERR)) != 0) {
                        break
                    }

                    val tunEvents = tunPoll.revents.toInt()
                    if ((tunEvents and (OsConstants.POLLERR or OsConstants.POLLHUP or OsConstants.POLLNVAL)) != 0) {
                        break
                    }

                    if (ready > 0 && (tunEvents and OsConstants.POLLIN) != 0) {
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
                }
            } else {
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

    private fun signalWakeupPipe() {
        try {
            wakeupPipeWrite?.let { writePfd ->
                FileOutputStream(writePfd.fileDescriptor).write(1)
            }
        } catch (_: Exception) {}
    }

    private fun closeWakeupPipe() {
        try {
            wakeupPipeWrite?.close()
        } catch (_: Exception) {}
        wakeupPipeWrite = null
        try {
            wakeupPipeRead?.close()
        } catch (_: Exception) {}
        wakeupPipeRead = null
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
        signalWakeupPipe()
        tunnelJob?.cancel()
        tunnelJob = null
        closeVpnInterfaceOnly()
        closeWakeupPipe()
    }

    override fun onDestroy() {
        if (activeInstance === this) {
            activeInstance = null
        }
        disconnectVpn()
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onRevoke() {
        requestShutdown()
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
