package com.example.service

import java.util.Locale

enum class DnsTrafficCategory {
    AD_NETWORK,
    TRACKER,
    TELEMETRY,
    CLEAN
}

data class DnsQuestionInfo(
    val domain: String,
    val qtype: Int
)

data class DnsResponseInspection(
    val rcode: Int,
    val answerCount: Int,
    val isSinkholeIp: Boolean
)

data class DnsPacketAnalysis(
    val domain: String,
    val qtype: Int,
    val category: DnsTrafficCategory,
    val isAdOrTrackerReached: Boolean,
    val isBlocked: Boolean,
    val rcode: Int
)

/**
 * Pure Kotlin RFC 1035 DNS packet inspector and ad/tracker domain classifier.
 * Inspects real DNS UDP queries and responses forwarded through [DnsVpnService].
 */
object DnsWireParser {

    private val adNetworkExactOrSuffix = setOf(
        "doubleclick.net",
        "googlesyndication.com",
        "googleadservices.com",
        "adservice.google.com",
        "admob.com",
        "ads.facebook.com",
        "an.facebook.com",
        "adcolony.com",
        "applovin.com",
        "applvn.com",
        "unityads.unity3d.com",
        "vungle.com",
        "ironsrc.com",
        "supersonicads.com",
        "mopub.com",
        "chartboost.com",
        "inmobi.com",
        "tapjoy.com",
        "fyber.com",
        "pangle.io",
        "pangleglobal.com",
        "mintegral.com",
        "taboola.com",
        "outbrain.com",
        "criteo.com",
        "criteo.net",
        "pubmatic.com",
        "rubiconproject.com",
        "openx.net",
        "adnxs.com",
        "casalemedia.com",
        "smaato.net",
        "startappservice.com",
        "bidswitch.net",
        "media.net",
        "amazon-adsystem.com",
        "ads-twitter.com",
        "ads.tiktok.com",
        "ads.linkedin.com",
        "ads.pinterest.com",
        "ads.yahoo.com",
        "advertising.com",
        "adform.net",
        "adroll.com",
        "revcontent.com",
        "mgid.com",
        "propellerads.com",
        "popads.net",
        "yandexadexchange.net",
        "an.yandex.ru",
        "ads.youtube.com",
        "ad.xiaomi.com",
        "adx.g.doubleclick.net"
    )

    private val trackerExactOrSuffix = setOf(
        "app-measurement.com",
        "google-analytics.com",
        "analytics.google.com",
        "googletagmanager.com",
        "googletagservices.com",
        "adjust.com",
        "adjust.io",
        "appsflyer.com",
        "appsflyersdk.com",
        "branch.io",
        "bnc.lt",
        "kochava.com",
        "singular.net",
        "mixpanel.com",
        "amplitude.com",
        "segment.io",
        "segment.com",
        "scorecardresearch.com",
        "quantserve.com",
        "comscore.com",
        "bluekai.com",
        "demdex.net",
        "omtrdc.net",
        "krxd.net",
        "chartbeat.com",
        "hotjar.com",
        "fullstory.com",
        "mouseflow.com",
        "clarity.ms",
        "pixel.facebook.com",
        "bat.bing.com",
        "ct.pinterest.com",
        "analytics.tiktok.com",
        "tr.snapchat.com",
        "adsymptotic.com",
        "rlcdn.com",
        "tapad.com",
        "exelator.com",
        "agkn.com",
        "liadm.com",
        "tracking.miui.com"
    )

    private val telemetryExactOrSuffix = setOf(
        "crashlytics.com",
        "sentry.io",
        "bugsnag.com",
        "instabug.com",
        "flurry.com",
        "vortex.data.microsoft.com",
        "telemetry.microsoft.com",
        "settings-win.data.microsoft.com",
        "mobile.events.data.microsoft.com",
        "metrics.icloud.com",
        "metrics.mzstatic.com",
        "xp.apple.com",
        "telemetry.mozilla.org",
        "incoming.telemetry.mozilla.org",
        "logx.optimizely.com",
        "usage.trackjs.com",
        "nr-data.net",
        "newrelic.com",
        "datadoghq.com",
        "appdynamics.com",
        "raygun.io",
        "data.mistat.xiaomi.com",
        "metrics.data.hicloud.com",
        "logservice.hicloud.com",
        "metrics.samsungcloudsolution.com",
        "log.byteoversea.com"
    )

