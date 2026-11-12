package com.example.data.preferences

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.data.model.DnsEngineMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "dns_pulse_prefs")

class PreferencesManager(private val context: Context) {

    companion object {
        val KEY_ENGINE_MODE = stringPreferencesKey("key_engine_mode")
        val KEY_AUTO_START_BOOT = booleanPreferencesKey("key_auto_start_boot")
        val KEY_LAST_VPN_SERVER_ID = stringPreferencesKey("key_last_vpn_server_id")
        val KEY_SELECTED_DOT_PRESET = stringPreferencesKey("key_selected_dot_preset")
        val KEY_GLOW_INTENSITY = booleanPreferencesKey("key_glow_intensity")
    }

    val engineModeFlow: Flow<DnsEngineMode> = context.dataStore.data.map { prefs ->
        val raw = prefs[KEY_ENGINE_MODE] ?: DnsEngineMode.PRIVATE_DNS.name
        try {
            DnsEngineMode.valueOf(raw)
        } catch (_: Exception) {
            DnsEngineMode.PRIVATE_DNS
        }
    }

    val autoStartBootFlow: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_AUTO_START_BOOT] ?: false
    }

    val lastVpnServerIdFlow: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[KEY_LAST_VPN_SERVER_ID] ?: "adguard_standard"
    }

    val selectedDotPresetFlow: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[KEY_SELECTED_DOT_PRESET] ?: "dns.adguard.com"
    }

    val glowIntensityFlow: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_GLOW_INTENSITY] ?: true
    }

    suspend fun setEngineMode(mode: DnsEngineMode) {
        context.dataStore.edit { prefs ->
            prefs[KEY_ENGINE_MODE] = mode.name
        }
    }

    suspend fun setAutoStartBoot(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[KEY_AUTO_START_BOOT] = enabled
        }
    }

    suspend fun setLastVpnServerId(id: String) {
        context.dataStore.edit { prefs ->
            prefs[KEY_LAST_VPN_SERVER_ID] = id
        }
    }

    suspend fun setSelectedDotPreset(hostname: String) {
        context.dataStore.edit { prefs ->
            prefs[KEY_SELECTED_DOT_PRESET] = hostname
        }
    }

    suspend fun setGlowIntensity(high: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[KEY_GLOW_INTENSITY] = high
        }
    }
}
