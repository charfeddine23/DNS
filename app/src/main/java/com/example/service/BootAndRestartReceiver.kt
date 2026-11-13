package com.example.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.util.Log
import com.example.data.preferences.PreferencesManager
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

class BootAndRestartReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "BootAndRestartReceiver"
    }

    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        val appContext = context.applicationContext

        val shouldResume = when (action) {
            Intent.ACTION_MY_PACKAGE_REPLACED -> {
                DnsVpnService.isShouldStayActive(appContext)
            }
            Intent.ACTION_BOOT_COMPLETED,
            "android.intent.action.QUICKBOOT_POWERON",
            "com.htc.intent.action.QUICKBOOT_POWERON" -> {
                val autoStartEnabled = try {
                    runBlocking { PreferencesManager(appContext).autoStartBootFlow.first() }
                } catch (_: Exception) {
                    false
                }
                autoStartEnabled || DnsVpnService.isShouldStayActive(appContext)
            }
            else -> false
        }

        if (!shouldResume) return

        val needsConsent = try {
            VpnService.prepare(appContext) != null
        } catch (_: Exception) {
            true
        }

        if (needsConsent) {
            Log.w(TAG, "Skipping auto-resume because VPN permission has not been granted yet.")
            return
        }

        val config = DnsVpnService.restoreLastKnownConfig(appContext)
        DnsVpnService.startVpn(
            context = appContext,
            serverName = config.serverName,
            primaryIpv4 = config.primaryIpv4,
            secondaryIpv4 = config.secondaryIpv4,
            primaryIpv6 = config.primaryIpv6,
            secondaryIpv6 = config.secondaryIpv6
        )
    }
}
