package com.example.viewmodel

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.DnsEngineMode
import com.example.data.model.DnsServerProfile
import com.example.data.model.PrivateDnsPreset
import com.example.data.preferences.PreferencesManager
import com.example.data.repository.DnsPresetData
import com.example.service.DnsVpnService
import com.example.service.ShizukuDnsBridgeService
import com.example.service.ShizukuStatus
import com.example.utils.LatencyTester
import com.example.utils.PrivateDnsController
import com.example.utils.PrivateDnsMode
import com.example.utils.PrivateDnsState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class HomeUiState(
    val engineMode: DnsEngineMode = DnsEngineMode.PRIVATE_DNS,
    val privateDnsState: PrivateDnsState = PrivateDnsState(PrivateDnsMode.Opportunistic, null, false),
    val selectedDotPreset: String = "dns.adguard.com",
    val privatePresets: List<PrivateDnsPreset> = DnsPresetData.defaultPrivateDnsPresets,
    val vpnServers: List<DnsServerProfile> = DnsPresetData.defaultVpnDnsServers,
    val selectedVpnServerId: String = "adguard_standard",
    val isVpnActive: Boolean = false,
    val activeVpnServerName: String = "Disconnected",
    val isPinging: Boolean = false,
    val livePingMs: Long? = null,
    val userMessage: String? = null,
    val shizukuStatus: ShizukuStatus = ShizukuStatus.NOT_AVAILABLE
)

class DnsPulseViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = PreferencesManager(application)
    val shizukuBridge: ShizukuDnsBridgeService = ShizukuDnsBridgeService.getInstance(application)

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        refreshSystemPrivateDnsState()
        observeVpnService()
        observeShizukuStatus()
        loadPreferences()
        pingAllServers()
    }

    private fun loadPreferences() {
        viewModelScope.launch {
            prefs.engineModeFlow.collect { mode ->
                _uiState.value = _uiState.value.copy(engineMode = mode)
            }
        }
        viewModelScope.launch {
            prefs.selectedDotPresetFlow.collect { dot ->
                _uiState.value = _uiState.value.copy(selectedDotPreset = dot)
            }
        }
        viewModelScope.launch {
            prefs.lastVpnServerIdFlow.collect { id ->
                _uiState.value = _uiState.value.copy(selectedVpnServerId = id)
            }
        }
    }

    private fun observeShizukuStatus() {
        viewModelScope.launch {
            shizukuBridge.shizukuStatus.collect { status ->
                _uiState.value = _uiState.value.copy(shizukuStatus = status)
                refreshSystemPrivateDnsState()
            }
        }
    }

    private fun observeVpnService() {
        viewModelScope.launch {
            DnsVpnService.isRunning.collect { running ->
                _uiState.value = _uiState.value.copy(isVpnActive = running)
                refreshSystemPrivateDnsState()
            }
        }
        viewModelScope.launch {
            DnsVpnService.activeServerName.collect { name ->
                _uiState.value = _uiState.value.copy(activeVpnServerName = name)
            }
        }
    }

    fun refreshSystemPrivateDnsState() {
        val state = PrivateDnsController.getPrivateDnsState(getApplication())
        shizukuBridge.checkStatus()
        _uiState.value = _uiState.value.copy(privateDnsState = state)
    }

    fun requestShizukuAuthorization() {
        shizukuBridge.requestShizukuPermission()
    }

    fun setEngineMode(mode: DnsEngineMode) {
        viewModelScope.launch {
            prefs.setEngineMode(mode)
            _uiState.value = _uiState.value.copy(engineMode = mode)
        }
    }

    fun selectVpnServer(serverId: String) {
        viewModelScope.launch {
            prefs.setLastVpnServerId(serverId)
            _uiState.value = _uiState.value.copy(selectedVpnServerId = serverId)
            // If VPN currently running, restart with new server
            if (_uiState.value.isVpnActive) {
                val server = _uiState.value.vpnServers.firstOrNull { it.id == serverId }
                if (server != null) {
                    DnsVpnService.startVpn(
                        context = getApplication(),
                        serverName = server.name,
                        primaryIpv4 = server.primaryIpv4,
                        secondaryIpv4 = server.secondaryIpv4,
                        primaryIpv6 = server.primaryIpv6,
                        secondaryIpv6 = server.secondaryIpv6
                    )
                }
            }
        }
    }

    /**
     * Activates a DoT preset using direct WRITE_SECURE_SETTINGS or Shizuku bridge,
     * falling back to clipboard + settings if neither elevated permission is active.
     */
    fun activatePrivateDnsPreset(preset: PrivateDnsPreset, onFallbackRequired: (String) -> Unit) {
        val context = getApplication<Application>()
        viewModelScope.launch {
            prefs.setSelectedDotPreset(preset.hostname)

            // 1. Try standard WRITE_SECURE_SETTINGS in-process
            val directSuccess = PrivateDnsController.setPrivateDnsDirectly(context, preset.hostname)
            if (directSuccess) {
                refreshSystemPrivateDnsState()
                _uiState.value = _uiState.value.copy(
                    userMessage = "Private DNS switched to ${preset.name} (${preset.hostname})"
                )
                measureActivePing(preset.hostname)
                return@launch
            }

            // 2. Try Shizuku API Bridge Service
            if (shizukuBridge.checkStatus() == ShizukuStatus.AUTHORIZED) {
                val shizukuResult = shizukuBridge.setPrivateDns(preset.hostname)
                if (shizukuResult.success) {
                    refreshSystemPrivateDnsState()
                    _uiState.value = _uiState.value.copy(
                        userMessage = "Shizuku bridged Private DNS to ${preset.name} (${preset.hostname})"
                    )
                    measureActivePing(preset.hostname)
                    return@launch
                }
            }

            // 3. Fallback: Copy to clipboard and launch Settings
            onFallbackRequired(preset.hostname)
        }
    }

    fun disablePrivateDns(onFallbackRequired: () -> Unit) {
        val context = getApplication<Application>()
        viewModelScope.launch {
            // 1. Direct in-process check
            val directSuccess = PrivateDnsController.disablePrivateDnsDirectly(context)
            if (directSuccess) {
                refreshSystemPrivateDnsState()
                _uiState.value = _uiState.value.copy(userMessage = "Private DNS disabled (Set to standard)")
                return@launch
            }

            // 2. Shizuku API bridge
            if (shizukuBridge.checkStatus() == ShizukuStatus.AUTHORIZED) {
                val shizukuResult = shizukuBridge.disablePrivateDns()
                if (shizukuResult.success) {
                    refreshSystemPrivateDnsState()
                    _uiState.value = _uiState.value.copy(userMessage = "Shizuku disabled Private DNS")
                    return@launch
                }
            }

            // 3. Fallback
            onFallbackRequired()
        }
    }

    fun grantPermissionViaShizuku() {
        viewModelScope.launch {
            val res = shizukuBridge.grantWriteSecureSettingsViaShizuku()
            if (res.success) {
                refreshSystemPrivateDnsState()
                _uiState.value = _uiState.value.copy(userMessage = "Granted WRITE_SECURE_SETTINGS via Shizuku!")
            } else {
                _uiState.value = _uiState.value.copy(userMessage = "Shizuku error: ${res.error}")
            }
        }
    }

    fun toggleVpn(context: Context) {
        val isRunning = _uiState.value.isVpnActive
        if (isRunning) {
            DnsVpnService.stopVpn(context)
            _uiState.value = _uiState.value.copy(userMessage = "Local DNS VPN Disconnected")
        } else {
            val server = _uiState.value.vpnServers.firstOrNull { it.id == _uiState.value.selectedVpnServerId }
                ?: _uiState.value.vpnServers.first()
            DnsVpnService.startVpn(
                context = context,
                serverName = server.name,
                primaryIpv4 = server.primaryIpv4,
                secondaryIpv4 = server.secondaryIpv4,
                primaryIpv6 = server.primaryIpv6,
                secondaryIpv6 = server.secondaryIpv6
            )
            _uiState.value = _uiState.value.copy(userMessage = "Connected to ${server.name} via local VPN")
            measureActivePing(server.primaryIpv4)
        }
    }

    fun clearUserMessage() {
        _uiState.value = _uiState.value.copy(userMessage = null)
    }

    fun measureActivePing(target: String) {
        viewModelScope.launch {
            val ping = LatencyTester.pingDnsServer(target)
            if (ping > 0) {
                _uiState.value = _uiState.value.copy(livePingMs = ping)
            }
        }
    }

    fun pingAllServers() {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.value = _uiState.value.copy(isPinging = true)
            val updatedServers = _uiState.value.vpnServers.map { server ->
                async {
                    val ping = LatencyTester.pingDnsServer(server.primaryIpv4)
                    server.copy(latencyMs = if (ping > 0) ping else null)
                }
            }.awaitAll()

            val fastest = updatedServers.filter { it.latencyMs != null && it.latencyMs > 0 }
                .minByOrNull { it.latencyMs ?: Long.MAX_VALUE }

            _uiState.value = _uiState.value.copy(
                vpnServers = updatedServers,
                isPinging = false,
                livePingMs = fastest?.latencyMs ?: _uiState.value.livePingMs
            )
        }
    }

    fun autoSelectFastestServer() {
        viewModelScope.launch {
            val fastest = _uiState.value.vpnServers.filter { it.latencyMs != null && it.latencyMs > 0 }
                .minByOrNull { it.latencyMs ?: Long.MAX_VALUE }
            if (fastest != null) {
                selectVpnServer(fastest.id)
                _uiState.value = _uiState.value.copy(
                    userMessage = "Auto-selected fastest DNS: ${fastest.name} (${fastest.latencyMs}ms)"
                )
            } else {
                pingAllServers()
            }
        }
    }

    fun addCustomServer(
        name: String,
        pIpv4: String,
        sIpv4: String,
        pIpv6: String,
        dotHostname: String
    ) {
        val newProfile = DnsServerProfile(
            id = "custom_${System.currentTimeMillis()}",
            name = name.ifBlank { "Custom DNS" },
            description = "User-defined DNS server profile",
            primaryIpv4 = pIpv4.trim(),
            secondaryIpv4 = sIpv4.trim(),
            primaryIpv6 = pIpv6.trim(),
            dotHostname = dotHostname.trim(),
            isCustom = true,
            iconKey = "dns"
        )
        val list = _uiState.value.vpnServers.toMutableList()
        list.add(0, newProfile)
        _uiState.value = _uiState.value.copy(
            vpnServers = list,
            selectedVpnServerId = newProfile.id,
            userMessage = "Added custom server: ${newProfile.name}"
        )
        viewModelScope.launch {
            val ping = LatencyTester.pingDnsServer(newProfile.primaryIpv4)
            val updated = _uiState.value.vpnServers.map {
                if (it.id == newProfile.id) it.copy(latencyMs = if (ping > 0) ping else null) else it
            }
            _uiState.value = _uiState.value.copy(vpnServers = updated)
        }
    }

    override fun onCleared() {
        super.onCleared()
        shizukuBridge.cleanUp()
    }
}
