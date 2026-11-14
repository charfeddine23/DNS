package com.example.viewmodel

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.BuildConfig
import com.example.data.model.DnsEngineMode
import com.example.data.model.DnsServerProfile
import com.example.data.model.PrivateDnsPreset
import com.example.data.preferences.PreferencesManager
import com.example.data.repository.DnsPresetData
import com.example.service.DnsVpnService
import com.example.service.ShizukuDnsBridgeService
import com.example.service.ShizukuStatus
import com.example.ui.theme.AppThemeStyle
import com.example.utils.GitHubReleaseInfo
import com.example.utils.LatencyTester
import com.example.utils.PrivateDnsController
import com.example.utils.PrivateDnsMode
import com.example.utils.PrivateDnsState
import com.example.utils.UpdateCheckResult
import com.example.utils.UpdateDownloadState
import com.example.utils.UpdateManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
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
    val shizukuStatus: ShizukuStatus = ShizukuStatus.NOT_AVAILABLE,
    val autoStartBoot: Boolean = false,
    val ambientGlow: Boolean = true,
    val themeStyle: AppThemeStyle = AppThemeStyle.CHROMATIC_FROSTED_LIQUID,
    val updateState: UpdateDownloadState = UpdateDownloadState.Idle,
    val showUpdateDialog: Boolean = false
)

class DnsPulseViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = PreferencesManager(application)
    val shizukuBridge: ShizukuDnsBridgeService = ShizukuDnsBridgeService.getInstance(application)

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private var updateCheckJob: Job? = null
    private var lastAutoCheckAtMs: Long = 0L

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
        viewModelScope.launch {
            prefs.autoStartBootFlow.collect { enabled ->
                _uiState.value = _uiState.value.copy(autoStartBoot = enabled)
            }
        }
        viewModelScope.launch {
            prefs.glowIntensityFlow.collect { enabled ->
                _uiState.value = _uiState.value.copy(ambientGlow = enabled)
            }
        }
        viewModelScope.launch {
            prefs.themeStyleFlow.collect { style ->
                _uiState.value = _uiState.value.copy(themeStyle = style)
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

    fun setAutoStartBoot(enabled: Boolean) {
        viewModelScope.launch {
            prefs.setAutoStartBoot(enabled)
            _uiState.value = _uiState.value.copy(autoStartBoot = enabled)
        }
    }

    fun setAmbientGlow(enabled: Boolean) {
        viewModelScope.launch {
            prefs.setGlowIntensity(enabled)
            _uiState.value = _uiState.value.copy(ambientGlow = enabled)
        }
    }

    fun setThemeStyle(style: AppThemeStyle) {
        viewModelScope.launch {
            prefs.setThemeStyle(style)
            _uiState.value = _uiState.value.copy(themeStyle = style)
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
        val isRunning = DnsVpnService.isRunning.value || _uiState.value.isVpnActive
        if (isRunning) {
            stopVpn(context)
        } else {
            startVpn(context)
        }
    }

    fun startVpn(context: Context) {
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
        _uiState.value = _uiState.value.copy(
            userMessage = "Connecting to ${server.name} via local VPN..."
        )
        measureActivePing(server.primaryIpv4)
    }

    fun stopVpn(context: Context) {
        DnsVpnService.stopVpn(context)
        _uiState.value = _uiState.value.copy(
            isVpnActive = false,
            activeVpnServerName = "Disconnected",
            userMessage = "Local DNS VPN Disconnected"
        )
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

    fun checkForUpdatesOnAppEnter() {
        val currentState = _uiState.value
        if (updateCheckJob?.isActive == true) return
        if (currentState.updateState is UpdateDownloadState.Downloading ||
            currentState.updateState is UpdateDownloadState.ReadyToInstall
        ) {
            return
        }
        if (currentState.showUpdateDialog && currentState.updateState is UpdateDownloadState.Available) {
            return
        }
        val now = System.currentTimeMillis()
        if (now - lastAutoCheckAtMs < 1_500L) return
        lastAutoCheckAtMs = now
        checkForUpdates(isManual = false)
    }

    fun checkForUpdates(isManual: Boolean = false) {
        if (!isManual && updateCheckJob?.isActive == true) return
        if (isManual) {
            updateCheckJob?.cancel()
        }

        updateCheckJob = viewModelScope.launch {
            if (isManual) {
                _uiState.value = _uiState.value.copy(
                    updateState = UpdateDownloadState.Checking,
                    showUpdateDialog = true
                )
            }

            when (val result = UpdateManager.checkForUpdate(BuildConfig.VERSION_NAME)) {
                is UpdateCheckResult.UpdateAvailable -> {
                    _uiState.value = _uiState.value.copy(
                        updateState = UpdateDownloadState.Available(result.release),
                        showUpdateDialog = true
                    )
                }
                is UpdateCheckResult.UpToDate -> {
                    val latestNote = result.latestTag?.let { " • GitHub: $it" }.orEmpty()
                    _uiState.value = _uiState.value.copy(
                        updateState = UpdateDownloadState.Idle,
                        showUpdateDialog = false,
                        userMessage = if (isManual) "Up to date (Installed: v${BuildConfig.VERSION_NAME}$latestNote)" else _uiState.value.userMessage
                    )
                }
                is UpdateCheckResult.Error -> {
                    if (isManual) {
                        _uiState.value = _uiState.value.copy(
                            updateState = UpdateDownloadState.Error(result.message),
                            showUpdateDialog = true
                        )
                    }
                }
            }
        }
    }

    fun startApkDownload(release: GitHubReleaseInfo) {
        val context = getApplication<Application>()
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                updateState = UpdateDownloadState.Downloading(
                    release = release,
                    progressPercent = 0,
                    downloadedBytes = 0L,
                    totalBytes = release.apkSizeBytes
                )
            )

            val downloadResult = UpdateManager.downloadApk(context, release) { percent, downloaded, total ->
                _uiState.value = _uiState.value.copy(
                    updateState = UpdateDownloadState.Downloading(
                        release = release,
                        progressPercent = percent,
                        downloadedBytes = downloaded,
                        totalBytes = total
                    )
                )
            }

            downloadResult
                .onSuccess { apkFile ->
                    _uiState.value = _uiState.value.copy(
                        updateState = UpdateDownloadState.ReadyToInstall(apkFile, release)
                    )
                }
                .onFailure { error ->
                    _uiState.value = _uiState.value.copy(
                        updateState = UpdateDownloadState.Error(
                            message = error.localizedMessage ?: "Failed to download APK",
                            release = release
                        )
                    )
                }
        }
    }

    fun dismissUpdateDialog() {
        _uiState.value = _uiState.value.copy(
            showUpdateDialog = false,
            updateState = if (_uiState.value.updateState is UpdateDownloadState.Available ||
                _uiState.value.updateState is UpdateDownloadState.Error ||
                _uiState.value.updateState is UpdateDownloadState.Checking
            ) UpdateDownloadState.Idle else _uiState.value.updateState
        )
    }

    override fun onCleared() {
        super.onCleared()
        shizukuBridge.cleanUp()
    }
}
