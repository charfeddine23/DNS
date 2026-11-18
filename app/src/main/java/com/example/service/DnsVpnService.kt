package com.example.service

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.ConnectivityManager
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
import com.example.data.repository.DnsAnalyticsRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

class DnsVpnService : VpnService() {

    private class TunnelSession(
        val generation: Long,
        val pfd: ParcelFileDescriptor,
        val wakeReadPfd: ParcelFileDescriptor?,
        val wakeWritePfd: ParcelFileDescriptor?
    ) {
        val writeLock = Any()
        val closed = AtomicBoolean(false)

        fun wake() {
            val writePfd = wakeWritePfd ?: return
            if (closed.get()) return
            try {
                Os.write(writePfd.fileDescriptor, ByteBuffer.wrap(byteArrayOf(1)))
            } catch (_: Throwable) {
                try {
                    FileOutputStream(writePfd.fileDescriptor).write(1)
                } catch (_: Throwable) {}
            }
        }

        fun writePacket(packet: ByteArray) {
            if (closed.get()) return
            synchronized(writeLock) {
                if (closed.get()) return
                try {
                    if (pfd.fileDescriptor.valid()) {
                        FileOutputStream(pfd.fileDescriptor).write(packet)
                    }
                } catch (_: Throwable) {}
            }
        }

        fun closeOnce() {
            if (!closed.compareAndSet(false, true)) return
            synchronized(writeLock) {
                try {
                    wakeWritePfd?.close()
                } catch (_: Throwable) {}
                try {
                    wakeReadPfd?.close()
                } catch (_: Throwable) {}
                try {
                    pfd.close()
                } catch (e: Throwable) {
                    Log.w(TAG, "Ignored error closing vpnInterface", e)
                }
            }
        }
    }

    @Volatile
    private var activeSession: TunnelSession? = null
    @Volatile
    private var currentServerKey: String? = null
    @Volatile
    private var isInForeground: Boolean = false
    private val tunnelGeneration = AtomicLong(0L)
    private val tunnelMutex = Mutex()
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    @OptIn(ExperimentalCoroutinesApi::class)
    private val dnsForwardDispatcher = Dispatchers.IO.limitedParallelism(24)
    private val forwardConcurrencyGate = Semaphore(permits = 48)
    @Volatile
    private var tunnelJob: Job? = null
    private val analyticsRepo: DnsAnalyticsRepository by lazy {
        DnsAnalyticsRepository.getInstance(applicationContext)
    }

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

        private const val TUN_CLIENT_IPV4 = "10.233.1.2"
        private const val TUN_VIRTUAL_DNS_IPV4 = "10.233.1.1"
        private const val DEFAULT_UPSTREAM_IPV4 = "94.140.14.14"

