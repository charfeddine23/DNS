package com.example

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.PagerSnapDistance
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import com.example.data.model.DnsEngineMode
import com.example.ui.components.BreathingAtmosphere
import com.example.ui.components.GlassCard
import com.example.ui.components.UpdateDialog
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.ServersScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.theme.AppThemeStyle
import com.example.ui.theme.DarkSurfaceElevated
import com.example.ui.theme.DeepVoidBlack
import com.example.ui.theme.DnsPulseTheme
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.EmeraldMint
import com.example.ui.theme.GlassBorder
import com.example.ui.theme.GlassTopSpecular
import com.example.ui.theme.HoloViolet
import com.example.ui.theme.LocalAppThemeStyle
import com.example.ui.theme.LocalSimpleMode
import com.example.ui.theme.NeuBaseDark
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.utils.PrivateDnsController
import com.example.utils.PrivateDnsMode
import com.example.utils.UpdateDownloadState
import com.example.utils.UpdateManager
import com.example.viewmodel.DnsPulseViewModel
import kotlinx.coroutines.launch
import java.io.File
import kotlin.math.absoluteValue

private data class NavTabSpec(
    val index: Int,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String,
    val contentDesc: String
)

class MainActivity : ComponentActivity() {

    private val viewModel: DnsPulseViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val uiState by viewModel.uiState.collectAsState()
            DnsPulseTheme(
                themeStyle = uiState.themeStyle,
                simpleMode = uiState.simpleMode
            ) {
                val snackbarHostState = remember { SnackbarHostState() }
                val pagerState = rememberPagerState(initialPage = 0, pageCount = { 3 })
                val coroutineScope = rememberCoroutineScope()
                var pendingApkInstall by remember { mutableStateOf<File?>(null) }

                val slideToPage: (Int) -> Unit = { targetPage ->
                    coroutineScope.launch {
                        if (uiState.simpleMode) {
                            pagerState.scrollToPage(targetPage)
                        } else {
                            pagerState.animateScrollToPage(
                                page = targetPage,
                                animationSpec = spring(
                                    dampingRatio = 0.86f,
                                    stiffness = Spring.StiffnessMediumLow
                                )
                            )
                        }
                    }
                }

                // Pressing back from Benchmark or Settings smoothly slides back to Home (Shield)
                BackHandler(enabled = pagerState.currentPage != 0) {
                    slideToPage(0)
                }

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

                LaunchedEffect(uiState.updateState) {
                    val state = uiState.updateState
                    if (state is UpdateDownloadState.ReadyToInstall) {
                        triggerApkInstallFlow(state.apkFile)
                    }
                }

                val isDoTActive = uiState.privateDnsState.mode is PrivateDnsMode.Strict
                val isAdGuardDoT = isDoTActive &&
                    uiState.privateDnsState.hostname.equals("dns.adguard.com", ignoreCase = true)
                val isProtected = if (uiState.engineMode == DnsEngineMode.PRIVATE_DNS) {
                    isDoTActive
                } else {
                    uiState.isVpnActive
                }
                val continuousPagePos = pagerState.currentPage + pagerState.currentPageOffsetFraction

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(DeepVoidBlack)
                ) {
                    // Full-bleed living, breathing cyber atmosphere behind all pages (omitted in Simple Mode)
                    if (!uiState.simpleMode) {
                        BreathingAtmosphere(
                            isProtected = isProtected,
                            isAdGuardShield = isAdGuardDoT,
                            ambientGlowEnabled = uiState.ambientGlow,
                            pageOffsetFraction = continuousPagePos
                        )
                    }

                    Scaffold(
                        modifier = Modifier.fillMaxSize(),
                        containerColor = Color.Transparent,
                        snackbarHost = {
                            SnackbarHost(
                                hostState = snackbarHostState,
                                modifier = Modifier.padding(bottom = 86.dp)
                            )
                        },
                        contentWindowInsets = WindowInsets.safeDrawing
                    ) { innerPadding ->
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(
                                    top = innerPadding.calculateTopPadding()
                                )
                        ) {
                            HorizontalPager(
                                state = pagerState,
                                beyondViewportPageCount = 1,
                                flingBehavior = PagerDefaults.flingBehavior(
                                    state = pagerState,
                                    pagerSnapDistance = PagerSnapDistance.atMost(1)
                                ),
                                modifier = Modifier.fillMaxSize()
                            ) { page ->
                                val pageOffset = ((pagerState.currentPage - page) +
                                    pagerState.currentPageOffsetFraction).absoluteValue.coerceIn(0f, 1f)

                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .then(
                                            if (uiState.simpleMode) {
                                                Modifier
                                            } else {
                                                Modifier.graphicsLayer {
                                                    val scale = lerp(1f, 0.93f, pageOffset)
                                                    scaleX = scale
                                                    scaleY = scale
                                                    alpha = lerp(1f, 0.65f, pageOffset)
                                                }
                                            }
                                        )
                                ) {
                                    when (page) {
                                        0 -> HomeScreen(
                                            uiState = uiState,
                                            viewModel = viewModel,
                                            onFallbackRequired = { hostname ->
                                                val intent = PrivateDnsController.copyToClipboardAndGetSettingsIntent(
                                                    this@MainActivity,
                                                    hostname
                                                )
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
                                            onNavigateToServers = { slideToPage(1) }
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

                            // Floating Glass Slide Dock pinned cleanly above system navigation bar
                            FloatingPulseNavBar(
                                currentPage = pagerState.currentPage,
                                continuousPagePosition = continuousPagePos,
                                activeAccent = if (isProtected && isAdGuardDoT) EmeraldMint else ElectricCyan,
                                onSelectPage = slideToPage,
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .windowInsetsPadding(WindowInsets.navigationBars)
                                    .padding(horizontal = 20.dp, vertical = 14.dp)
                            )
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

@Composable
private fun FloatingPulseNavBar(
    currentPage: Int,
    continuousPagePosition: Float,
    activeAccent: Color,
    onSelectPage: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val tabs = remember {
        listOf(
            NavTabSpec(0, "Shield", Icons.Filled.Home, Icons.Outlined.Home, "nav_item_home", "Shield"),
            NavTabSpec(1, "Benchmark", Icons.Filled.Dns, Icons.Outlined.Dns, "nav_item_servers", "Benchmark"),
            NavTabSpec(2, "Settings", Icons.Filled.Settings, Icons.Outlined.Settings, "nav_item_settings", "Settings")
        )
    }

    val themeStyle = LocalAppThemeStyle.current
    val simpleMode = LocalSimpleMode.current
    val resolvedAccent = if (!simpleMode &&
        themeStyle == AppThemeStyle.HOLOGRAPHIC_LIQUID_GLASS &&
        activeAccent == ElectricCyan
    ) {
        HoloViolet
    } else {
        activeAccent
    }
    val animatedAccent = if (simpleMode) {
        resolvedAccent
    } else {
        val animated by animateColorAsState(
            targetValue = resolvedAccent,
            animationSpec = tween(500),
            label = "nav_accent"
        )
        animated
    }

    GlassCard(
        modifier = modifier
            .fillMaxWidth()
            .widthIn(max = 460.dp)
            .height(66.dp)
            .testTag("main_bottom_nav"),
        cornerRadius = 28.dp,
        borderColor = animatedAccent.copy(alpha = 0.48f),
        backgroundColor = if (!simpleMode && themeStyle == AppThemeStyle.NEUMORPHISM) {
            NeuBaseDark
        } else {
            DarkSurfaceElevated
        },
        breathingGlow = !simpleMode && (
            themeStyle == AppThemeStyle.HOLOGRAPHIC_LIQUID_GLASS ||
                themeStyle == AppThemeStyle.CHROMATIC_FROSTED_LIQUID
            )
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(5.dp)
        ) {
            val tabWidth = maxWidth / tabs.size
            val clampedPosition = continuousPagePosition.coerceIn(0f, (tabs.size - 1).toFloat())

            // Real-time sliding luminous indicator capsule synced directly to swipe progress
            Box(
                modifier = Modifier
                    .offset(x = tabWidth * clampedPosition)
                    .width(tabWidth)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(23.dp))
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                animatedAccent.copy(alpha = if (simpleMode) 0.16f else 0.24f),
                                animatedAccent.copy(alpha = if (simpleMode) 0.08f else 0.09f)
                            )
                        )
                    )
                    .border(
                        width = 1.dp,
                        color = animatedAccent.copy(alpha = 0.45f),
                        shape = RoundedCornerShape(23.dp)
                    )
            )

            Row(
                modifier = Modifier.fillMaxSize(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                tabs.forEach { tab ->
                    val isSelected = currentPage == tab.index
                    val iconScale = if (simpleMode) {
                        1f
                    } else {
                        val animatedScale by animateFloatAsState(
                            targetValue = if (isSelected) 1.12f else 1f,
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                stiffness = Spring.StiffnessMedium
                            ),
                            label = "tab_icon_scale_${tab.index}"
                        )
                        animatedScale
                    }

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(23.dp))
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = ripple(bounded = true, color = animatedAccent),
                                onClick = { onSelectPage(tab.index) }
                            )
                            .testTag(tab.testTag),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = if (isSelected) tab.selectedIcon else tab.unselectedIcon,
                            contentDescription = tab.contentDesc,
                            tint = if (isSelected) animatedAccent else TextSecondary,
                            modifier = Modifier
                                .size(21.dp)
                                .scale(iconScale)
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = tab.label,
                            color = if (isSelected) TextPrimary else TextSecondary,
                            fontSize = 10.5.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}
