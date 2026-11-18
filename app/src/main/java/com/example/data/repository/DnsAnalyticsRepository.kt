package com.example.data.repository

import android.content.Context
import android.content.SharedPreferences
import com.example.data.model.DnsAnalyticsSnapshot
import com.example.data.model.DomainStatItem
import com.example.data.model.HourlyTrafficBucket
import com.example.data.model.RecentDnsEvent
import com.example.service.DnsPacketAnalysis
import com.example.service.DnsTrafficCategory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class DnsAnalyticsRepository private constructor(
    context: Context
) {
    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val ioScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val stateLock = Any()

    private var totalQueries: Long = 0L
    private var adsReachedCount: Long = 0L
    private var adsBlockedCount: Long = 0L
    private var adsPassedCount: Long = 0L
    private var cleanQueriesCount: Long = 0L
    private var adNetworkHits: Long = 0L
    private var trackerHits: Long = 0L
    private var telemetryHits: Long = 0L
    private var totalLatencyMs: Long = 0L
    private var latencySamples: Long = 0L
    private var lastUpdatedMs: Long = 0L

    private val domainStats = LinkedHashMap<String, MutableDomainStat>()
    private val hourlyBuckets = LinkedHashMap<Long, MutableHourlyBucket>()
    private val recentEvents = ArrayDeque<RecentDnsEvent>()

    private var persistJob: Job? = null
    private var snapshotEmitJob: Job? = null
    @Volatile
    private var lastSnapshotEmitAtMs: Long = 0L
    @Volatile
    private var lastReferenceTimeMs: Long = 0L
    private val hourFormatter = SimpleDateFormat("HH", Locale.US)

    private val _analyticsFlow = MutableStateFlow(DnsAnalyticsSnapshot())
    val analyticsFlow: StateFlow<DnsAnalyticsSnapshot> = _analyticsFlow.asStateFlow()

    private data class MutableDomainStat(
        var hits: Long,
        var blockedHits: Long,
        var category: DnsTrafficCategory,
        var lastSeenMs: Long
    )

    private data class MutableHourlyBucket(
        var totalQueries: Long,
        var adsDetected: Long,
        var adsBlocked: Long
    )

    init {
        loadFromPrefs()
    }

    fun recordQuery(
        analysis: DnsPacketAnalysis,
        latencyMs: Long,
        timestampMs: Long = System.currentTimeMillis()
    ) {
        var immediateSnapshot: DnsAnalyticsSnapshot? = null
        val nowWall = System.currentTimeMillis()
        synchronized(stateLock) {
            totalQueries++
            lastUpdatedMs = timestampMs
            lastReferenceTimeMs = timestampMs

            if (latencyMs >= 0L) {
                totalLatencyMs += latencyMs
                latencySamples++
            }

            if (analysis.isAdOrTrackerReached) {
                adsReachedCount++
                if (analysis.isBlocked) {
                    adsBlockedCount++
                } else {
                    adsPassedCount++
                }
                when (analysis.category) {
                    DnsTrafficCategory.AD_NETWORK -> adNetworkHits++
                    DnsTrafficCategory.TRACKER -> trackerHits++
                    DnsTrafficCategory.TELEMETRY -> telemetryHits++
                    DnsTrafficCategory.CLEAN -> Unit
                }

                val existing = domainStats[analysis.domain]
                if (existing != null) {
                    existing.hits++
                    if (analysis.isBlocked) existing.blockedHits++
                    existing.category = analysis.category
                    existing.lastSeenMs = timestampMs
                } else {
                    if (domainStats.size >= MAX_TRACKED_DOMAINS) {
                        trimLeastUsedDomain()
                    }
                    domainStats[analysis.domain] = MutableDomainStat(
                        hits = 1L,
                        blockedHits = if (analysis.isBlocked) 1L else 0L,
                        category = analysis.category,
                        lastSeenMs = timestampMs
                    )
                }
            } else {
                cleanQueriesCount++
            }

            val epochHour = timestampMs / MILLIS_PER_HOUR
            val bucket = hourlyBuckets.getOrPut(epochHour) {
                MutableHourlyBucket(0L, 0L, 0L)
            }
            bucket.totalQueries++
            if (analysis.isAdOrTrackerReached) {
                bucket.adsDetected++
                if (analysis.isBlocked) {
                    bucket.adsBlocked++
                }
            }
            pruneOldHourlyBuckets(epochHour)

            // Prioritize keeping ad/tracker events visible alongside live queries
            if (analysis.isAdOrTrackerReached || recentEvents.size < MAX_RECENT_EVENTS) {
                recentEvents.addFirst(
                    RecentDnsEvent(
                        domain = analysis.domain,
                        category = analysis.category,
                        wasBlocked = analysis.isBlocked,
                        latencyMs = latencyMs.coerceAtLeast(0L),
                        timestampMs = timestampMs
                    )
                )
                while (recentEvents.size > MAX_RECENT_EVENTS) {
                    recentEvents.removeLast()
                }
            }

            if (nowWall - lastSnapshotEmitAtMs >= SNAPSHOT_EMIT_THROTTLE_MS) {
                lastSnapshotEmitAtMs = nowWall
                immediateSnapshot = buildSnapshotLocked(timestampMs)
            }
        }
        if (immediateSnapshot != null) {
            snapshotEmitJob?.cancel()
            _analyticsFlow.value = immediateSnapshot!!
        } else {
            scheduleSnapshotEmit()
        }
        schedulePersist()
    }

    fun resetAnalytics() {
        val emptySnapshot: DnsAnalyticsSnapshot
        synchronized(stateLock) {
            totalQueries = 0L
            adsReachedCount = 0L
            adsBlockedCount = 0L
            adsPassedCount = 0L
            cleanQueriesCount = 0L
            adNetworkHits = 0L
            trackerHits = 0L
            telemetryHits = 0L
            totalLatencyMs = 0L
            latencySamples = 0L
            lastUpdatedMs = 0L
            lastReferenceTimeMs = 0L
            domainStats.clear()
            hourlyBuckets.clear()
            recentEvents.clear()
            emptySnapshot = DnsAnalyticsSnapshot()
        }
        snapshotEmitJob?.cancel()
        persistJob?.cancel()
        lastSnapshotEmitAtMs = System.currentTimeMillis()
        _analyticsFlow.value = emptySnapshot
        prefs.edit().remove(KEY_ANALYTICS_JSON).apply()
    }

    fun flushNow() {
        snapshotEmitJob?.cancel()
        persistJob?.cancel()
        val snap = synchronized(stateLock) {
            buildSnapshotLocked(lastReferenceTimeMs)
        }
        lastSnapshotEmitAtMs = System.currentTimeMillis()
        _analyticsFlow.value = snap
        saveToPrefsNow()
    }

    private fun scheduleSnapshotEmit() {
        if (snapshotEmitJob?.isActive == true) return
        snapshotEmitJob = ioScope.launch {
            delay(SNAPSHOT_EMIT_THROTTLE_MS)
            val snap = synchronized(stateLock) {
                lastSnapshotEmitAtMs = System.currentTimeMillis()
                buildSnapshotLocked(lastReferenceTimeMs)
            }
            _analyticsFlow.value = snap
        }
    }

    private fun schedulePersist() {
        if (persistJob?.isActive == true) return
        persistJob = ioScope.launch {
            delay(PERSIST_DEBOUNCE_MS)
            saveToPrefsNow()
        }
    }

    private fun trimLeastUsedDomain() {
        val victim = domainStats.entries.minByOrNull { it.value.hits }?.key ?: return
        domainStats.remove(victim)
    }

    private fun pruneOldHourlyBuckets(currentEpochHour: Long) {
        val minHour = currentEpochHour - 23L
        val iterator = hourlyBuckets.entries.iterator()
        while (iterator.hasNext()) {
            if (iterator.next().key < minHour) {
                iterator.remove()
            }
        }
    }

    private fun buildSnapshotLocked(referenceTimeMs: Long): DnsAnalyticsSnapshot {
        val avgLatency = if (latencySamples > 0L) totalLatencyMs / latencySamples else 0L
        val estimatedKb = adsBlockedCount * ESTIMATED_KB_PER_BLOCKED_AD

        val nowHour = (if (referenceTimeMs > 0L) referenceTimeMs else System.currentTimeMillis()) / MILLIS_PER_HOUR
        val chartBuckets = if (totalQueries == 0L) {
            emptyList()
        } else {
            ((nowHour - 11L)..nowHour).map { epochHour ->
                val b = hourlyBuckets[epochHour]
                val label = hourFormatter.format(Date(epochHour * MILLIS_PER_HOUR))
                HourlyTrafficBucket(
                    epochHour = epochHour,
                    label = label,
                    totalQueries = b?.totalQueries ?: 0L,
                    adsDetected = b?.adsDetected ?: 0L,
                    adsBlocked = b?.adsBlocked ?: 0L
                )
            }
        }

        val topDomains = domainStats.entries
            .sortedWith(compareByDescending<Map.Entry<String, MutableDomainStat>> { it.value.hits }
                .thenByDescending { it.value.lastSeenMs })
            .take(8)
            .map { (domain, stat) ->
                DomainStatItem(
                    domain = domain,
                    hits = stat.hits,
                    blockedHits = stat.blockedHits,
                    category = stat.category
                )
            }

        return DnsAnalyticsSnapshot(
            totalQueries = totalQueries,
            adsReachedCount = adsReachedCount,
            adsBlockedCount = adsBlockedCount,
            adsPassedCount = adsPassedCount,
            cleanQueriesCount = cleanQueriesCount,
            adNetworkHits = adNetworkHits,
            trackerHits = trackerHits,
            telemetryHits = telemetryHits,
            avgLatencyMs = avgLatency,
            estimatedDataSavedKb = estimatedKb,
            hourlyBuckets = chartBuckets,
            topInterceptedDomains = topDomains,
            recentEvents = recentEvents.toList(),
            lastUpdatedMs = lastUpdatedMs
        )
    }

    private fun saveToPrefsNow() {
        val jsonText = synchronized(stateLock) {
            val root = JSONObject()
            root.put("totalQueries", totalQueries)
            root.put("adsReachedCount", adsReachedCount)
            root.put("adsBlockedCount", adsBlockedCount)
            root.put("adsPassedCount", adsPassedCount)
            root.put("cleanQueriesCount", cleanQueriesCount)
            root.put("adNetworkHits", adNetworkHits)
            root.put("trackerHits", trackerHits)
            root.put("telemetryHits", telemetryHits)
            root.put("totalLatencyMs", totalLatencyMs)
            root.put("latencySamples", latencySamples)
            root.put("lastUpdatedMs", lastUpdatedMs)

            val domainsArr = JSONArray()
            domainStats.entries
                .sortedByDescending { it.value.hits }
                .take(MAX_TRACKED_DOMAINS)
                .forEach { (domain, stat) ->
                    domainsArr.put(
                        JSONObject().apply {
                            put("domain", domain)
                            put("hits", stat.hits)
                            put("blockedHits", stat.blockedHits)
                            put("category", stat.category.name)
                            put("lastSeenMs", stat.lastSeenMs)
                        }
                    )
                }
            root.put("domains", domainsArr)

            val bucketsArr = JSONArray()
            hourlyBuckets.forEach { (hour, bucket) ->
                bucketsArr.put(
                    JSONObject().apply {
                        put("hour", hour)
                        put("total", bucket.totalQueries)
                        put("detected", bucket.adsDetected)
                        put("blocked", bucket.adsBlocked)
                    }
                )
            }
            root.put("hourly", bucketsArr)

            val eventsArr = JSONArray()
            recentEvents.forEach { ev ->
                eventsArr.put(
                    JSONObject().apply {
                        put("domain", ev.domain)
                        put("category", ev.category.name)
                        put("blocked", ev.wasBlocked)
                        put("latency", ev.latencyMs)
                        put("ts", ev.timestampMs)
                    }
                )
            }
            root.put("recent", eventsArr)
            root.toString()
        }
        prefs.edit().putString(KEY_ANALYTICS_JSON, jsonText).apply()
    }

    private fun loadFromPrefs() {
        val raw = prefs.getString(KEY_ANALYTICS_JSON, null) ?: return
        val snapshot = try {
            val root = JSONObject(raw)
            synchronized(stateLock) {
                totalQueries = root.optLong("totalQueries", 0L)
                adsReachedCount = root.optLong("adsReachedCount", 0L)
                adsBlockedCount = root.optLong("adsBlockedCount", 0L)
                adsPassedCount = root.optLong("adsPassedCount", 0L)
                cleanQueriesCount = root.optLong("cleanQueriesCount", 0L)
                adNetworkHits = root.optLong("adNetworkHits", 0L)
                trackerHits = root.optLong("trackerHits", 0L)
                telemetryHits = root.optLong("telemetryHits", 0L)
                totalLatencyMs = root.optLong("totalLatencyMs", 0L)
                latencySamples = root.optLong("latencySamples", 0L)
                lastUpdatedMs = root.optLong("lastUpdatedMs", 0L)

                domainStats.clear()
                val domainsArr = root.optJSONArray("domains")
                if (domainsArr != null) {
                    for (i in 0 until domainsArr.length()) {
                        val obj = domainsArr.optJSONObject(i) ?: continue
                        val domain = obj.optString("domain")
                        if (domain.isBlank()) continue
                        val cat = runCatching {
                            DnsTrafficCategory.valueOf(obj.optString("category"))
                        }.getOrDefault(DnsTrafficCategory.AD_NETWORK)
                        domainStats[domain] = MutableDomainStat(
                            hits = obj.optLong("hits", 0L),
                            blockedHits = obj.optLong("blockedHits", 0L),
                            category = cat,
                            lastSeenMs = obj.optLong("lastSeenMs", 0L)
                        )
                    }
                }

                hourlyBuckets.clear()
                val bucketsArr = root.optJSONArray("hourly")
                if (bucketsArr != null) {
                    for (i in 0 until bucketsArr.length()) {
                        val obj = bucketsArr.optJSONObject(i) ?: continue
                        hourlyBuckets[obj.optLong("hour")] = MutableHourlyBucket(
                            totalQueries = obj.optLong("total", 0L),
                            adsDetected = obj.optLong("detected", 0L),
                            adsBlocked = obj.optLong("blocked", 0L)
                        )
                    }
                }

                recentEvents.clear()
                val eventsArr = root.optJSONArray("recent")
                if (eventsArr != null) {
                    for (i in 0 until eventsArr.length()) {
                        val obj = eventsArr.optJSONObject(i) ?: continue
                        val domain = obj.optString("domain")
                        if (domain.isBlank()) continue
                        val cat = runCatching {
                            DnsTrafficCategory.valueOf(obj.optString("category"))
                        }.getOrDefault(DnsTrafficCategory.CLEAN)
                        recentEvents.addLast(
                            RecentDnsEvent(
                                domain = domain,
                                category = cat,
                                wasBlocked = obj.optBoolean("blocked", false),
                                latencyMs = obj.optLong("latency", 0L),
                                timestampMs = obj.optLong("ts", 0L)
                            )
                        )
                    }
                }
                buildSnapshotLocked(lastUpdatedMs)
            }
        } catch (_: Exception) {
            DnsAnalyticsSnapshot()
        }
        _analyticsFlow.value = snapshot
    }

    companion object {
        private const val PREFS_NAME = "dns_pulse_analytics"
        private const val KEY_ANALYTICS_JSON = "analytics_snapshot_json"
        private const val MILLIS_PER_HOUR = 3_600_000L
        private const val PERSIST_DEBOUNCE_MS = 1_200L
        private const val SNAPSHOT_EMIT_THROTTLE_MS = 280L
        private const val MAX_TRACKED_DOMAINS = 64
        private const val MAX_RECENT_EVENTS = 12
        private const val ESTIMATED_KB_PER_BLOCKED_AD = 38L

        @Volatile
        private var instance: DnsAnalyticsRepository? = null

        fun getInstance(context: Context): DnsAnalyticsRepository {
            return instance ?: synchronized(this) {
                instance ?: DnsAnalyticsRepository(context.applicationContext).also { instance = it }
            }
        }

        internal fun createIsolatedForTesting(context: Context): DnsAnalyticsRepository {
            return DnsAnalyticsRepository(context.applicationContext)
        }
    }
}
