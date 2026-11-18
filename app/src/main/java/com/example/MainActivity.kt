package com.example

import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.WindowManager
import android.widget.Toast
import androidx.fragment.app.FragmentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBarsIgnoringVisibility
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsIgnoringVisibility
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Analytics
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.DnsEngineMode
import com.example.ui.components.BreathingAtmosphere
import com.example.ui.components.GlassCard
import com.example.ui.components.UpdateDialog
import com.example.ui.screens.AnalyticsScreen
import com.example.ui.screens.AuthenticatorScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.theme.AppThemeStyle
import com.example.ui.theme.DarkSurfaceElevated
import com.example.ui.theme.DeepVoidBlack
import com.example.ui.theme.DnsPulseTheme
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.EmeraldMint
import com.example.ui.theme.HoloViolet
import com.example.ui.theme.LocalAppThemeStyle
import com.example.ui.theme.LocalBottomDockClearance
import com.example.ui.theme.LocalIsDarkTheme
import com.example.ui.theme.LocalPageActive
import com.example.ui.theme.LocalSimpleMode
import com.example.ui.theme.NeuBaseDark
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.utils.PrivateDnsController
import com.example.utils.PrivateDnsMode
import com.example.utils.UpdateDownloadState
import com.example.utils.UpdateManager
import com.example.viewmodel.DnsPulseViewModel
import java.io.File

private data class NavTabSpec(
    val index: Int,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String,
    val contentDesc: String
)

class MainActivity : FragmentActivity() {

    companion object {
        private const val VPN_REQUEST_CODE = 100
        private const val INSTALL_APPS_REQUEST_CODE = 101
    }

    private val viewModel: DnsPulseViewModel by viewModels()
    private var isFullScreenEnabled: Boolean = false
    private var pendingApkInstall: File? = null

