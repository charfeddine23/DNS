package com.example.service

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
