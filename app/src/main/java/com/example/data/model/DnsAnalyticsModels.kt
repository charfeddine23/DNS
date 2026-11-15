package com.example.data.model

import com.example.service.DnsTrafficCategory

data class DomainStatItem(
    val domain: String,
    val hits: Long,
    val blockedHits: Long,
    val category: DnsTrafficCategory
)

data class HourlyTrafficBucket(
    val epochHour: Long,
    val label: String,
    val totalQueries: Long,
    val adsDetected: Long,
    val adsBlocked: Long
)

data class RecentDnsEvent(
    val domain: String,
    val category: DnsTrafficCategory,
    val wasBlocked: Boolean,
    val latencyMs: Long,
    val timestampMs: Long
)

data class DnsAnalyticsSnapshot(
    val totalQueries: Long = 0L,
    val adsReachedCount: Long = 0L,
    val adsBlockedCount: Long = 0L,
    val adsPassedCount: Long = 0L,
    val cleanQueriesCount: Long = 0L,
    val adNetworkHits: Long = 0L,
    val trackerHits: Long = 0L,
    val telemetryHits: Long = 0L,
    val avgLatencyMs: Long = 0L,
    val estimatedDataSavedKb: Long = 0L,
    val hourlyBuckets: List<HourlyTrafficBucket> = emptyList(),
    val topInterceptedDomains: List<DomainStatItem> = emptyList(),
    val recentEvents: List<RecentDnsEvent> = emptyList(),
    val lastUpdatedMs: Long = 0L
) {
    val blockEfficiencyPercent: Int
        get() = if (adsReachedCount <= 0L) 0
        else ((adsBlockedCount * 100L) / adsReachedCount).toInt().coerceIn(0, 100)
}