    private val adHostLabelKeywords = setOf(
        "ad", "ads", "admob", "adserver", "adservice", "adx", "pagead", "doubleclick", "banner", "adclick"
    )
    private val trackerHostLabelKeywords = setOf(
        "tracker", "tracking", "analytics", "pixel", "attribution", "retargeting"
    )
    private val telemetryHostLabelKeywords = setOf(
        "telemetry", "metrics", "crashlytics", "crashreport", "diagnostic", "diagnostics"
    )

    fun parseQuestion(dnsPacket: ByteArray): DnsQuestionInfo? {
        if (dnsPacket.size < 14) return null
        val qdCount = readUInt16(dnsPacket, 4)
        if (qdCount <= 0) return null

        val (domain, nextOffset) = readDnsName(dnsPacket, 12) ?: return null
        if (domain.isBlank() || nextOffset + 4 > dnsPacket.size) return null

        val qtype = readUInt16(dnsPacket, nextOffset)
        return DnsQuestionInfo(domain = domain, qtype = qtype)
    }

    fun inspectResponse(responsePacket: ByteArray): DnsResponseInspection {
        if (responsePacket.size < 12) {
            return DnsResponseInspection(rcode = -1, answerCount = 0, isSinkholeIp = false)
        }
        val rcode = responsePacket[3].toInt() and 0x0F
        val qdCount = readUInt16(responsePacket, 4)
        val anCount = readUInt16(responsePacket, 6)

        var offset = 12
        for (i in 0 until qdCount.coerceAtMost(8)) {
            val next = skipDnsName(responsePacket, offset) ?: return DnsResponseInspection(rcode, anCount, false)
            offset = next + 4
            if (offset > responsePacket.size) return DnsResponseInspection(rcode, anCount, false)
        }

        var isSinkholeIp = false
        for (i in 0 until anCount.coerceAtMost(32)) {
            val afterName = skipDnsName(responsePacket, offset) ?: break
            if (afterName + 10 > responsePacket.size) break
            val rrType = readUInt16(responsePacket, afterName)
            val rdLength = readUInt16(responsePacket, afterName + 8)
            val rdataStart = afterName + 10
            val rdataEnd = rdataStart + rdLength
            if (rdataEnd > responsePacket.size) break

            if (rrType == 1 && rdLength == 4) {
                val b0 = responsePacket[rdataStart].toInt() and 0xFF
                val b1 = responsePacket[rdataStart + 1].toInt() and 0xFF
                val b2 = responsePacket[rdataStart + 2].toInt() and 0xFF
                val b3 = responsePacket[rdataStart + 3].toInt() and 0xFF
                if ((b0 == 0 && b1 == 0 && b2 == 0 && b3 == 0) ||
                    (b0 == 127 && b1 == 0 && b2 == 0 && b3 == 1)
                ) {
                    isSinkholeIp = true
                }
            } else if (rrType == 28 && rdLength == 16) {
                var allZero = true
                for (k in 0 until 15) {
                    if (responsePacket[rdataStart + k].toInt() != 0) {
                        allZero = false
                        break
                    }
                }
                val lastByte = responsePacket[rdataStart + 15].toInt() and 0xFF
                if (allZero && (lastByte == 0 || lastByte == 1)) {
                    isSinkholeIp = true
                }
            }
            offset = rdataEnd
        }

        return DnsResponseInspection(
            rcode = rcode,
            answerCount = anCount,
            isSinkholeIp = isSinkholeIp
        )
    }

    fun classifyDomain(domain: String): DnsTrafficCategory {
        val normalized = domain.trim().trimEnd('.').lowercase(Locale.ROOT)
        if (normalized.isEmpty()) return DnsTrafficCategory.CLEAN

        if (matchesDomainSet(normalized, adNetworkExactOrSuffix)) {
            return DnsTrafficCategory.AD_NETWORK
        }
        if (matchesDomainSet(normalized, trackerExactOrSuffix)) {
            return DnsTrafficCategory.TRACKER
        }
        if (matchesDomainSet(normalized, telemetryExactOrSuffix)) {
            return DnsTrafficCategory.TELEMETRY
        }

        // Heuristic check on subdomain labels (excluding the registrable SLD+TLD suffix when single label)
        val labels = normalized.split('.')
        for (i in 0 until (labels.size - 1).coerceAtLeast(0)) {
            val label = labels[i]
            if (label in adHostLabelKeywords ||
                label.startsWith("adservice") ||
                label.startsWith("pagead")
            ) {
                return DnsTrafficCategory.AD_NETWORK
            }
            if (label in trackerHostLabelKeywords) {
                return DnsTrafficCategory.TRACKER
            }
            if (label in telemetryHostLabelKeywords) {
                return DnsTrafficCategory.TELEMETRY
            }
        }
        return DnsTrafficCategory.CLEAN
    }