    fun startVpnWithPermissionCheck(prepareIntent: Intent? = null) {
        val intent = prepareIntent ?: try {
            VpnService.prepare(this)
        } catch (e: Exception) {
            Toast.makeText(
                this,
                "VPN permission unavailable: ${e.localizedMessage}",
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        if (intent != null) {
            try {
                @Suppress("DEPRECATION")
                startActivityForResult(intent, VPN_REQUEST_CODE)
            } catch (e: Exception) {
                Toast.makeText(
                    this,
                    "VPN permission dialog unavailable: ${e.localizedMessage}",
                    Toast.LENGTH_SHORT
                ).show()
            }
        } else {
            viewModel.startVpn(this)
        }
    }

    @Suppress("DEPRECATION")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        when (requestCode) {
            VPN_REQUEST_CODE -> {
                if (resultCode == RESULT_OK) {
                    viewModel.startVpn(this)
                }
            }
            INSTALL_APPS_REQUEST_CODE -> {
                val file = pendingApkInstall
                if (file != null && UpdateManager.canInstallPackages(this)) {
                    UpdateManager.triggerApkInstall(this, file)
                    pendingApkInstall = null
                }
            }
        }
    }

    private fun applyFullScreenWindowState(enabled: Boolean) {
        isFullScreenEnabled = enabled
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.attributes = window.attributes.apply {
                layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            }
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.attributes = window.attributes.apply {
                layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        if (enabled) {
            controller.hide(WindowInsetsCompat.Type.systemBars())
        } else {
            controller.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    private fun configureHighRefreshRate() {
        try {
            val activeDisplay = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                display
            } else {
                @Suppress("DEPRECATION")
                windowManager.defaultDisplay
            } ?: return

            val currentMode = activeDisplay.mode
            val highRefreshModes = activeDisplay.supportedModes
                .filter { mode ->
                    mode.physicalWidth == currentMode.physicalWidth &&
                        mode.physicalHeight == currentMode.physicalHeight &&
                        mode.refreshRate >= 59.5f
                }

            val targetMode = highRefreshModes.maxByOrNull { it.refreshRate }

            window.attributes = window.attributes.apply {
                if (targetMode != null) {
                    preferredDisplayModeId = targetMode.modeId
                    preferredRefreshRate = targetMode.refreshRate
                } else {
                    preferredRefreshRate = 120f
                }
            }
        } catch (_: Exception) {
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            configureHighRefreshRate()
            if (isFullScreenEnabled) {
                applyFullScreenWindowState(true)
            }
        }
    }

    @OptIn(ExperimentalLayoutApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        configureHighRefreshRate()

        setContent {
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()

            LaunchedEffect(uiState.fullScreenMode) {
                applyFullScreenWindowState(uiState.fullScreenMode)
            }

            DnsPulseTheme(
                themeStyle = uiState.themeStyle,
                appearanceMode = uiState.appearanceMode,
                simpleMode = uiState.simpleMode
            ) {
                val isDarkTheme = LocalIsDarkTheme.current
                LaunchedEffect(isDarkTheme) {
                    val controller = WindowCompat.getInsetsController(window, window.decorView)
                    controller.isAppearanceLightStatusBars = !isDarkTheme
                    controller.isAppearanceLightNavigationBars = !isDarkTheme
                }
                val snackbarHostState = remember { SnackbarHostState() }
                var selectedTab by rememberSaveable { mutableIntStateOf(0) }
                val saveableStateHolder = rememberSaveableStateHolder()
                val currentSimpleMode by rememberUpdatedState(uiState.simpleMode)

                val smoothNavEasing = remember { CubicBezierEasing(0.16f, 1.0f, 0.30f, 1.0f) }
                val smoothPagePosition by animateFloatAsState(
                    targetValue = selectedTab.toFloat(),
                    animationSpec = tween(
                        durationMillis = if (currentSimpleMode) 300 else 580,
                        easing = if (currentSimpleMode) FastOutSlowInEasing else smoothNavEasing
                    ),
                    label = "smooth_page_position"
                )

                val navigateToTab: (Int) -> Unit = remember {
                    { targetPage ->
                        if (targetPage != selectedTab) {
                            selectedTab = targetPage
                        }
                    }
                }

                // Pressing back from Analytics, Authenticator, or Settings smoothly returns to Shield
                BackHandler(enabled = selectedTab != 0) {
                    navigateToTab(0)
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
                                try {
                                    @Suppress("DEPRECATION")
                                    startActivityForResult(
                                        UpdateManager.buildManageUnknownSourcesIntent(this@MainActivity),
                                        INSTALL_APPS_REQUEST_CODE
                                    )
                                } catch (e: Exception) {
                                    Toast.makeText(
                                        this@MainActivity,
                                        "Unable to open install settings: ${e.localizedMessage}",
                                        Toast.LENGTH_LONG
                                    ).show()
                                }
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
                val onFallbackRequiredStable = remember {
                    { hostname: String ->
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
                        Unit
                    }
                }
                val onLaunchWirelessSettingsStable = remember {
                    {
                        try {
                            startActivity(Intent(Settings.ACTION_WIRELESS_SETTINGS))
                        } catch (_: Exception) {
                            startActivity(Intent(Settings.ACTION_SETTINGS))
                        }
                        Unit
                    }
                }
                val onNavigateToShieldStable = remember(navigateToTab) {
                    { navigateToTab(0) }
                }

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
                            pageOffsetFractionProvider = { smoothPagePosition }
                        )
                    }

                    // Keep UI titles, containers, and controls fixed in place even when system bars hide in Full Screen
                    val stableSafeInsets = WindowInsets.systemBarsIgnoringVisibility
                        .union(WindowInsets.displayCutout)
                        .union(WindowInsets.ime)
                    val stableNavInsets = WindowInsets.navigationBarsIgnoringVisibility

                    Scaffold(
                        modifier = Modifier.fillMaxSize(),
                        containerColor = Color.Transparent,
                        snackbarHost = {
                            SnackbarHost(
                                hostState = snackbarHostState,
                                modifier = Modifier
                                    .windowInsetsPadding(stableNavInsets)
                                    .padding(start = 12.dp, end = 12.dp, bottom = 98.dp)
                            )
                        },
                        contentWindowInsets = stableSafeInsets
                    ) { innerPadding ->
                        val originalLayoutDirection = LocalLayoutDirection.current
                        val bottomDockClearance = innerPadding.calculateBottomPadding() + 106.dp

                        CompositionLocalProvider(
                            LocalLayoutDirection provides LayoutDirection.Ltr,
                            LocalBottomDockClearance provides bottomDockClearance
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(
                                        top = innerPadding.calculateTopPadding(),
                                        start = innerPadding.calculateStartPadding(LayoutDirection.Ltr),
                                        end = innerPadding.calculateEndPadding(LayoutDirection.Ltr)
                                    )
                            ) {
                                // Overlap-free, GPU-composited directional Fade-Through transition
                                // Direct target transition skips intermediate tabs (no cross-tab flashing or gesture conflicts)
                                AnimatedContent(
                                    targetState = selectedTab,
                                    transitionSpec = {
                                        val direction = if (targetState > initialState) 1 else -1
                                        val spec = if (currentSimpleMode) {
                                            (fadeIn(
                                                animationSpec = tween(
                                                    durationMillis = 300,
                                                    delayMillis = 110,
                                                    easing = LinearOutSlowInEasing
                                                )
                                            )) togetherWith fadeOut(
                                                animationSpec = tween(
                                                    durationMillis = 140,
                                                    easing = FastOutLinearInEasing
                                                )
                                            )
                                        } else {
                                            val enter = fadeIn(
                                                animationSpec = tween(
                                                    durationMillis = 440,
                                                    delayMillis = 160,
                                                    easing = LinearOutSlowInEasing
                                                )
                                            ) + slideInHorizontally(
                                                animationSpec = tween(
                                                    durationMillis = 560,
                                                    delayMillis = 110,
                                                    easing = smoothNavEasing
                                                )
                                            ) { fullWidth -> direction * (fullWidth / 8) }

                                            val exit = fadeOut(
                                                animationSpec = tween(
                                                    durationMillis = 180,
                                                    easing = FastOutLinearInEasing
                                                )
                                            ) + slideOutHorizontally(
                                                animationSpec = tween(
                                                    durationMillis = 260,
                                                    easing = FastOutLinearInEasing
                                                )
                                            ) { fullWidth -> -direction * (fullWidth / 12) }

                                            enter togetherWith exit
                                        }
                                        spec using SizeTransform(clip = true)
                                    },
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clipToBounds(),
                                    label = "main_tab_navigation"
                                ) { page ->
                                    val isCurrentTarget = selectedTab == page
                                    CompositionLocalProvider(
                                        LocalLayoutDirection provides originalLayoutDirection,
                                        LocalPageActive provides isCurrentTarget
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .clipToBounds()
                                                .graphicsLayer {
                                                    compositingStrategy = CompositingStrategy.Offscreen
                                                }
                                        ) {
                                            saveableStateHolder.SaveableStateProvider(key = page) {
                                                when (page) {
                                                    0 -> HomeScreen(
                                                        uiState = uiState,
                                                        viewModel = viewModel,
                                                        onFallbackRequired = onFallbackRequiredStable,
                                                        onLaunchWirelessSettings = onLaunchWirelessSettingsStable,
                                                        onRequestVpnStart = ::startVpnWithPermissionCheck
                                                    )
                                                    1 -> AnalyticsScreen(
                                                        uiState = uiState,
                                                        viewModel = viewModel,
                                                        onNavigateToShield = onNavigateToShieldStable
                                                    )
                                                    2 -> AuthenticatorScreen(
                                                        uiState = uiState,
                                                        viewModel = viewModel
                                                    )
                                                    3 -> SettingsScreen(
                                                        uiState = uiState,
                                                        viewModel = viewModel,
                                                        onLaunchWirelessSettings = onLaunchWirelessSettingsStable
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }

                                // Floating Glass Slide Dock pinned cleanly above system navigation bar (LTR fixed left-to-right)
                                FloatingPulseNavBar(
                                    selectedTabProvider = { selectedTab },
                                    continuousPagePositionProvider = { smoothPagePosition },
                                    activeAccent = if (isProtected && isAdGuardDoT) EmeraldMint else ElectricCyan,
                                    onSelectPage = navigateToTab,
                                    modifier = Modifier
                                        .align(Alignment.BottomCenter)
                                        .windowInsetsPadding(stableNavInsets)
                                        .padding(horizontal = 10.dp, vertical = 14.dp)
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
    }

    override fun onStart() {
        super.onStart()
        viewModel.checkForUpdatesOnAppEnter()
    }

    override fun onResume() {
        super.onResume()
        configureHighRefreshRate()
        viewModel.refreshSystemPrivateDnsState()
        viewModel.refreshAuthenticatorEnvironment()
    }
}

@Composable
private fun FloatingPulseNavBar(
    selectedTabProvider: () -> Int,
    continuousPagePositionProvider: () -> Float,
    activeAccent: Color,
    onSelectPage: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val tabs = remember {
        listOf(
            NavTabSpec(0, "Shield", Icons.Filled.Home, Icons.Outlined.Home, "nav_item_home", "Shield"),
            NavTabSpec(1, "Analytics", Icons.Filled.Analytics, Icons.Outlined.Analytics, "nav_item_analytics", "Analytics"),
            NavTabSpec(2, "Authenticator", Icons.Filled.Fingerprint, Icons.Outlined.Fingerprint, "nav_item_authenticator", "Authenticator"),
            NavTabSpec(3, "Settings", Icons.Filled.Settings, Icons.Outlined.Settings, "nav_item_settings", "Settings")
        )
    }

    val currentPage by remember { derivedStateOf(selectedTabProvider) }
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
            animationSpec = tween(400),
            label = "nav_accent"
        )
        animated
    }

    GlassCard(
        modifier = modifier
            .fillMaxWidth()
            .widthIn(max = 540.dp)
            .height(66.dp)
            .testTag("main_bottom_nav"),
        cornerRadius = 28.dp,
        borderColor = animatedAccent.copy(alpha = 0.48f),
        backgroundColor = if (!simpleMode && themeStyle == AppThemeStyle.NEUMORPHISM) {
            NeuBaseDark
        } else {
            DarkSurfaceElevated
        },
        breathingGlow = false
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(5.dp)
                .clip(RoundedCornerShape(23.dp))
        ) {
            val tabWidth = maxWidth / tabs.size
            val slotWidthPx = constraints.maxWidth.toFloat() / tabs.size
            val maxIndex = (tabs.size - 1).toFloat()

            // Real-time GPU-translated sliding indicator capsule (0 layout passes, 0 recompositions)
            Box(
                modifier = Modifier
                    .width(tabWidth)
                    .fillMaxHeight()
                    .graphicsLayer {
                        translationX = slotWidthPx * continuousPagePositionProvider().coerceIn(0f, maxIndex)
                    }
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
                    val iconScaleState = if (simpleMode) {
                        remember { androidx.compose.runtime.mutableFloatStateOf(1f) }
                    } else {
                        animateFloatAsState(
                            targetValue = if (isSelected) 1.12f else 1f,
                            animationSpec = tween(
                                durationMillis = 480,
                                easing = CubicBezierEasing(0.16f, 1.0f, 0.30f, 1.0f)
                            ),
                            label = "tab_icon_scale_${tab.index}"
                        )
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
                                .graphicsLayer {
                                    val s = iconScaleState.value
                                    scaleX = s
                                    scaleY = s
                                }
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = tab.label,
                            color = if (isSelected) TextPrimary else TextSecondary,
                            fontSize = 9.sp,
                            letterSpacing = (-0.2).sp,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}
