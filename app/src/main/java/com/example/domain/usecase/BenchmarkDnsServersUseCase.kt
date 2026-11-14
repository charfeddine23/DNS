package com.example.domain.usecase

import com.example.data.model.DnsServerProfile
import com.example.utils.LatencyTester
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext

/**
 * Domain use case that asynchronously benchmarks all configured DNS resolvers in parallel
 * and identifies the lowest-latency healthy endpoint.
 */
class BenchmarkDnsServersUseCase {

    data class BenchmarkResult(
        val updatedServers: List<DnsServerProfile>,
        val fastestLatencyMs: Long?
    )

    suspend operator fun invoke(servers: List<DnsServerProfile>): BenchmarkResult =
        withContext(Dispatchers.IO) {
            val updated = servers.map { server ->
                async {
                    val ping = LatencyTester.pingDnsServer(server.primaryIpv4)
                    server.copy(latencyMs = if (ping > 0L) ping else null)
                }
            }.awaitAll()

            val fastest = updated
                .mapNotNull { it.latencyMs }
                .filter { it > 0L }
                .minOrNull()

            BenchmarkResult(
                updatedServers = updated,
                fastestLatencyMs = fastest
            )
        }
}
