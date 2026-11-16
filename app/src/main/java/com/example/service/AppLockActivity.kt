package com.example.service

import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import androidx.fragment.app.FragmentActivity
import com.example.R
import com.example.data.preferences.PreferencesManager
import com.example.data.repository.AppAuthenticatorRepository
import com.example.ui.components.BreathingAtmosphere
import com.example.ui.components.GlassCard
import com.example.ui.theme.AppAppearanceMode
import com.example.ui.theme.AppThemeStyle
import com.example.ui.theme.CrimsonPulse
import com.example.ui.theme.DeepVoidBlack
import com.example.ui.theme.DnsPulseTheme
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.EmeraldMint
import com.example.ui.theme.LocalSimpleMode
import com.example.ui.theme.OnAccentInk
import com.example.ui.theme.SubtleSurfaceBorder
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.utils.BiometricHelper

class AppLockActivity : FragmentActivity() {

    private val authRepo: AppAuthenticatorRepository by lazy {
        AppAuthenticatorRepository.getInstance(applicationContext)
    }
    private val prefs: PreferencesManager by lazy {
        PreferencesManager(applicationContext)
    }

    private var targetPackageState by mutableStateOf("")
    private var targetAppNameState by mutableStateOf("")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        readExtras(intent)

        setContent {
            val themeStyle by prefs.themeStyleFlow.collectAsState(initial = AppThemeStyle.CHROMATIC_FROSTED_LIQUID)
            val appearanceMode by prefs.appearanceModeFlow.collectAsState(initial = AppAppearanceMode.DARK)
            val simpleMode by prefs.simpleModeFlow.collectAsState(initial = false)
            val config by authRepo.configFlow.collectAsState()

            val currentPkg = targetPackageState
            val currentAppName = targetAppNameState
            val appIconBitmap: Bitmap? = remember(currentPkg) {
                runCatching {
                    packageManager.getApplicationIcon(currentPkg).toBitmap(width = 160, height = 160)
                }.getOrNull()
            }
            var errorText by remember(currentPkg) { mutableStateOf<String?>(null) }

            val triggerPrompt: () -> Unit = {
                errorText = null
                BiometricHelper.showFingerprintPrompt(
                    activity = this@AppLockActivity,
                    title = getString(R.string.auth_prompt_title),
                    subtitle = getString(R.string.auth_prompt_subtitle, currentAppName),
                    negativeButtonText = getString(R.string.auth_prompt_negative),
                    allowDeviceCredential = config.allowDeviceCredentialFallback,
                    onSuccess = {
                        authRepo.markAppUnlocked(currentPkg)
                        finish()
                    },
                    onErrorOrCancelled = { msg ->
                        errorText = msg
                    }
                )
            }

            LaunchedEffect(currentPkg) {
                if (currentPkg.isNotBlank()) {
                    triggerPrompt()
                }
            }

            BackHandler {
                exitToHomeLauncher()
            }

            DnsPulseTheme(
                themeStyle = themeStyle,
                appearanceMode = appearanceMode,
                simpleMode = simpleMode
            ) {
                AppLockGateScreen(
                    appName = currentAppName,
                    packageName = currentPkg,
                    appIcon = appIconBitmap,
                    errorText = errorText,
                    onUnlockClick = triggerPrompt,
                    onReturnHomeClick = { exitToHomeLauncher() }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        readExtras(intent)
    }

    private fun readExtras(intent: Intent?) {
        val pkg = intent?.getStringExtra(EXTRA_TARGET_PACKAGE).orEmpty()
        targetPackageState = pkg
        targetAppNameState = intent?.getStringExtra(EXTRA_TARGET_APP_NAME).orEmpty().ifBlank { pkg }
    }

    private fun exitToHomeLauncher() {
        val homeIntent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        startActivity(homeIntent)
        finish()
    }

    companion object {
        const val EXTRA_TARGET_PACKAGE = "extra_target_package"
        const val EXTRA_TARGET_APP_NAME = "extra_target_app_name"
    }
}

@Composable
private fun AppLockGateScreen(
    appName: String,
    packageName: String,
    appIcon: Bitmap?,
    errorText: String?,
    onUnlockClick: () -> Unit,
    onReturnHomeClick: () -> Unit
) {
    val simpleMode = LocalSimpleMode.current
    val pulseScale = if (simpleMode) {
        1f
    } else {
        val anim by rememberInfiniteTransition(label = "lock_gate_pulse").animateFloat(
            initialValue = 0.96f,
            targetValue = 1.05f,
            animationSpec = infiniteRepeatable(
                animation = tween(1300, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "lock_pulse_scale"
        )
        anim
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DeepVoidBlack)
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        if (!simpleMode) {
            BreathingAtmosphere(
                isProtected = true,
                isAdGuardShield = true,
                ambientGlowEnabled = true,
                pageOffsetFraction = 0f
            )
        }

        GlassCard(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 420.dp),
            cornerRadius = 28.dp,
            borderColor = EmeraldMint.copy(alpha = 0.50f),
            breathingGlow = !simpleMode
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .scale(pulseScale)
                        .clip(RoundedCornerShape(20.dp))
                        .background(EmeraldMint.copy(alpha = 0.14f))
                        .border(1.5.dp, EmeraldMint.copy(alpha = 0.55f), RoundedCornerShape(20.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    if (appIcon != null) {
                        Image(
                            bitmap = appIcon.asImageBitmap(),
                            contentDescription = appName,
                            modifier = Modifier
                                .size(50.dp)
                                .clip(RoundedCornerShape(14.dp))
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Filled.Lock,
                            contentDescription = appName,
                            tint = EmeraldMint,
                            modifier = Modifier.size(34.dp)
                        )
                    }
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = appName,
                        color = TextPrimary,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.ExtraBold,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = packageName,
                        color = TextSecondary,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        textAlign = TextAlign.Center
                    )
                }

                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.radialGradient(
                                listOf(
                                    ElectricCyan.copy(alpha = 0.24f),
                                    ElectricCyan.copy(alpha = 0.05f)
                                )
                            )
                        )
                        .border(1.dp, ElectricCyan.copy(alpha = 0.45f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.Fingerprint,
                        contentDescription = stringResource(R.string.auth_prompt_title),
                        tint = ElectricCyan,
                        modifier = Modifier.size(36.dp)
                    )
                }

                Text(
                    text = stringResource(R.string.auth_prompt_subtitle, appName),
                    color = TextSecondary,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center
                )

                if (!errorText.isNullOrBlank()) {
                    Text(
                        text = errorText,
                        color = CrimsonPulse,
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center
                    )
                }

                Button(
                    onClick = onUnlockClick,
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = EmeraldMint,
                        contentColor = OnAccentInk
                    ),
                    contentPadding = PaddingValues(horizontal = 20.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("lock_gate_unlock_button")
                ) {
                    Icon(
                        imageVector = Icons.Filled.Fingerprint,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.auth_lock_screen_unlock_btn),
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }

                OutlinedButton(
                    onClick = onReturnHomeClick,
                    shape = RoundedCornerShape(14.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, SubtleSurfaceBorder),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("lock_gate_home_button")
                ) {
                    Icon(
                        imageVector = Icons.Filled.Home,
                        contentDescription = null,
                        tint = TextSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.auth_lock_screen_cancel_btn),
                        color = TextSecondary,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp
                    )
                }
            }
        }
    }
}
