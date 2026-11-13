package com.example

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.components.UpdateDialog
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.ServersScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.theme.DarkSurfaceElevated
import com.example.ui.theme.DeepVoidBlack
import com.example.ui.theme.DnsPulseTheme
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.utils.PrivateDnsController
import com.example.utils.UpdateDownloadState
import com.example.utils.UpdateManager
import com.example.viewmodel.DnsPulseViewModel
import java.io.File

class MainActivity : ComponentActivity() {

    private val viewModel: DnsPulseViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            DnsPulseTheme {
                val uiState by viewModel.uiState.collectAsState()
                val snackbarHostState = remember { SnackbarHostState() }
                var selectedTab by remember { mutableIntStateOf(0) }
                var pendingApkInstall by remember { mutableStateOf<File?>(null) }

                val installPermissionLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.StartActivityForResult()
                ) {
                    val file = pendingApkInstall
                    if (file != null && UpdateManager.canInstallPackages(this@MainActivity)) {
                        UpdateManager.triggerApkInstall(this@MainActivity, file)
                        pendingApkInstall = null
                    }
                }

                val triggerApkInstallFlow: (File) -> Unit = { apkFile ->
                    when (val check = UpdateManager.verifyApkForDirectUpdate(this@MainActivity, apkFile)) {
                        is UpdateManager.ApkCompatibilityResult.Incompatible -> {
                            Toast.makeText(
                                this@MainActivity,
                                check.reason,
                                Toast.LENGTH_LONG
                            ).show()
                        }
                        is UpdateManager.ApkCompatibilityResult.Compatible -> {
                            if (UpdateManager.canInstallPackages(this@MainActivity)) {
                                val launched = UpdateManager.triggerApkInstall(this@MainActivity, apkFile)
                                if (!launched) {
                                    Toast.makeText(
                                        this@MainActivity,
                                        "Unable to launch package installer.",
                                        Toast.LENGTH_LONG
                                    ).show()
                                }
                            } else {
                                pendingApkInstall = apkFile
                                Toast.makeText(
                                    this@MainActivity,
                                    "Please allow \"Install unknown apps\" for DNS Pulse to update.",
                                    Toast.LENGTH_LONG
                                ).show()
                                installPermissionLauncher.launch(
                                    UpdateManager.buildManageUnknownSourcesIntent(this@MainActivity)
                                )
                            }
                        }
                    }
                }

                LaunchedEffect(uiState.userMessage) {
                    uiState.userMessage?.let { msg ->
                        snackbarHostState.showSnackbar(msg)
                        viewModel.clearUserMessage()
                    }
                }

                // Automatically launch installer when APK download finishes
                LaunchedEffect(uiState.updateState) {
                    val state = uiState.updateState
                    if (state is UpdateDownloadState.ReadyToInstall) {
                        triggerApkInstallFlow(state.apkFile)
                    }
                }

                Scaffold(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(DeepVoidBlack),
                    snackbarHost = { SnackbarHost(snackbarHostState) },
                    contentWindowInsets = WindowInsets.safeDrawing,
                    bottomBar = {
                        NavigationBar(
                            containerColor = DarkSurfaceElevated,
                            contentColor = TextPrimary,
                            tonalElevation = 8.dp,
                            modifier = Modifier
                                .windowInsetsPadding(WindowInsets.navigationBars)
                                .testTag("main_bottom_nav")
                        ) {
                            NavigationBarItem(
                                selected = selectedTab == 0,
                                onClick = { selectedTab = 0 },
                                icon = {
                                    Icon(
                                        imageVector = if (selectedTab == 0) Icons.Filled.Home else Icons.Outlined.Home,
                                        contentDescription = "Home"
                                    )
                                },
                                label = { Text("Shield", fontSize = 11.sp) },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = DeepVoidBlack,
                                    indicatorColor = ElectricCyan,
                                    selectedTextColor = ElectricCyan,
                                    unselectedIconColor = TextSecondary,
                                    unselectedTextColor = TextSecondary
                                ),
                                modifier = Modifier.testTag("nav_item_home")
                            )

                            NavigationBarItem(
                                selected = selectedTab == 1,
                                onClick = { selectedTab = 1 },
                                icon = {
                                    Icon(
                                        imageVector = if (selectedTab == 1) Icons.Filled.Dns else Icons.Outlined.Dns,
                                        contentDescription = "Benchmark"
                                    )
                                },
                                label = { Text("Benchmark", fontSize = 11.sp) },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = DeepVoidBlack,
                                    indicatorColor = ElectricCyan,
                                    selectedTextColor = ElectricCyan,
                                    unselectedIconColor = TextSecondary,
                                    unselectedTextColor = TextSecondary
                                ),
                                modifier = Modifier.testTag("nav_item_servers")
                            )

                            NavigationBarItem(
                                selected = selectedTab == 2,
                                onClick = { selectedTab = 2 },
                                icon = {
                                    Icon(
                                        imageVector = if (selectedTab == 2) Icons.Filled.Settings else Icons.Outlined.Settings,
                                        contentDescription = "Settings"
                                    )
                                },
                                label = { Text("Settings", fontSize = 11.sp) },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = DeepVoidBlack,
                                    indicatorColor = ElectricCyan,
                                    selectedTextColor = ElectricCyan,
                                    unselectedIconColor = TextSecondary,
                                    unselectedTextColor = TextSecondary
                                ),
                                modifier = Modifier.testTag("nav_item_settings")
                            )
                        }
                    }
                ) { innerPadding ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    ) {
                        when (selectedTab) {
                            0 -> HomeScreen(
                                uiState = uiState,
                                viewModel = viewModel,
                                onFallbackRequired = { hostname ->
                                    val intent = PrivateDnsController.copyToClipboardAndGetSettingsIntent(this@MainActivity, hostname)
                                    Toast.makeText(
                                        this@MainActivity,
                                        "Copied \"$hostname\"! Paste it under Private DNS provider.",
                                        Toast.LENGTH_LONG
                                    ).show()
                                    try {
                                        startActivity(intent)
                                    } catch (_: Exception) {
                                        startActivity(Intent(Settings.ACTION_SETTINGS))
                                    }
                                },
                                onLaunchWirelessSettings = {
                                    try {
                                        startActivity(Intent(Settings.ACTION_WIRELESS_SETTINGS))
                                    } catch (_: Exception) {
                                        startActivity(Intent(Settings.ACTION_SETTINGS))
                                    }
                                },
                                onNavigateToServers = { selectedTab = 1 }
                            )
                            1 -> ServersScreen(
                                uiState = uiState,
                                viewModel = viewModel
                            )
                            2 -> SettingsScreen(
                                uiState = uiState,
                                viewModel = viewModel,
                                onLaunchWirelessSettings = {
                                    try {
                                        startActivity(Intent(Settings.ACTION_WIRELESS_SETTINGS))
                                    } catch (_: Exception) {
                                        startActivity(Intent(Settings.ACTION_SETTINGS))
                                    }
                                }
                            )
                        }
                    }
                }

                if (uiState.showUpdateDialog && uiState.updateState != UpdateDownloadState.Idle) {
                    UpdateDialog(
                        updateState = uiState.updateState,
                        onStartDownload = { release -> viewModel.startApkDownload(release) },
                        onInstallApk = triggerApkInstallFlow,
                        onRetryCheck = { viewModel.checkForUpdates(isManual = true) },
                        onDismiss = { viewModel.dismissUpdateDialog() }
                    )
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        viewModel.checkForUpdatesOnAppEnter()
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshSystemPrivateDnsState()
    }
}
