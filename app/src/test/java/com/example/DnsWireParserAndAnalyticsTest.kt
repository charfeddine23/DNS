package com.example

import androidx.test.core.app.ApplicationProvider
import com.example.data.repository.DnsAnalyticsRepository
import com.example.service.DnsTrafficCategory
import com.example.service.DnsWireParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.ByteArrayOutputStream

@RunWith(RobolectricTestRunner::class)
class DnsWireParserAndAnalyticsTest {

    private fun buildDnsQuery(domain: String, qtype: Int = 1): ByteArray {
        val out = ByteArrayOutputStream()
        // Header: ID=0x1234, Flags=0x0100, QDCOUNT=1, AN/NS/AR=0
        out.write(byteArrayOf(0x12, 0x34, 0x01, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00))
        domain.split('.').forEach { label ->
            val bytes = label.toByteArray(Charsets.US_ASCII)
            out.write(bytes.size)
            out.write(bytes)
        }
        out.write(0x00)
        // QTYPE & QCLASS=IN(1)
        out.write((qtype ushr 8) and 0xFF)
        out.write(qtype and 0xFF)
        out.write(0x00)
        out.write(0x01)
        return out.toByteArray()
    }

    private fun buildDnsResponse(
        domain: String,
        rcode: Int = 0,
        answerIpBytes: ByteArray? = null,
        qtype: Int = 1
    ): ByteArray {
        val out = ByteArrayOutputStream()
        val anCount = if (answerIpBytes != null) 1 else 0
        // Flags: QR=1, RD=1, RA=1, RCODE
        out.write(byteArrayOf(0x12, 0x34, 0x81.toByte(), (0x80 or (rcode and 0x0F)).toByte()))
        out.write(byteArrayOf(0x00, 0x01, 0x00, anCount.toByte(), 0x00, 0x00, 0x00, 0x00))
        domain.split('.').forEach { label ->
            val bytes = label.toByteArray(Charsets.US_ASCII)
            out.write(bytes.size)
            out.write(bytes)
        }
        out.write(0x00)
        out.write((qtype ushr 8) and 0xFF)
        out.write(qtype and 0xFF)
        out.write(0x00)
        out.write(0x01)

        if (answerIpBytes != null) {
            // Pointer to offset 12 (0xC00C)
            out.write(byteArrayOf(0xC0.toByte(), 0x0C))
            val rrType = if (answerIpBytes.size == 16) 28 else 1
            out.write(byteArrayOf(0x00, rrType.toByte(), 0x00, 0x01))
            // TTL = 60s
            out.write(byteArrayOf(0x00, 0x00, 0x00, 0x3C))
            // RDLENGTH + RDATA
            out.write(byteArrayOf(0x00, answerIpBytes.size.toByte()))
            out.write(answerIpBytes)
        }
        return out.toByteArray()
    }

    @Test
    fun `parses ad domain and detects AdGuard 0_0_0_0 sinkhole block`() {
        val query = buildDnsQuery("googleads.g.doubleclick.net", qtype = 1)
        val response = buildDnsResponse(
            domain = "googleads.g.doubleclick.net",
            rcode = 0,
            answerIpBytes = byteArrayOf(0, 0, 0, 0)
        )

        val analysis = DnsWireParser.analyzeTransaction(query, response)
        assertNotNull(analysis)
        assertEquals("googleads.g.doubleclick.net", analysis!!.domain)
        assertEquals(DnsTrafficCategory.AD_NETWORK, analysis.category)
        assertTrue(analysis.isAdOrTrackerReached)
        assertTrue(analysis.isBlocked)
    }