        private const val NOTIFICATION_CHANNEL_ID = "dns_pulse_vpn_channel"
        private const val NOTIFICATION_ID = 1001
        private const val KEEP_ALIVE_REQUEST_CODE = 1002
        private const val KEEP_ALIVE_DELAY_MS = 800L
        private const val DNS_SOCKET_TIMEOUT_MS = 1800

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
            config: PersistedVpnConfig
        ) {
            context.applicationContext
                .getSharedPreferences(VPN_PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(KEY_SHOULD_STAY_ACTIVE, active)
                .putString(KEY_SERVER_NAME, config.serverName)
                .putString(KEY_PRIMARY_IPV4, config.primaryIpv4)
                .putString(KEY_SECONDARY_IPV4, config.secondaryIpv4)
                .putString(KEY_PRIMARY_IPV6, config.primaryIpv6)
                .putString(KEY_SECONDARY_IPV6, config.secondaryIpv6)
                .apply()
        }

        internal fun setShouldStayActive(context: Context, active: Boolean) {
            context.applicationContext
                .getSharedPreferences(VPN_PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(KEY_SHOULD_STAY_ACTIVE, active)
                .apply()
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
                primaryIpv4 = prefs.getString(KEY_PRIMARY_IPV4, null)?.takeIf { it.isNotBlank() } ?: DEFAULT_UPSTREAM_IPV4,
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
            } catch (_: Throwable) {}
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
                serverName = serverName.ifBlank { "DNS Pulse" },
                primaryIpv4 = primaryIpv4.trim().ifBlank { DEFAULT_UPSTREAM_IPV4 },
                secondaryIpv4 = secondaryIpv4.trim(),
                primaryIpv6 = primaryIpv6.trim(),
                secondaryIpv6 = secondaryIpv6.trim()
            )
            persistActiveConfig(context = context, active = true, config = config)
            launchForegroundService(context, buildStartIntent(context, config))
        }

        private fun launchForegroundService(context: Context, intent: Intent) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (e: Throwable) {
                Log.e(TAG, "Unable to start DnsVpnService", e)
                setShouldStayActive(context, false)
                _isRunning.value = false
                _activeServerName.value = "Disconnected"
            }
        }

        fun stopVpn(context: Context) {
            setShouldStayActive(context, false)
            cancelKeepAliveAlarm(context.applicationContext)

            _isRunning.value = false
            _activeServerName.value = "Disconnected"

            val instance = activeInstance
            if (instance != null) {
                instance.requestShutdown()
            } else {
                try {
                    val stopIntent = Intent(context, DnsVpnService::class.java).apply {
                        action = ACTION_STOP
                    }
                    context.startService(stopIntent)
                } catch (_: Throwable) {
                    try {
                        context.stopService(Intent(context, DnsVpnService::class.java))
                    } catch (e: Throwable) {
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
        val isStickyRetry = (flags and START_FLAG_RETRY) != 0

        return when (intent?.action) {
            ACTION_STOP -> {
                ensureForegroundBeforeStopOnOreo()
                requestShutdown()
                START_NOT_STICKY
            }
            ACTION_START -> {
                val config = PersistedVpnConfig(
                    serverName = intent.getStringExtra(EXTRA_SERVER_NAME)?.takeIf { it.isNotBlank() } ?: "DNS Pulse",
                    primaryIpv4 = intent.getStringExtra(EXTRA_PRIMARY_IPV4)?.trim()?.takeIf { it.isNotBlank() } ?: DEFAULT_UPSTREAM_IPV4,
                    secondaryIpv4 = intent.getStringExtra(EXTRA_SECONDARY_IPV4).orEmpty().trim(),
                    primaryIpv6 = intent.getStringExtra(EXTRA_PRIMARY_IPV6).orEmpty().trim(),
                    secondaryIpv6 = intent.getStringExtra(EXTRA_SECONDARY_IPV6).orEmpty().trim()
                )
                startOrMaintainTunnel(config)
            }
            else -> {
                // START_STICKY restarts deliver a null intent (or START_FLAG_RETRY without action).
                // Restore persisted VPN tunnel config automatically if it was active before process kill.
                if (isShouldStayActive(this) || (isStickyRetry && isShouldStayActive(this))) {
                    startOrMaintainTunnel(restoreLastKnownConfig(this))
                } else {
                    ensureForegroundBeforeStopOnOreo()
                    stopForegroundCompat()
                    stopSelf()
                    START_NOT_STICKY
                }
            }
        }
    }

    private fun ensureForegroundBeforeStopOnOreo() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !isInForeground) {
            try {
                startForegroundCompat(buildNotification("DNS Pulse"))
            } catch (_: Throwable) {}
        }
    }

    private fun startOrMaintainTunnel(config: PersistedVpnConfig): Int {
        persistActiveConfig(context = this, active = true, config = config)

        try {
            startForegroundCompat(buildNotification(config.serverName))
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to enter foreground mode", e)
            setShouldStayActive(this, false)
            _isRunning.value = false
            _activeServerName.value = "Disconnected"
            stopSelf()
            return START_NOT_STICKY
        }

        // Maintain existing tunnel without packet interruption if already running on same server
        val existingSession = activeSession
        if (existingSession != null && !existingSession.closed.get() && _isRunning.value && currentServerKey == config.key()) {
            return START_STICKY
        }

        currentServerKey = config.key()
        val generation = tunnelGeneration.incrementAndGet()
        val previousJob = tunnelJob
        activeSession?.wake()
        previousJob?.cancel()

        tunnelJob = serviceScope.launch {
            tunnelMutex.withLock {
                if (tunnelGeneration.get() != generation || !currentCoroutineContext().isActive) {
                    return@withLock
                }
                if (previousJob != null) {
                    withTimeoutOrNull(800L) {
                        previousJob.join()
                    }
                }
                // Guarantee previous session resources are closed before opening a new TUN interface
                activeSession?.let { oldSession ->
                    oldSession.closeOnce()
                    if (activeSession === oldSession) {
                        activeSession = null
                    }
                }
                connectVpnAsync(config, generation)
            }
        }
        return START_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        if (!isShouldStayActive(this)) return

        val config = restoreLastKnownConfig(this)
        val restartIntent = buildStartIntent(applicationContext, config)

        // If tunnel interface was dropped during task removal, explicitly re-launch immediately
        val currentSession = activeSession
        if (currentSession == null || currentSession.closed.get() || !_isRunning.value) {
            launchForegroundService(applicationContext, restartIntent)
        }

        // Schedule Doze-tolerant AlarmManager revival in case OEM ROM kills the process post-swipe
        try {
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
            val triggerAt = SystemClock.elapsedRealtime() + KEEP_ALIVE_DELAY_MS
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager?.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAt, pi)
            } else {
                alarmManager?.set(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAt, pi)
            }
        } catch (e: Throwable) {
            Log.w(TAG, "onTaskRemoved keep-alive schedule skipped", e)
        }
    }

    internal fun requestShutdown() {
        setShouldStayActive(this, false)
        cancelKeepAliveAlarm(this)
        currentServerKey = null
        tunnelGeneration.incrementAndGet()
        disconnectVpn()
        stopForegroundCompat()
        stopSelf()
        try {
            applicationContext.stopService(Intent(applicationContext, DnsVpnService::class.java))
        } catch (_: Throwable) {}
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
        isInForeground = true
    }

    private fun stopForegroundCompat() {
        isInForeground = false
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                stopForeground(STOP_FOREGROUND_REMOVE)
            } else {
                @Suppress("DEPRECATION")
                stopForeground(true)
            }
        } catch (e: Throwable) {
            Log.w(TAG, "stopForegroundCompat ignored", e)
        }
    }

    private suspend fun connectVpnAsync(config: PersistedVpnConfig, generation: Long) {
        if (tunnelGeneration.get() != generation || !currentCoroutineContext().isActive) return

        val upstreamIpv4Targets = resolveLiteralIpv4List(
            config.primaryIpv4,
            config.secondaryIpv4,
            DEFAULT_UPSTREAM_IPV4
        )
        val upstreamIpv6Targets = resolveLiteralIpv6List(
            config.primaryIpv6,
            config.secondaryIpv6
        )

        var session: TunnelSession? = null
        try {
            val builder = Builder().apply {
                setSession("DNS Pulse (${config.serverName})")
                setMtu(1500)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    setMetered(false)
                }
                addAddress(TUN_CLIENT_IPV4, 30)
                // Virtual local TUN DNS endpoint avoids routing collisions with HTTPS/DoT to real DNS IPs
                // and prevents IPv6-only DNS blackhole stalls on IPv4-only Wi-Fi/mobile networks.
                addDnsServer(TUN_VIRTUAL_DNS_IPV4)
                addRoute(TUN_VIRTUAL_DNS_IPV4, 32)

                try {
                    addDisallowedApplication(packageName)
                } catch (e: Throwable) {
                    Log.w(TAG, "Cannot disallow self package", e)
                }
            }

            val established = builder.establish()
            if (established == null) {
                Log.e(TAG, "Failed to establish VPN interface (null)")
                setShouldStayActive(this, false)
                cancelKeepAliveAlarm(this)
                currentServerKey = null
                _isRunning.value = false
                _activeServerName.value = "Disconnected"
                stopForegroundCompat()
                stopSelf()
                return
            }

            val pipe = try {
                ParcelFileDescriptor.createPipe()
            } catch (_: Throwable) {
                null
            }

            session = TunnelSession(
                generation = generation,
                pfd = established,
                wakeReadPfd = pipe?.get(0),
                wakeWritePfd = pipe?.get(1)
            )

            if (tunnelGeneration.get() != generation || !currentCoroutineContext().isActive) {
                session.closeOnce()
                return
            }

            activeSession = session
            _isRunning.value = true
            _activeServerName.value = config.serverName

            runDnsTunnelLoop(session, upstreamIpv4Targets, upstreamIpv6Targets)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            Log.e(TAG, "Error establishing/running VPN", e)
            if (tunnelGeneration.get() == generation) {
                _isRunning.value = false
                _activeServerName.value = "Disconnected"
                stopForegroundCompat()
                stopSelf()
            }
        } finally {
            withContext(NonCancellable) {
                session?.let { s ->
                    if (activeSession === s) {
                        activeSession = null
                    }
                    s.closeOnce()
                }
            }
        }
    }

    private suspend fun runDnsTunnelLoop(
        session: TunnelSession,
        upstreamIpv4Targets: List<InetAddress>,
        upstreamIpv6Targets: List<InetAddress>
    ) {
        val pfd = session.pfd
        val wakeReadPfd = session.wakeReadPfd
        val inStream = FileInputStream(pfd.fileDescriptor)
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

                while (
                    currentCoroutineContext().isActive &&
                    tunnelGeneration.get() == session.generation &&
                    !session.closed.get()
                ) {
                    tunPoll.revents = 0
                    wakePoll.revents = 0
                    val ready = try {
                        Os.poll(pollFds, -1)
                    } catch (e: ErrnoException) {
                        if (e.errno == OsConstants.EINTR) continue
                        break
                    } catch (_: Throwable) {
                        break
                    }

                    if (
                        !currentCoroutineContext().isActive ||
                        tunnelGeneration.get() != session.generation ||
                        session.closed.get()
                    ) {
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
                        } catch (e: ErrnoException) {
                            if (e.errno == OsConstants.EINTR || e.errno == OsConstants.EAGAIN) continue
                            break
                        } catch (_: Throwable) {
                            break
                        }
                        if (length <= 0) continue
                        dispatchDnsPacket(packet, length, session, upstreamIpv4Targets, upstreamIpv6Targets)
                    }
                }
            } else {
                while (
                    currentCoroutineContext().isActive &&
                    tunnelGeneration.get() == session.generation &&
                    !session.closed.get()
                ) {
                    val length = try {
                        inStream.read(packet)
                    } catch (e: ErrnoException) {
                        if (e.errno == OsConstants.EINTR || e.errno == OsConstants.EAGAIN) continue
                        break
                    } catch (_: Throwable) {
                        break
                    }
                    if (length <= 0) continue
                    dispatchDnsPacket(packet, length, session, upstreamIpv4Targets, upstreamIpv6Targets)
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            Log.d(TAG, "Tunnel loop ended", e)
        }
    }

    private fun dispatchDnsPacket(
        packet: ByteArray,
        length: Int,
        session: TunnelSession,
        upstreamIpv4Targets: List<InetAddress>,
        upstreamIpv6Targets: List<InetAddress>
    ) {
        if (length < 28 || length > packet.size) return
        val version = (packet[0].toInt() and 0xF0) ushr 4
        when (version) {
            4 -> handleIpv4Packet(packet, length, session, upstreamIpv4Targets)
            6 -> handleIpv6Packet(packet, length, session, upstreamIpv6Targets, upstreamIpv4Targets)
        }
    }

    private fun handleIpv4Packet(
        packet: ByteArray,
        length: Int,
        session: TunnelSession,
        upstreamIpv4Targets: List<InetAddress>
    ) {
        if (length < 28) return
        val ihl = (packet[0].toInt() and 0x0F) * 4
        if (ihl < 20 || length < ihl + 8) return
        if ((packet[9].toInt() and 0xFF) != 17) return // UDP only

        val dstPort = ((packet[ihl + 2].toInt() and 0xFF) shl 8) or (packet[ihl + 3].toInt() and 0xFF)
        if (dstPort != 53) return

        val srcIp = packet.copyOfRange(12, 16)
        val dstIp = packet.copyOfRange(16, 20)
        val srcPort = ((packet[ihl].toInt() and 0xFF) shl 8) or (packet[ihl + 1].toInt() and 0xFF)
        val udpPayloadOffset = ihl + 8
        if (length - udpPayloadOffset <= 0) return

        if (!forwardConcurrencyGate.tryAcquire()) {
            return
        }

        val dnsQuery = packet.copyOfRange(udpPayloadOffset, length)
        serviceScope.launch(dnsForwardDispatcher) {
            try {
                if (session.closed.get() || tunnelGeneration.get() != session.generation) return@launch
                val startedAtMs = SystemClock.elapsedRealtime()
                val responseData = forwardDnsUdp(dnsQuery, upstreamIpv4Targets) ?: return@launch
                val rttMs = (SystemClock.elapsedRealtime() - startedAtMs).coerceAtLeast(0L)
                runCatching {
                    DnsWireParser.analyzeTransaction(dnsQuery, responseData)?.let { analysis ->
                        analyticsRepo.recordQuery(analysis, rttMs)
                    }
                }
                if (session.closed.get() || tunnelGeneration.get() != session.generation) return@launch
                val responsePacket = DnsPacketBuilder.buildIpv4UdpResponse(
                    clientIp = srcIp,
                    clientPort = srcPort,
                    serverIp = dstIp,
                    serverPort = 53,
                    dnsPayload = responseData
                )
                session.writePacket(responsePacket)
            } finally {
                forwardConcurrencyGate.release()
            }
        }
    }

    private fun handleIpv6Packet(
        packet: ByteArray,
        length: Int,
        session: TunnelSession,
        upstreamIpv6Targets: List<InetAddress>,
        upstreamIpv4Targets: List<InetAddress>
    ) {
        if (length < 48) return
        if ((packet[6].toInt() and 0xFF) != 17) return // UDP only

        val dstPort = ((packet[42].toInt() and 0xFF) shl 8) or (packet[43].toInt() and 0xFF)
        if (dstPort != 53) return

        val srcIp = packet.copyOfRange(8, 24)
        val dstIp = packet.copyOfRange(24, 40)
        val srcPort = ((packet[40].toInt() and 0xFF) shl 8) or (packet[41].toInt() and 0xFF)
        if (length - 48 <= 0) return

        if (!forwardConcurrencyGate.tryAcquire()) {
            return
        }

        val dnsQuery = packet.copyOfRange(48, length)
        val targets = upstreamIpv6Targets + upstreamIpv4Targets
        serviceScope.launch(dnsForwardDispatcher) {
            try {
                if (session.closed.get() || tunnelGeneration.get() != session.generation) return@launch
                val startedAtMs = SystemClock.elapsedRealtime()
                val responseData = forwardDnsUdp(dnsQuery, targets) ?: return@launch
                val rttMs = (SystemClock.elapsedRealtime() - startedAtMs).coerceAtLeast(0L)
                runCatching {
                    DnsWireParser.analyzeTransaction(dnsQuery, responseData)?.let { analysis ->
                        analyticsRepo.recordQuery(analysis, rttMs)
                    }
                }
                if (session.closed.get() || tunnelGeneration.get() != session.generation) return@launch
                val responsePacket = DnsPacketBuilder.buildIpv6UdpResponse(
                    clientIp = srcIp,
                    clientPort = srcPort,
                    serverIp = dstIp,
                    serverPort = 53,
                    dnsPayload = responseData
                )
                session.writePacket(responsePacket)
            } finally {
                forwardConcurrencyGate.release()
            }
        }
    }

    private fun forwardDnsUdp(
        dnsQuery: ByteArray,
        targets: List<InetAddress>
    ): ByteArray? {
        for (address in targets) {
            val response = try {
                forwardToAddress(dnsQuery, address)
            } catch (_: Throwable) {
                null
            }
            if (response != null) return response
        }
        return null
    }

    private fun forwardToAddress(dnsQuery: ByteArray, address: InetAddress): ByteArray? {
        DatagramSocket().use { socket ->
            if (!protect(socket)) {
                try {
                    val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                    cm?.activeNetwork?.bindSocket(socket)
                } catch (_: Throwable) {}
                Log.w(TAG, "protect(socket) returned false")
            }
            socket.soTimeout = DNS_SOCKET_TIMEOUT_MS
            socket.send(DatagramPacket(dnsQuery, dnsQuery.size, address, 53))

            val respBuffer = ByteArray(2048)
            val inPacket = DatagramPacket(respBuffer, respBuffer.size)
            socket.receive(inPacket)
            if (inPacket.length <= 0) return null
            return respBuffer.copyOfRange(0, inPacket.length)
        }
    }

    private fun resolveLiteralIpv4List(vararg candidates: String): List<InetAddress> {
        val out = LinkedHashSet<InetAddress>()
        for (raw in candidates) {
            val trimmed = raw.trim()
            if (trimmed.isBlank() || !isLiteralIpv4(trimmed)) continue
            runCatching { InetAddress.getByName(trimmed) }
                .getOrNull()
                ?.takeIf { it is Inet4Address }
                ?.let { out.add(it) }
        }
        if (out.isEmpty()) {
            runCatching { InetAddress.getByName(DEFAULT_UPSTREAM_IPV4) }
                .getOrNull()
                ?.let { out.add(it) }
        }
        return out.toList()
    }

    private fun resolveLiteralIpv6List(vararg candidates: String): List<InetAddress> {
        val out = LinkedHashSet<InetAddress>()
        for (raw in candidates) {
            val trimmed = raw.trim()
            if (trimmed.isBlank() || !trimmed.contains(':')) continue
            runCatching { InetAddress.getByName(trimmed) }
                .getOrNull()
                ?.takeIf { it is Inet6Address }
                ?.let { out.add(it) }
        }
        return out.toList()
    }

    private fun isLiteralIpv4(host: String): Boolean {
        val parts = host.split('.')
        if (parts.size != 4) return false
        return parts.all { part ->
            val v = part.toIntOrNull()
            v != null && v in 0..255
        }
    }

    private fun disconnectVpn() {
        _isRunning.value = false
        _activeServerName.value = "Disconnected"
        val jobToStop = tunnelJob
        tunnelJob = null
        val currentSession = activeSession
        currentSession?.wake()
        jobToStop?.cancel()
        serviceScope.launch {
            tunnelMutex.withLock {
                if (jobToStop != null) {
                    withTimeoutOrNull(800L) {
                        jobToStop.join()
                    }
                }
                activeSession?.let { s ->
                    s.closeOnce()
                    if (activeSession === s) {
                        activeSession = null
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        if (activeInstance === this) {
            activeInstance = null
        }
        try {
            analyticsRepo.flushNow()
        } catch (_: Throwable) {}
        _isRunning.value = false
        _activeServerName.value = "Disconnected"
        tunnelGeneration.incrementAndGet()
        activeSession?.wake()
        tunnelJob?.cancel()
        tunnelJob = null
        activeSession?.closeOnce()
        activeSession = null
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
            getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
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
