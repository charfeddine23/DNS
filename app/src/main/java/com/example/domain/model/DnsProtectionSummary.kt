package com.example.domain.model

import com.example.data.model.DnsEngineMode

data class DnsProtectionSummary(
    val engineMode: DnsEngineMode,
    val isProtected: Boolean,
    val activeTarget: String,
    val isAdGuardShield: Boolean,
    val latencyMs: Long?
)
