package com.example

import com.example.service.DnsPacketBuilder
import org.junit.Assert.assertEquals
import org.junit.Test

class DnsPacketHelperTest {

    @Test
    fun `buildIpv4UdpResponse produces valid IPv4 header checksum and swapped addresses`() {
        val clientIp = byteArrayOf(10, 233.toByte(), 1, 2)
        val serverIp = byteArrayOf(1, 1, 1, 1)
        val dnsPayload = byteArrayOf(0x12, 0x34, 0x81.toByte(), 0x80.toByte(), 0x00, 0x01)

        val pkt = DnsPacketBuilder.buildIpv4UdpResponse(
            clientIp = clientIp,
            clientPort = 54321,
            serverIp = serverIp,
            serverPort = 53,
            dnsPayload = dnsPayload
        )

        assertEquals(20 + 8 + dnsPayload.size, pkt.size)
        // IPv4 version & IHL
        assertEquals(0x45.toByte(), pkt[0])
        // Protocol UDP = 17
        assertEquals(17.toByte(), pkt[9])
        // Valid IPv4 header checksum verifies to 0
        assertEquals(0, DnsPacketBuilder.onesComplementChecksum(pkt, 0, 20))
        // Swapped Src (1.1.1.1) and Dst (10.233.1.2)
        assertEquals(1.toByte(), pkt[12])
        assertEquals(10.toByte(), pkt[16])
        // Swapped Ports: srcPort=53, dstPort=54321
        assertEquals(53, ((pkt[20].toInt() and 0xFF) shl 8) or (pkt[21].toInt() and 0xFF))
        assertEquals(54321, ((pkt[22].toInt() and 0xFF) shl 8) or (pkt[23].toInt() and 0xFF))
    }

    @Test
    fun `buildIpv6UdpResponse produces valid IPv6 UDP header and length`() {
        val clientIp = ByteArray(16).apply { this[0] = 0xfd.toByte(); this[15] = 2 }
        val serverIp = ByteArray(16).apply { this[0] = 0x26; this[15] = 1 }
        val dnsPayload = byteArrayOf(0x12, 0x34, 0x81.toByte(), 0x80.toByte())

        val pkt = DnsPacketBuilder.buildIpv6UdpResponse(
            clientIp = clientIp,
            clientPort = 40000,
            serverIp = serverIp,
            serverPort = 53,
            dnsPayload = dnsPayload
        )

        assertEquals(40 + 8 + dnsPayload.size, pkt.size)
        assertEquals(0x60.toByte(), pkt[0])
        assertEquals(17.toByte(), pkt[6])
        assertEquals(53, ((pkt[40].toInt() and 0xFF) shl 8) or (pkt[41].toInt() and 0xFF))
        assertEquals(40000, ((pkt[42].toInt() and 0xFF) shl 8) or (pkt[43].toInt() and 0xFF))
    }
}
