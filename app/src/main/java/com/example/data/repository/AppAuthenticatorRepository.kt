package com.example.data.repository

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.provider.Settings
import android.text.TextUtils
import com.example.data.model.AppAuthenticatorConfig
import com.example.data.model.InstalledAppItem
import com.example.service.AppLockAccessibilityService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

class AppAuthenticatorRepository private constructor(
    context: Context
) {
    private val appContext = context.applicationContext
    private val prefs: SharedPreferences =
        appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val lock = Any()

    private var isMasterGuardEnabled: Boolean = false
    private var relockTimeoutSeconds: Int = 30
    private var allowDeviceCredentialFallback: Boolean = true
    private val protectedPackages = LinkedHashSet<String>()
    private var totalUnlockCount: Long = 0L
    private var totalInterceptionCount: Long = 0L

    // Tracks temporarily unlocked apps after successful biometric prompt
    private val unlockedUntilMs = ConcurrentHashMap<String, Long>()
    @Volatile
    private var activeForegroundUnlockedPkg: String? = null
    @Volatile
    private var activeLockPromptPkg: String? = null
    @Volatile
    private var activeLockPromptAtMs: Long = 0L
    @Volatile
    private var lastUnlockedPkg: String? = null
    @Volatile
    private var lastUnlockedAtMs: Long = 0L

    private val _configFlow = MutableStateFlow(AppAuthenticatorConfig())
    val configFlow: StateFlow<AppAuthenticatorConfig> = _configFlow.asStateFlow()

    init {
        loadFromPrefs()
    }

    fun setMasterGuardEnabled(enabled: Boolean) {
        val snapshot: AppAuthenticatorConfig
        synchronized(lock) {
            isMasterGuardEnabled = enabled
            if (!enabled) {
                unlockedUntilMs.clear()
                activeForegroundUnlockedPkg = null
                activeLockPromptPkg = null
                lastUnlockedPkg = null
            }
            prefs.edit().putBoolean(KEY_MASTER_GUARD, enabled).apply()
            snapshot = buildConfigLocked()
        }
        _configFlow.value = snapshot
    }

    fun toggleAppLock(packageName: String, enabled: Boolean) {
        val snapshot: AppAuthenticatorConfig
        synchronized(lock) {
            if (enabled) {
                protectedPackages.add(packageName)
            } else {
                protectedPackages.remove(packageName)
                unlockedUntilMs.remove(packageName)
                if (activeForegroundUnlockedPkg == packageName) {
                    activeForegroundUnlockedPkg = null
                }
                if (activeLockPromptPkg == packageName) {
                    activeLockPromptPkg = null
                }
                if (lastUnlockedPkg == packageName) {
                    lastUnlockedPkg = null
                }
            }
            prefs.edit().putStringSet(KEY_PROTECTED_PACKAGES, protectedPackages.toSet()).apply()
            snapshot = buildConfigLocked()
        }
        _configFlow.value = snapshot
    }

    fun setRelockTimeoutSeconds(seconds: Int) {
        val snapshot: AppAuthenticatorConfig
        synchronized(lock) {
            relockTimeoutSeconds = seconds.coerceAtLeast(0)
            prefs.edit().putInt(KEY_RELOCK_TIMEOUT_SEC, relockTimeoutSeconds).apply()
            snapshot = buildConfigLocked()
        }
        _configFlow.value = snapshot
    }

    fun setAllowDeviceCredentialFallback(allowed: Boolean) {
        val snapshot: AppAuthenticatorConfig
        synchronized(lock) {
            allowDeviceCredentialFallback = allowed
            prefs.edit().putBoolean(KEY_ALLOW_CREDENTIAL_FALLBACK, allowed).apply()
            snapshot = buildConfigLocked()
        }
        _configFlow.value = snapshot
    }

    fun isPackageProtected(packageName: String): Boolean {
        synchronized(lock) {
            return isMasterGuardEnabled && packageName in protectedPackages
        }
    }

    fun setLockPromptActive(packageName: String, nowMs: Long = System.currentTimeMillis()) {
        synchronized(lock) {
            activeLockPromptPkg = packageName
            activeLockPromptAtMs = nowMs
        }
    }

    fun clearLockPromptActive(packageName: String? = null) {
        synchronized(lock) {
            if (packageName == null || activeLockPromptPkg == packageName) {
                activeLockPromptPkg = null
                activeLockPromptAtMs = 0L
            }
        }
    }

    fun shouldPromptLock(packageName: String, nowMs: Long = System.currentTimeMillis()): Boolean {
        synchronized(lock) {
            if (!isMasterGuardEnabled) return false
            if (packageName !in protectedPackages) {
                return false
            }

            // Do not stack or re-trigger lock prompts while this app's prompt screen is already active
            if (activeLockPromptPkg == packageName &&
                (nowMs - activeLockPromptAtMs) in 0L..LOCK_PROMPT_STALE_MS
            ) {
                return false
            }

            if (activeForegroundUnlockedPkg == packageName) {
                return false
            }

            // Guard against instant-relock re-triggering during AppLockActivity/BiometricPrompt close transition
            if (relockTimeoutSeconds == 0 &&
                lastUnlockedPkg == packageName &&
                (nowMs - lastUnlockedAtMs) in 0L..POST_UNLOCK_SETTLE_MS
            ) {
                activeForegroundUnlockedPkg = packageName
                return false
            }

            val expiry = unlockedUntilMs[packageName] ?: 0L
            if (relockTimeoutSeconds > 0 && nowMs < expiry) {
                activeForegroundUnlockedPkg = packageName
                return false
            }
            return true
        }
    }

    fun onNonProtectedAppForegrounded(
        packageName: String,
        nowMs: Long = System.currentTimeMillis(),
        forceOwnApp: Boolean = false
    ) {
        synchronized(lock) {
            if (!forceOwnApp && packageName == appContext.packageName) return
            if (packageName in protectedPackages) return

            // Ignore window state churn right after a fingerprint unlock while AppLockActivity finishes back to the app
            val activePkg = activeForegroundUnlockedPkg
            if (activePkg != null &&
                relockTimeoutSeconds == 0 &&
                lastUnlockedPkg == activePkg &&
                (nowMs - lastUnlockedAtMs) in 0L until POST_UNLOCK_SETTLE_MS
            ) {
                return
            }

            if (activePkg != null) {
                if (relockTimeoutSeconds == 0) {
                    unlockedUntilMs.remove(activePkg)
                }
                activeForegroundUnlockedPkg = null
            }
            if (relockTimeoutSeconds == 0 &&
                lastUnlockedPkg != null &&
                (nowMs - lastUnlockedAtMs) >= POST_UNLOCK_SETTLE_MS
            ) {
                lastUnlockedPkg = null
            }
            activeLockPromptPkg = null
        }
    }

    fun markAppUnlocked(packageName: String, nowMs: Long = System.currentTimeMillis()) {
        val snapshot: AppAuthenticatorConfig
        synchronized(lock) {
            activeForegroundUnlockedPkg = packageName
            lastUnlockedPkg = packageName
            lastUnlockedAtMs = nowMs
            if (activeLockPromptPkg == packageName) {
                activeLockPromptPkg = null
                activeLockPromptAtMs = 0L
            }
            if (relockTimeoutSeconds > 0) {
                unlockedUntilMs[packageName] = nowMs + relockTimeoutSeconds * 1000L
            } else {
                unlockedUntilMs.remove(packageName)
            }
            totalUnlockCount++
            prefs.edit().putLong(KEY_TOTAL_UNLOCKS, totalUnlockCount).apply()
            snapshot = buildConfigLocked()
        }
        _configFlow.value = snapshot
    }

    fun recordInterception() {
        val snapshot: AppAuthenticatorConfig
        synchronized(lock) {
            totalInterceptionCount++
            prefs.edit().putLong(KEY_TOTAL_INTERCEPTIONS, totalInterceptionCount).apply()
            snapshot = buildConfigLocked()
        }
        _configFlow.value = snapshot
    }

    fun clearUnlockSessions() {
        synchronized(lock) {
            unlockedUntilMs.clear()
            activeForegroundUnlockedPkg = null
            activeLockPromptPkg = null
            activeLockPromptAtMs = 0L
            lastUnlockedPkg = null
            lastUnlockedAtMs = 0L
        }
    }

    suspend fun loadLaunchableApps(context: Context): List<InstalledAppItem> = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        val launcherIntent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        val resolved = pm.queryIntentActivities(launcherIntent, PackageManager.MATCH_ALL)
        val currentLocked = synchronized(lock) { protectedPackages.toSet() }
        val seenPackages = HashSet<String>()
        val items = ArrayList<InstalledAppItem>(resolved.size)

        for (info in resolved) {
            val pkg = info.activityInfo?.packageName ?: continue
            if (pkg == context.packageName || !seenPackages.add(pkg)) continue

            val appInfo = info.activityInfo?.applicationInfo
            val isSystem = appInfo != null &&
                ((appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0 ||
                    (appInfo.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0)
            val label = runCatching { info.loadLabel(pm)?.toString() }.getOrNull()
                ?.takeIf { it.isNotBlank() } ?: pkg

            items.add(
                InstalledAppItem(
                    packageName = pkg,
                    appName = label,
                    isSystemApp = isSystem,
                    isLocked = pkg in currentLocked
                )
            )
        }

        items.sortWith(
            compareByDescending<InstalledAppItem> { it.isLocked }
                .thenBy { it.isSystemApp }
                .thenBy { it.appName.lowercase() }
        )
        items
    }

    private fun loadFromPrefs() {
        synchronized(lock) {
            isMasterGuardEnabled = prefs.getBoolean(KEY_MASTER_GUARD, false)
            relockTimeoutSeconds = prefs.getInt(KEY_RELOCK_TIMEOUT_SEC, 30)
            allowDeviceCredentialFallback = prefs.getBoolean(KEY_ALLOW_CREDENTIAL_FALLBACK, true)
            totalUnlockCount = prefs.getLong(KEY_TOTAL_UNLOCKS, 0L)
            totalInterceptionCount = prefs.getLong(KEY_TOTAL_INTERCEPTIONS, 0L)
            protectedPackages.clear()
            prefs.getStringSet(KEY_PROTECTED_PACKAGES, emptySet())?.let {
                protectedPackages.addAll(it)
            }
            _configFlow.value = buildConfigLocked()
        }
    }

    private fun buildConfigLocked(): AppAuthenticatorConfig {
        return AppAuthenticatorConfig(
            isMasterGuardEnabled = isMasterGuardEnabled,
            relockTimeoutSeconds = relockTimeoutSeconds,
            allowDeviceCredentialFallback = allowDeviceCredentialFallback,
            protectedPackages = protectedPackages.toSet(),
            totalUnlockCount = totalUnlockCount,
            totalInterceptionCount = totalInterceptionCount
        )
    }

    companion object {
        private const val PREFS_NAME = "dns_pulse_app_auth"
        private const val KEY_MASTER_GUARD = "key_master_guard"
        private const val KEY_PROTECTED_PACKAGES = "key_protected_packages"
        private const val KEY_RELOCK_TIMEOUT_SEC = "key_relock_timeout_sec"
        private const val KEY_ALLOW_CREDENTIAL_FALLBACK = "key_allow_credential_fallback"
        private const val KEY_TOTAL_UNLOCKS = "key_total_unlocks"
        private const val KEY_TOTAL_INTERCEPTIONS = "key_total_interceptions"
        internal const val POST_UNLOCK_SETTLE_MS = 1_500L
        internal const val LOCK_PROMPT_STALE_MS = 20_000L

        @Volatile
        private var instance: AppAuthenticatorRepository? = null

        fun getInstance(context: Context): AppAuthenticatorRepository {
            return instance ?: synchronized(this) {
                instance ?: AppAuthenticatorRepository(context.applicationContext).also { instance = it }
            }
        }

        internal fun createIsolatedForTesting(context: Context): AppAuthenticatorRepository {
            return AppAuthenticatorRepository(context.applicationContext)
        }

        fun isAccessibilityServiceEnabled(context: Context): Boolean {
            val expectedComponent = ComponentName(context, AppLockAccessibilityService::class.java)
            val enabledServices = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: return false

            val splitter = TextUtils.SimpleStringSplitter(':')
            splitter.setString(enabledServices)
            while (splitter.hasNext()) {
                val componentNameString = splitter.next()
                val enabledComponent = ComponentName.unflattenFromString(componentNameString)
                if (enabledComponent == expectedComponent) {
                    return true
                }
            }
            return false
        }
    }
}
