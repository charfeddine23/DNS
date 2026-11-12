package com.example.data.repository

import com.example.data.model.DnsServerProfile
import com.example.data.model.PrivateDnsPreset

object DnsPresetData {
    val defaultPrivateDnsPresets = listOf(
        PrivateDnsPreset(
            id = "adguard_default",
            name = "AdGuard DNS",
            tag = "Ad Blocking & Anti-Tracker",
            hostname = "dns.adguard.com",
            description = "Blocks intrusive ads, analytics, trackers, and malicious phishing domains system-wide.",
            isAdGuardShield = true
        ),
        PrivateDnsPreset(
            id = "adguard_family",
            name = "AdGuard Family",
            tag = "Family Protection",
            hostname = "family.adguard-dns.com",
            description = "Blocks adult content, enables SafeSearch on search engines, and blocks ads.",
            isAdGuardShield = false
        ),
        PrivateDnsPreset(
            id = "cloudflare",
            name = "Cloudflare 1.1.1.1",
            tag = "Ultra Fast & Privacy",
            hostname = "one.one.one.one",
            description = "World's fastest DNS resolver with audited privacy protection.",
            isAdGuardShield = false
        ),
        PrivateDnsPreset(
            id = "quad9",
            name = "Quad9 Security",
            tag = "Threat Intelligence",
            hostname = "dns.quad9.net",
            description = "Blocks cyber threats, malware, and fraudulent botnet infrastructure.",
            isAdGuardShield = false
        ),
        PrivateDnsPreset(
            id = "google",
            name = "Google Public DNS",
            tag = "Global Anycast",
            hostname = "dns.google",
            description = "High-availability, fast global geo-distributed DNS resolution.",
            isAdGuardShield = false
        )
    )

    val defaultVpnDnsServers = listOf(
        DnsServerProfile(
            id = "adguard_standard",
            name = "AdGuard DNS",
            description = "Comprehensive ad and tracker blocking",
            primaryIpv4 = "94.140.14.14",
            secondaryIpv4 = "94.140.15.15",
            primaryIpv6 = "2a10:50c0::ad1:ff",
            secondaryIpv6 = "2a10:50c0::ad2:ff",
            dotHostname = "dns.adguard.com",
            iconKey = "shield"
        ),
        DnsServerProfile(
            id = "cloudflare_standard",
            name = "Cloudflare (1.1.1.1)",
            description = "High-performance privacy-focused DNS",
            primaryIpv4 = "1.1.1.1",
            secondaryIpv4 = "1.0.0.1",
            primaryIpv6 = "2606:4700:4700::1111",
            secondaryIpv6 = "2606:4700:4700::1001",
            dotHostname = "one.one.one.one",
            iconKey = "bolt"
        ),
        DnsServerProfile(
            id = "google_standard",
            name = "Google Public DNS",
            description = "Global anycast routing & reliability",
            primaryIpv4 = "8.8.8.8",
            secondaryIpv4 = "8.8.4.4",
            primaryIpv6 = "2001:4860:4860::8888",
            secondaryIpv6 = "2001:4860:4860::8844",
            dotHostname = "dns.google",
            iconKey = "public"
        ),
        DnsServerProfile(
            id = "quad9_standard",
            name = "Quad9 DNS",
            description = "Automated malicious domain blocking",
            primaryIpv4 = "9.9.9.9",
            secondaryIpv4 = "149.112.112.112",
            primaryIpv6 = "2620:fe::fe",
            secondaryIpv6 = "2620:fe::9",
            dotHostname = "dns.quad9.net",
            iconKey = "security"
        ),
        DnsServerProfile(
            id = "opendns_standard",
            name = "Cisco OpenDNS",
            description = "Reliable enterprise-grade protection",
            primaryIpv4 = "208.67.222.222",
            secondaryIpv4 = "208.67.220.220",
            primaryIpv6 = "",
            secondaryIpv6 = "",
            dotHostname = "",
            iconKey = "dns"
        )
    )
}
