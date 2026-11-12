package com.example.utils

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import kotlin.system.measureTimeMillis

object LatencyTester {

    /**
     * Measures latency by attempting a fast TCP socket connection to port 53 (DNS port) or 853 (DNS-over-TLS).
     * Falls back to ICMP/InetAddress reachability check.
     */
    suspend fun pingDnsServer(addressOrHost: String, port: Int = 53, timeoutMs: Int = 1200): Long = withContext(Dispatchers.IO) {
        if (addressOrHost.isBlank()) return@withContext -1L

        // Try TCP handshake on DNS port 53 / 853 for ultra-accurate measurement
        try {
            var latency: Long = -1L
            val elapsed = measureTimeMillis {
                Socket().use { socket ->
                    socket.connect(InetSocketAddress(addressOrHost, port), timeoutMs)
                }
            }
            latency = elapsed
            if (latency >= 0) return@withContext latency
        } catch (_: Exception) {
            // TCP connect to 53 failed or closed; try ICMP reachability / resolution
        }

        // Secondary fallback: InetAddress reachability
        try {
            val addr = InetAddress.getByName(addressOrHost)
            val startTime = System.currentTimeMillis()
            val reachable = addr.isReachable(timeoutMs)
            val elapsed = System.currentTimeMillis() - startTime
            if (reachable && elapsed > 0) {
                return@withContext elapsed
            } else if (reachable) {
                return@withContext 12L // local minimum estimate
            }
        } catch (_: Exception) {
            // Unreachable or timeout
        }

        -1L
    }
}