    @Test
    fun `detects IPv6 sinkhole block and distinguishes unblocked ad on standard DNS`() {
        val trackerQuery = buildDnsQuery("app-measurement.com", qtype = 28)
        val ipv6Sinkhole = buildDnsResponse(
            domain = "app-measurement.com",
            rcode = 0,
            answerIpBytes = ByteArray(16),
            qtype = 28
        )
        val blockedTracker = DnsWireParser.analyzeTransaction(trackerQuery, ipv6Sinkhole)!!
        assertEquals(DnsTrafficCategory.TRACKER, blockedTracker.category)
        assertTrue(blockedTracker.isAdOrTrackerReached)
        assertTrue(blockedTracker.isBlocked)

        val allowedAdResp = buildDnsResponse(
            domain = "googleads.g.doubleclick.net",
            rcode = 0,
            answerIpBytes = byteArrayOf(142.toByte(), 250.toByte(), 74.toByte(), 46)
        )
        val passedAd = DnsWireParser.analyzeTransaction(
            buildDnsQuery("googleads.g.doubleclick.net"),
            allowedAdResp
        )!!
        assertTrue(passedAd.isAdOrTrackerReached)
        assertFalse(passedAd.isBlocked)
    }

    @Test
    fun `clean domain NXDOMAIN is not miscounted as blocked ad`() {
        val cleanQuery = buildDnsQuery("api.github.com")
        val nxResponse = buildDnsResponse("api.github.com", rcode = 3, answerIpBytes = null)

        val analysis = DnsWireParser.analyzeTransaction(cleanQuery, nxResponse)!!
        assertEquals(DnsTrafficCategory.CLEAN, analysis.category)
        assertFalse(analysis.isAdOrTrackerReached)
        assertFalse(analysis.isBlocked)
    }

    @Test
    fun `analytics repository aggregates real traffic, persists, and resets cleanly`() {
        val context = ApplicationProvider.getApplicationContext<android.app.Application>()
        val repo = DnsAnalyticsRepository.createIsolatedForTesting(context)
        repo.resetAnalytics()

        val blockedAd = DnsWireParser.analyzeTransaction(
            buildDnsQuery("googleads.g.doubleclick.net"),
            buildDnsResponse("googleads.g.doubleclick.net", answerIpBytes = byteArrayOf(0, 0, 0, 0))
        )!!
        val passedTracker = DnsWireParser.analyzeTransaction(
            buildDnsQuery("app-measurement.com"),
            buildDnsResponse("app-measurement.com", answerIpBytes = byteArrayOf(1, 2, 3, 4))
        )!!
        val cleanHit = DnsWireParser.analyzeTransaction(
            buildDnsQuery("example.org"),
            buildDnsResponse("example.org", answerIpBytes = byteArrayOf(93.toByte(), 184.toByte(), 216.toByte(), 34))
        )!!

        val now = 1_760_000_000_000L
        repo.recordQuery(blockedAd, latencyMs = 14L, timestampMs = now)
        repo.recordQuery(passedTracker, latencyMs = 22L, timestampMs = now + 500L)
        repo.recordQuery(cleanHit, latencyMs = 18L, timestampMs = now + 1000L)
        repo.flushNow()

        val snap = repo.analyticsFlow.value
        assertEquals(3L, snap.totalQueries)
        assertEquals(2L, snap.adsReachedCount)
        assertEquals(1L, snap.adsBlockedCount)
        assertEquals(1L, snap.adsPassedCount)
        assertEquals(1L, snap.cleanQueriesCount)
        assertEquals(50, snap.blockEfficiencyPercent)
        assertEquals(18L, snap.avgLatencyMs)
        assertEquals(2, snap.topInterceptedDomains.size)
        assertEquals(12, snap.hourlyBuckets.size)

        // Verify persistence reloads cleanly
        val reloaded = DnsAnalyticsRepository.createIsolatedForTesting(context)
        assertEquals(3L, reloaded.analyticsFlow.value.totalQueries)
        assertEquals(1L, reloaded.analyticsFlow.value.adsBlockedCount)

        reloaded.resetAnalytics()
        assertEquals(0L, reloaded.analyticsFlow.value.totalQueries)
        assertEquals(0L, reloaded.analyticsFlow.value.adsReachedCount)
        assertTrue(reloaded.analyticsFlow.value.topInterceptedDomains.isEmpty())
    }
}
