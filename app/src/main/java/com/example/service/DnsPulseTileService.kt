package com.example.service

import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.example.utils.PrivateDnsController
import com.example.utils.PrivateDnsMode

class DnsPulseTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        updateTileState()
    }

    override fun onClick() {
        super.onClick()
        val privateState = PrivateDnsController.getPrivateDnsState(this)
        val vpnActive = DnsVpnService.isRunning.value

        if (privateState.hasWriteSecureSettings) {
            // Can toggle directly
            if (privateState.mode is PrivateDnsMode.Strict && privateState.hostname == "dns.adguard.com") {
                // Disable / set to opportunistic
                PrivateDnsController.setPrivateDnsDirectly(this, null)
            } else {
                // Activate AdGuard Private DNS
                PrivateDnsController.setPrivateDnsDirectly(this, "dns.adguard.com")
            }
        } else if (vpnActive) {
            // Stop VPN
            DnsVpnService.stopVpn(this)
        } else {
            // Start default AdGuard VPN
            DnsVpnService.startVpn(
                context = this,
                serverName = "AdGuard DNS",
                primaryIpv4 = "94.140.14.14",
                secondaryIpv4 = "94.140.15.15"
            )
        }
        updateTileState()
    }

    private fun updateTileState() {
        val tile = qsTile ?: return
        val privateState = PrivateDnsController.getPrivateDnsState(this)
        val vpnActive = DnsVpnService.isRunning.value

        val isAdGuardDoT = privateState.mode is PrivateDnsMode.Strict &&
                privateState.hostname.equals("dns.adguard.com", ignoreCase = true)

        val isActive = isAdGuardDoT || vpnActive

        tile.state = if (isActive) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.label = "DNS Pulse"
        tile.subtitle = when {
            isAdGuardDoT -> "DoT: AdGuard Active"
            vpnActive -> "VPN: Active"
            privateState.mode is PrivateDnsMode.Strict -> "DoT: ${privateState.hostname}"
            else -> "Off / Auto"
        }
        tile.updateTile()
    }
}
