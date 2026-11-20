package com.example.data.preferences

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.data.model.DnsEngineMode
import com.example.ui.theme.AppAppearanceMode
import com.example.ui.theme.AppThemeStyle
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "dns_pulse_prefs")

data class AllPreferences(
    val engineMode: DnsEngineMode,
    val autoStartBoot: Boolean,
    val lastVpnServerId: String,
    val glowIntensity: Boolean,
    val themeStyle: AppThemeStyle,
    val appearanceMode: AppAppearanceMode,
    val simpleMode: Boolean,
    val fullScreenMode: Boolean
)

class PreferencesManager(private val context: Context) {

    companion object {
        val KEY_ENGINE_MODE = stringPreferencesKey("key_engine_mode")
        val KEY_AUTO_START_BOOT = booleanPreferencesKey("key_auto_start_boot")
        val KEY_LAST_VPN_SERVER_ID = stringPreferencesKey("key_last_vpn_server_id")
        val KEY_GLOW_INTENSITY = booleanPreferencesKey("key_glow_intensity")
        val KEY_THEME_STYLE = stringPreferencesKey("key_theme_style")
        val KEY_APPEARANCE_MODE = stringPreferencesKey("key_appearance_mode")
        val KEY_SIMPLE_MODE = booleanPreferencesKey("key_simple_mode")
        val KEY_FULL_SCREEN_MODE = booleanPreferencesKey("key_full_screen_mode")
    }

    val allPreferencesFlow: Flow<AllPreferences> = context.dataStore.data
        .map { prefs ->
            val engineMode = try {
                DnsEngineMode.valueOf(prefs[KEY_ENGINE_MODE] ?: DnsEngineMode.PRIVATE_DNS.name)
            } catch (_: Exception) {
                DnsEngineMode.PRIVATE_DNS
            }
            val themeStyle = try {
                AppThemeStyle.valueOf(prefs[KEY_THEME_STYLE] ?: AppThemeStyle.CHROMATIC_FROSTED_LIQUID.name)
            } catch (_: Exception) {
                AppThemeStyle.CHROMATIC_FROSTED_LIQUID
            }
            val appearanceMode = try {
                AppAppearanceMode.valueOf(prefs[KEY_APPEARANCE_MODE] ?: AppAppearanceMode.DARK.name)
            } catch (_: Exception) {
                AppAppearanceMode.DARK
            }
            AllPreferences(
                engineMode = engineMode,
                autoStartBoot = prefs[KEY_AUTO_START_BOOT] ?: false,
                lastVpnServerId = prefs[KEY_LAST_VPN_SERVER_ID] ?: "adguard_standard",
                glowIntensity = prefs[KEY_GLOW_INTENSITY] ?: true,
                themeStyle = themeStyle,
                appearanceMode = appearanceMode,
                simpleMode = prefs[KEY_SIMPLE_MODE] ?: false,
                fullScreenMode = prefs[KEY_FULL_SCREEN_MODE] ?: false
            )
        }
        .distinctUntilChanged()

    val autoStartBootFlow: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_AUTO_START_BOOT] ?: false
    }

    val themeStyleFlow: Flow<AppThemeStyle> = context.dataStore.data.map { prefs ->
        val raw = prefs[KEY_THEME_STYLE] ?: AppThemeStyle.CHROMATIC_FROSTED_LIQUID.name
        try {
            AppThemeStyle.valueOf(raw)
        } catch (_: Exception) {
            AppThemeStyle.CHROMATIC_FROSTED_LIQUID
        }
    }

    val appearanceModeFlow: Flow<AppAppearanceMode> = context.dataStore.data.map { prefs ->
        val raw = prefs[KEY_APPEARANCE_MODE] ?: AppAppearanceMode.DARK.name
        try {
            AppAppearanceMode.valueOf(raw)
        } catch (_: Exception) {
            AppAppearanceMode.DARK
        }
    }

    val simpleModeFlow: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_SIMPLE_MODE] ?: false
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

    suspend fun setGlowIntensity(high: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[KEY_GLOW_INTENSITY] = high
        }
    }

    suspend fun setThemeStyle(style: AppThemeStyle) {
        context.dataStore.edit { prefs ->
            prefs[KEY_THEME_STYLE] = style.name
        }
    }

    suspend fun setAppearanceMode(mode: AppAppearanceMode) {
        context.dataStore.edit { prefs ->
            prefs[KEY_APPEARANCE_MODE] = mode.name
        }
    }

    suspend fun setSimpleMode(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[KEY_SIMPLE_MODE] = enabled
        }
    }

    suspend fun setFullScreenMode(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[KEY_FULL_SCREEN_MODE] = enabled
        }
    }
}
