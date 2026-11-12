package com.example.data.model

enum class DnsEngineMode {
    PRIVATE_DNS, // System Private DNS (DNS-over-TLS)
    LOCAL_VPN    // Local VPN DNS Switcher
}

enum class LatencyStatus {
    EXCELLENT, // < 40ms (Green)
    MODERATE,  // 40ms - 100ms (Amber)
    POOR,      // > 100ms (Red)
    UNKNOWN
}

data class DnsServerProfile(
    val id: String,
    val name: String,
    val description: String,
    val primaryIpv4: String,
    val secondaryIpv4: String = "",
    val primaryIpv6: String = "",
    val secondaryIpv6: String = "",
    val dotHostname: String = "",
    val isCustom: Boolean = false,
    val iconKey: String = "dns",
    val latencyMs: Long? = null
) {
    val latencyStatus: LatencyStatus
        get() = when {
            latencyMs == null -> LatencyStatus.UNKNOWN
            latencyMs < 40 -> LatencyStatus.EXCELLENT
            latencyMs <= 100 -> LatencyStatus.MODERATE
            else -> LatencyStatus.POOR
        }
}

data class PrivateDnsPreset(
    val id: String,
    val name: String,
    val tag: String,
    val hostname: String,
    val description: String,
    val isAdGuardShield: Boolean = false
)
