package com.example.utils

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import android.util.Log

sealed class PrivateDnsMode(val rawValue: String) {
    object Off : PrivateDnsMode("off")
    object Opportunistic : PrivateDnsMode("opportunistic") // Auto
    object Strict : PrivateDnsMode("hostname") // Custom provider hostname
    data class Unknown(val value: String) : PrivateDnsMode(value)

    companion object {
        fun fromString(value: String?): PrivateDnsMode = when (value?.lowercase()) {
            "off" -> Off
            "opportunistic", "auto" -> Opportunistic
            "hostname", "strict" -> Strict
            null -> Opportunistic
            else -> Unknown(value)
        }
    }
}

data class PrivateDnsState(
    val mode: PrivateDnsMode,
    val hostname: String?,
    val hasWriteSecureSettings: Boolean
)

object PrivateDnsController {
    private const val TAG = "PrivateDnsController"
    private const val SETTING_PRIVATE_DNS_MODE = "private_dns_mode"
    private const val SETTING_PRIVATE_DNS_SPECIFIER = "private_dns_specifier"

    fun hasWriteSecureSettingsPermission(context: Context): Boolean {
        return context.checkSelfPermission(android.Manifest.permission.WRITE_SECURE_SETTINGS) ==
                PackageManager.PERMISSION_GRANTED
    }

    fun getPrivateDnsState(context: Context): PrivateDnsState {
        val cr = context.contentResolver
        val modeStr = try {
            Settings.Global.getString(cr, SETTING_PRIVATE_DNS_MODE)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to read private_dns_mode", e)
            null
        }

        val specifier = try {
            Settings.Global.getString(cr, SETTING_PRIVATE_DNS_SPECIFIER)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to read private_dns_specifier", e)
            null
        }

        val mode = PrivateDnsMode.fromString(modeStr)
        val hasPermission = hasWriteSecureSettingsPermission(context)
        return PrivateDnsState(
            mode = mode,
            hostname = specifier?.takeIf { it.isNotBlank() },
            hasWriteSecureSettings = hasPermission
        )
    }

    /**
     * Direct toggle if WRITE_SECURE_SETTINGS is granted.
     * @return true if successfully changed, false if permission missing or failed.
     */
    fun setPrivateDnsDirectly(context: Context, hostname: String?): Boolean {
        if (!hasWriteSecureSettingsPermission(context)) {
            return false
        }
        return try {
            val cr = context.contentResolver
            if (hostname.isNullOrBlank()) {
                // Set to Auto / Opportunistic
                Settings.Global.putString(cr, SETTING_PRIVATE_DNS_MODE, "opportunistic")
                Settings.Global.putString(cr, SETTING_PRIVATE_DNS_SPECIFIER, "")
            } else {
                // Strict hostname mode
                Settings.Global.putString(cr, SETTING_PRIVATE_DNS_MODE, "hostname")
                Settings.Global.putString(cr, SETTING_PRIVATE_DNS_SPECIFIER, hostname)
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error writing secure settings", e)
            false
        }
    }

    /**
     * Disable Private DNS directly if permitted
     */
    fun disablePrivateDnsDirectly(context: Context): Boolean {
        if (!hasWriteSecureSettingsPermission(context)) return false
        return try {
            val cr = context.contentResolver
            Settings.Global.putString(cr, SETTING_PRIVATE_DNS_MODE, "off")
            Settings.Global.putString(cr, SETTING_PRIVATE_DNS_SPECIFIER, "")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to disable private DNS directly", e)
            false
        }
    }

    /**
     * Copies hostname to clipboard and creates intent to launch system Network & Internet / Private DNS settings
     */
    fun copyToClipboardAndGetSettingsIntent(context: Context, hostname: String): Intent {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("Private DNS Provider", hostname)
        clipboard.setPrimaryClip(clip)

        // Try launching specific Private DNS / Wireless Settings
        val intent = Intent(Settings.ACTION_WIRELESS_SETTINGS).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        return intent
    }

    fun getAdbCommand(context: Context): String {
        val pkg = context.packageName
        return "adb shell pm grant $pkg android.permission.WRITE_SECURE_SETTINGS"
    }

    fun getDirectAdbDnsCommand(hostname: String): String {
        return "adb shell settings put global private_dns_mode hostname && adb shell settings put global private_dns_specifier $hostname"
    }
}
