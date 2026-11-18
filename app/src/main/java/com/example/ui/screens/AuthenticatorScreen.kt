package com.example.ui.screens

import android.content.Intent
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentActivity
import com.example.R
import com.example.data.model.AppFilterTab
import com.example.data.model.BiometricHardwareStatus
import com.example.ui.components.AccessibilityGuardBannerCard
import com.example.ui.components.AppLockRowCard
import com.example.ui.components.AppRelockDelayBottomSheet
import com.example.ui.components.AuthenticatorHeroGuardCard
import com.example.ui.components.GlassCard
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.EmeraldMint
import com.example.ui.theme.GlassBorder
import com.example.ui.theme.SubtleSurfaceBorder
import com.example.ui.theme.SubtleSurfaceFill
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.utils.BiometricHelper
import com.example.viewmodel.DnsPulseViewModel
import com.example.viewmodel.HomeUiState

@Composable
fun AuthenticatorScreen(
    uiState: HomeUiState,
    viewModel: DnsPulseViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val activity = context as? FragmentActivity
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var selectedFilter by rememberSaveable { mutableStateOf(AppFilterTab.ALL) }
    var delaySheetTargetPkg by rememberSaveable { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        viewModel.refreshAuthenticatorEnvironment()
        if (uiState.installedApps.isEmpty() && !uiState.isLoadingApps) {
            viewModel.loadInstalledApps()
        }
    }

    val filteredApps = remember(uiState.installedApps, searchQuery, selectedFilter) {
        val q = searchQuery.trim().lowercase()
        uiState.installedApps.filter { app ->
            val matchesFilter = when (selectedFilter) {
                AppFilterTab.ALL -> true
                AppFilterTab.PROTECTED -> app.isLocked
                AppFilterTab.USER_APPS -> !app.isSystemApp
                AppFilterTab.SYSTEM_APPS -> app.isSystemApp
            }
            val matchesQuery = q.isEmpty() ||
                app.appName.lowercase().contains(q) ||
                app.packageName.lowercase().contains(q)
            matchesFilter && matchesQuery
        }
    }

    val testPromptTitle = stringResource(R.string.auth_prompt_title)
    val testPromptSubtitle = stringResource(R.string.auth_prompt_test_subtitle)
    val promptNegative = stringResource(R.string.auth_prompt_negative)
    val successToast = stringResource(R.string.auth_test_success)
    val failedToast = stringResource(R.string.auth_test_failed)

    val canVerifyIdentity = uiState.biometricStatus == BiometricHardwareStatus.READY ||
        (uiState.authConfig.allowDeviceCredentialFallback && BiometricHelper.canUseDeviceCredential(context))

    val launchFingerprintVerification: (subtitle: String, onVerified: () -> Unit) -> Unit = { sub, onVerified ->
        if (activity != null && canVerifyIdentity) {
            BiometricHelper.showFingerprintPrompt(
                activity = activity,
                title = testPromptTitle,
                subtitle = sub,
                negativeButtonText = promptNegative,
                allowDeviceCredential = uiState.authConfig.allowDeviceCredentialFallback,
                onSuccess = onVerified,
                onErrorOrCancelled = { msg ->
                    Toast.makeText(context, msg ?: failedToast, Toast.LENGTH_SHORT).show()
                }
            )
        } else {
            onVerified()
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Transparent),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 720.dp)
                .padding(horizontal = 16.dp)
                .testTag("authenticator_screen"),
            contentPadding = PaddingValues(bottom = 110.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Header
            item {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.auth_title),
                            color = TextPrimary,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Text(
                            text = stringResource(R.string.auth_subtitle),
                            color = TextSecondary,
                            fontSize = 12.sp
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        IconButton(
                            onClick = { viewModel.loadInstalledApps(forceReload = true) },
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(SubtleSurfaceFill)
                                .border(1.dp, SubtleSurfaceBorder, CircleShape)
                                .testTag("auth_refresh_apps_btn")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Refresh,
                                contentDescription = "Refresh Apps",
                                tint = TextSecondary
                            )
                        }

                        IconButton(
                            onClick = {
                                if (uiState.biometricStatus == BiometricHardwareStatus.NO_ENROLLED_BIOMETRICS) {
                                    runCatching {
                                        context.startActivity(BiometricHelper.buildEnrollFingerprintIntent())
                                    }
                                } else {
                                    launchFingerprintVerification(testPromptSubtitle) {
                                        Toast.makeText(context, successToast, Toast.LENGTH_SHORT).show()
                                    }
                                }
                            },
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(EmeraldMint.copy(alpha = 0.16f))
                                .border(1.dp, EmeraldMint.copy(alpha = 0.45f), CircleShape)
                                .testTag("auth_test_fingerprint_btn")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Fingerprint,
                                contentDescription = stringResource(R.string.auth_test_fingerprint_desc),
                                tint = EmeraldMint
                            )
                        }
                    }
                }
            }

            // Hero Guard Controls Card
            item {
                AuthenticatorHeroGuardCard(
                    config = uiState.authConfig,
                    onToggleMasterGuard = { enabled ->
                        if (!enabled &&
                            uiState.isAccessibilityGuardActive &&
                            canVerifyIdentity
                        ) {
                            launchFingerprintVerification(testPromptSubtitle) {
                                viewModel.setMasterAppGuardEnabled(false)
                            }
                        } else {
                            viewModel.setMasterAppGuardEnabled(enabled)
                        }
                    }
                )
            }

            // Accessibility Service Monitor Status Card
            item {
                AccessibilityGuardBannerCard(
                    isServiceActive = uiState.isAccessibilityGuardActive,
                    onOpenAccessibilitySettings = {
                        runCatching {
                            context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                        }.onFailure {
                            runCatching {
                                context.startActivity(Intent(Settings.ACTION_SETTINGS))
                            }
                        }
                    }
                )
            }

            // Search & Filter Controls
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        singleLine = true,
                        placeholder = {
                            Text(
                                text = stringResource(R.string.auth_search_hint),
                                color = TextSecondary,
                                fontSize = 13.sp
                            )
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Filled.Search,
                                contentDescription = null,
                                tint = ElectricCyan
                            )
                        },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(
                                        imageVector = Icons.Filled.Close,
                                        contentDescription = "Clear Search",
                                        tint = TextSecondary
                                    )
                                }
                            }
                        },
                        shape = RoundedCornerShape(16.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = ElectricCyan,
                            unfocusedBorderColor = GlassBorder,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary,
                            cursorColor = ElectricCyan
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("auth_search_input")
                    )

                    val protectedCount = uiState.authConfig.protectedPackages.size
                    val filterOptions = listOf(
                        AppFilterTab.ALL to "${stringResource(R.string.auth_filter_all)} (${uiState.installedApps.size})",
                        AppFilterTab.PROTECTED to "${stringResource(R.string.auth_filter_protected)} ($protectedCount)",
                        AppFilterTab.USER_APPS to stringResource(R.string.auth_filter_user),
                        AppFilterTab.SYSTEM_APPS to stringResource(R.string.auth_filter_system)
                    )

                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(filterOptions, key = { it.first.name }) { (tab, label) ->
                            val isSelected = selectedFilter == tab
                            Box(
                                modifier = Modifier
                                    .height(38.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(
                                        if (isSelected) ElectricCyan.copy(alpha = 0.20f) else SubtleSurfaceFill
                                    )
                                    .border(
                                        width = 1.dp,
                                        color = if (isSelected) ElectricCyan else SubtleSurfaceBorder,
                                        shape = RoundedCornerShape(12.dp)
                                    )
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = ripple(color = ElectricCyan),
                                        onClick = { selectedFilter = tab }
                                    )
                                    .padding(horizontal = 14.dp)
                                    .testTag("auth_filter_${tab.name.lowercase()}"),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = label,
                                    color = if (isSelected) ElectricCyan else TextSecondary,
                                    fontSize = 12.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }
            }

            // Installed Apps List
            if (uiState.isLoadingApps && uiState.installedApps.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 36.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = ElectricCyan)
                    }
                }
            } else if (filteredApps.isEmpty()) {
                item {
                    GlassCard(
                        modifier = Modifier.fillMaxWidth(),
                        cornerRadius = 18.dp,
                        borderColor = GlassBorder
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Fingerprint,
                                contentDescription = null,
                                tint = TextSecondary,
                                modifier = Modifier.size(32.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = stringResource(R.string.auth_empty_apps),
                                color = TextSecondary,
                                fontSize = 13.sp
                            )
                        }
                    }
                }
            } else {
                items(filteredApps, key = { it.packageName }) { app ->
                    val effectiveTimeout =
                        app.customRelockTimeoutSeconds ?: uiState.authConfig.relockTimeoutSeconds
                    AppLockRowCard(
                        item = app,
                        effectiveTimeoutSeconds = effectiveTimeout,
                        onToggle = { enableLock ->
                            if (!enableLock &&
                                uiState.authConfig.isMasterGuardEnabled &&
                                uiState.isAccessibilityGuardActive &&
                                canVerifyIdentity
                            ) {
                                launchFingerprintVerification(
                                    context.getString(R.string.auth_prompt_subtitle, app.appName)
                                ) {
                                    viewModel.toggleAppFingerprintLock(app.packageName, false)
                                }
                            } else {
                                viewModel.toggleAppFingerprintLock(app.packageName, enableLock)
                            }
                        },
                        onLongPress = {
                            delaySheetTargetPkg = app.packageName
                        }
                    )
                }
            }
        }

        val delaySheetTargetApp = remember(delaySheetTargetPkg, uiState.installedApps) {
            delaySheetTargetPkg?.let { targetPkg ->
                uiState.installedApps.firstOrNull { it.packageName == targetPkg }
            }
        }
        if (delaySheetTargetApp != null) {
            val effectiveDelay =
                delaySheetTargetApp.customRelockTimeoutSeconds
                    ?: uiState.authConfig.relockTimeoutSeconds
            AppRelockDelayBottomSheet(
                item = delaySheetTargetApp,
                effectiveTimeoutSeconds = effectiveDelay,
                onToggleLock = { enableLock ->
                    if (!enableLock &&
                        uiState.authConfig.isMasterGuardEnabled &&
                        uiState.isAccessibilityGuardActive &&
                        canVerifyIdentity
                    ) {
                        launchFingerprintVerification(
                            context.getString(
                                R.string.auth_prompt_subtitle,
                                delaySheetTargetApp.appName
                            )
                        ) {
                            viewModel.toggleAppFingerprintLock(delaySheetTargetApp.packageName, false)
                        }
                    } else {
                        viewModel.toggleAppFingerprintLock(delaySheetTargetApp.packageName, enableLock)
                    }
                },
                onSelectDelay = { seconds ->
                    viewModel.setAppRelockTimeoutSeconds(delaySheetTargetApp.packageName, seconds)
                },
                onDismiss = { delaySheetTargetPkg = null }
            )
        }
    }
}