    fun analyzeTransaction(
        dnsQuery: ByteArray,
        dnsResponse: ByteArray?
    ): DnsPacketAnalysis? {
        val question = parseQuestion(dnsQuery) ?: return null
        val normalizedDomain = question.domain.trim().trimEnd('.').lowercase(Locale.ROOT)
        if (normalizedDomain.isEmpty() || normalizedDomain == "localhost") return null

        val baseCategory = classifyDomain(normalizedDomain)
        val responseInfo = dnsResponse?.let { inspectResponse(it) }

        val upstreamSinkholed = responseInfo?.isSinkholeIp == true
        val upstreamRefusedOrNx = responseInfo != null &&
            (responseInfo.rcode == 3 || responseInfo.rcode == 5)
        val emptyAdAnswer = responseInfo != null &&
            baseCategory != DnsTrafficCategory.CLEAN &&
            (question.qtype == 1 || question.qtype == 28) &&
            responseInfo.rcode == 0 &&
            responseInfo.answerCount == 0

        val effectiveCategory = if (baseCategory == DnsTrafficCategory.CLEAN && upstreamSinkholed) {
            DnsTrafficCategory.AD_NETWORK
        } else {
            baseCategory
        }

        val isAdOrTrackerReached = effectiveCategory != DnsTrafficCategory.CLEAN || upstreamSinkholed
        val isBlocked = upstreamSinkholed ||
            (effectiveCategory != DnsTrafficCategory.CLEAN && (upstreamRefusedOrNx || emptyAdAnswer))

        return DnsPacketAnalysis(
            domain = normalizedDomain,
            qtype = question.qtype,
            category = effectiveCategory,
            isAdOrTrackerReached = isAdOrTrackerReached,
            isBlocked = isBlocked,
            rcode = responseInfo?.rcode ?: -1
        )
    }

    private fun matchesDomainSet(domain: String, rules: Set<String>): Boolean {
        if (domain in rules) return true
        var dotIdx = domain.indexOf('.')
        while (dotIdx != -1 && dotIdx + 1 < domain.length) {
            val suffix = domain.substring(dotIdx + 1)
            if (suffix in rules) return true
            dotIdx = domain.indexOf('.', dotIdx + 1)
        }
        return false
    }

    private fun readDnsName(packet: ByteArray, startOffset: Int): Pair<String, Int>? {
        val sb = StringBuilder()
        var offset = startOffset
        var endOffset = -1
        var hops = 0

        while (offset < packet.size && hops < 64) {
            hops++
            val len = packet[offset].toInt() and 0xFF
            if (len == 0) {
                if (endOffset == -1) endOffset = offset + 1
                break
            }
            if ((len and 0xC0) == 0xC0) {
                if (offset + 1 >= packet.size) return null
                if (endOffset == -1) endOffset = offset + 2
                val ptr = ((len and 0x3F) shl 8) or (packet[offset + 1].toInt() and 0xFF)
                if (ptr >= packet.size || ptr == offset) return null
                offset = ptr
                continue
            }
            if (len > 63 || offset + 1 + len > packet.size) return null
            if (sb.isNotEmpty()) sb.append('.')
            for (i in 0 until len) {
                val ch = (packet[offset + 1 + i].toInt() and 0xFF).toChar().lowercaseChar()
                sb.append(ch)
            }
            offset += 1 + len
        }
        val finalEnd = if (endOffset != -1) endOffset else offset
        return sb.toString() to finalEnd
    }

    private fun skipDnsName(packet: ByteArray, startOffset: Int): Int? {
        var offset = startOffset
        var hops = 0
        while (offset < packet.size && hops < 64) {
            hops++
            val len = packet[offset].toInt() and 0xFF
            if (len == 0) return offset + 1
            if ((len and 0xC0) == 0xC0) {
                return if (offset + 1 < packet.size) offset + 2 else null
            }
            if (len > 63) return null
            offset += 1 + len
        }
        return null
    }

    private fun readUInt16(bytes: ByteArray, offset: Int): Int {
        if (offset + 1 >= bytes.size) return 0
        return ((bytes[offset].toInt() and 0xFF) shl 8) or (bytes[offset + 1].toInt() and 0xFF)
    }
}
