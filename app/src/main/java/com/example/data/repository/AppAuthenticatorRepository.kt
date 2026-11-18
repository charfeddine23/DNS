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
    private val appRelockTimeouts = ConcurrentHashMap<String, Int>()
    private var totalUnlockCount: Long = 0L
    private var totalInterceptionCount: Long = 0L

    // Tracks temporarily unlocked apps after successful biometric prompt (for timed delays > 0)
    private val unlockedUntilMs = ConcurrentHashMap<String, Long>()
    // Tracks Instant (0s) unlocked apps: Long.MAX_VALUE while active/foreground, or epoch deadline (now + 60_000ms) while idle in background
    private val instantIdleDeadlines = ConcurrentHashMap<String, Long>()
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
                instantIdleDeadlines.clear()
                activeForegroundUnlockedPkg = null
                activeLockPromptPkg = null
                activeLockPromptAtMs = 0L
                lastUnlockedPkg = null
                lastUnlockedAtMs = 0L
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
                instantIdleDeadlines.remove(packageName)
                if (activeForegroundUnlockedPkg == packageName) {
                    activeForegroundUnlockedPkg = null
                }
                if (activeLockPromptPkg == packageName) {
                    activeLockPromptPkg = null
                    activeLockPromptAtMs = 0L
                }
                if (lastUnlockedPkg == packageName) {
                    lastUnlockedPkg = null
                    lastUnlockedAtMs = 0L
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

    fun setAppRelockTimeoutSeconds(packageName: String, seconds: Int) {
        val sanitized = seconds.coerceAtLeast(0)
        val snapshot: AppAuthenticatorConfig
        synchronized(lock) {
            appRelockTimeouts[packageName] = sanitized
            if (sanitized == 0) {
                unlockedUntilMs.remove(packageName)
                instantIdleDeadlines.remove(packageName)
            } else {
                instantIdleDeadlines.remove(packageName)
            }
            persistAppTimeoutsLocked()
            snapshot = buildConfigLocked()
        }
        _configFlow.value = snapshot
    }

    fun clearAppRelockTimeout(packageName: String) {
        val snapshot: AppAuthenticatorConfig
        synchronized(lock) {
            appRelockTimeouts.remove(packageName)
            persistAppTimeoutsLocked()
            snapshot = buildConfigLocked()
        }
        _configFlow.value = snapshot
    }

    fun getEffectiveRelockTimeoutSeconds(packageName: String): Int {
        return appRelockTimeouts[packageName] ?: relockTimeoutSeconds
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

    fun getInstantUnlockedPackages(): Set<String> {
        synchronized(lock) {
            return instantIdleDeadlines.keys.filter { pkg ->
                pkg in protectedPackages && getEffectiveRelockTimeoutSeconds(pkg) == 0
            }.toSet()
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

    fun shouldPromptLock(
        packageName: String,
        nowMs: Long = System.currentTimeMillis(),
        isLockOverlayVisible: Boolean = true
    ): Boolean {
        synchronized(lock) {
            if (!isMasterGuardEnabled) return false
            if (packageName !in protectedPackages) {
                return false
            }

            // Do not stack or re-trigger lock prompts while this app's prompt screen is genuinely active.
            // Self-heal stale state if the lock overlay is no longer visible after initial launch window.
            val promptElapsed = nowMs - activeLockPromptAtMs
            if (activeLockPromptPkg == packageName && promptElapsed in 0L..LOCK_PROMPT_STALE_MS) {
                if (isLockOverlayVisible || promptElapsed < PROMPT_LAUNCH_GRACE_MS) {
                    return false
                }
                activeLockPromptPkg = null
                activeLockPromptAtMs = 0L
            }

            if (activeForegroundUnlockedPkg == packageName) {
                if (getEffectiveRelockTimeoutSeconds(packageName) == 0) {
                    instantIdleDeadlines[packageName] = Long.MAX_VALUE
                }
                return false
            }

            // If user switched directly from another protected app, move that previous Instant app to its 1m idle timer
            val prevActive = activeForegroundUnlockedPkg
            if (prevActive != null && prevActive != packageName) {
                moveInstantPkgToBackgroundLocked(prevActive, nowMs)
                if (lastUnlockedPkg == prevActive && (nowMs - lastUnlockedAtMs) >= POST_UNLOCK_SETTLE_MS) {
                    lastUnlockedPkg = null
                }
                activeForegroundUnlockedPkg = null
            }

            val effectiveTimeoutSec = getEffectiveRelockTimeoutSeconds(packageName)

            if (effectiveTimeoutSec == 0) {
                // Guard against instant-relock re-triggering during AppLockActivity/BiometricPrompt close transition
                if (lastUnlockedPkg == packageName &&
                    (nowMs - lastUnlockedAtMs) in 0L..POST_UNLOCK_SETTLE_MS
                ) {
                    instantIdleDeadlines[packageName] = Long.MAX_VALUE
                    activeForegroundUnlockedPkg = packageName
                    return false
                }

                // Instant (0s) stays unlocked across switches until closed from Recents OR 60s of non-use
                val deadline = instantIdleDeadlines[packageName]
                if (deadline != null) {
                    if (deadline == Long.MAX_VALUE || nowMs < deadline) {
                        instantIdleDeadlines[packageName] = Long.MAX_VALUE
                        activeForegroundUnlockedPkg = packageName
                        return false
                    }
                    instantIdleDeadlines.remove(packageName)
                }
                return true
            }

            val expiry = unlockedUntilMs[packageName] ?: 0L
            if (nowMs < expiry) {
                activeForegroundUnlockedPkg = packageName
                return false
            }
            return true
        }
    }

    fun onProtectedAppSentToBackground(nowMs: Long = System.currentTimeMillis()) {
        synchronized(lock) {
            val activePkg = activeForegroundUnlockedPkg ?: return
            moveInstantPkgToBackgroundLocked(activePkg, nowMs)
            activeForegroundUnlockedPkg = null
        }
    }

    fun onAppClosedFromRecents(packageName: String, nowMs: Long = System.currentTimeMillis()) {
        synchronized(lock) {
            instantIdleDeadlines.remove(packageName)
            if (activeForegroundUnlockedPkg == packageName) {
                activeForegroundUnlockedPkg = null
            }
            if (lastUnlockedPkg == packageName) {
                lastUnlockedPkg = null
                lastUnlockedAtMs = 0L
            }
            if (activeLockPromptPkg == packageName) {
                activeLockPromptPkg = null
                activeLockPromptAtMs = 0L
            }
        }
    }

    fun onAllAppsClosedFromRecents(nowMs: Long = System.currentTimeMillis()) {
        synchronized(lock) {
            instantIdleDeadlines.clear()
            val activePkg = activeForegroundUnlockedPkg
            if (activePkg != null && getEffectiveRelockTimeoutSeconds(activePkg) == 0) {
                activeForegroundUnlockedPkg = null
            }
            val lastPkg = lastUnlockedPkg
            if (lastPkg != null && getEffectiveRelockTimeoutSeconds(lastPkg) == 0) {
                lastUnlockedPkg = null
                lastUnlockedAtMs = 0L
            }
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

            val activePkg = activeForegroundUnlockedPkg
            val activeTimeout = activePkg?.let { getEffectiveRelockTimeoutSeconds(it) }
                ?: relockTimeoutSeconds

            // Ignore window state churn right after a fingerprint unlock while AppLockActivity finishes back to the app
            if (activePkg != null &&
                activeTimeout == 0 &&
                lastUnlockedPkg == activePkg &&
                (nowMs - lastUnlockedAtMs) in 0L until POST_UNLOCK_SETTLE_MS
            ) {
                return
            }

            if (activePkg != null) {
                // In Instant (0s) mode, leaving without closing from Recents starts the 1-minute inactivity countdown
                moveInstantPkgToBackgroundLocked(activePkg, nowMs)
                activeForegroundUnlockedPkg = null
            }
            val lastPkg = lastUnlockedPkg
            val lastTimeout = lastPkg?.let { getEffectiveRelockTimeoutSeconds(it) } ?: relockTimeoutSeconds
            if (lastTimeout == 0 &&
                lastPkg != null &&
                (nowMs - lastUnlockedAtMs) >= POST_UNLOCK_SETTLE_MS
            ) {
                lastUnlockedPkg = null
            }
            activeLockPromptPkg = null
            activeLockPromptAtMs = 0L
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
            val effectiveTimeoutSec = getEffectiveRelockTimeoutSeconds(packageName)
            if (effectiveTimeoutSec > 0) {
                unlockedUntilMs[packageName] = nowMs + effectiveTimeoutSec * 1000L
                instantIdleDeadlines.remove(packageName)
            } else {
                unlockedUntilMs.remove(packageName)
                instantIdleDeadlines[packageName] = Long.MAX_VALUE
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
            instantIdleDeadlines.clear()
            activeForegroundUnlockedPkg = null
            activeLockPromptPkg = null
            activeLockPromptAtMs = 0L
            lastUnlockedPkg = null
            lastUnlockedAtMs = 0L
        }
    }

    private fun moveInstantPkgToBackgroundLocked(pkg: String, nowMs: Long) {
        if (getEffectiveRelockTimeoutSeconds(pkg) != 0) return
        val currentDeadline = instantIdleDeadlines[pkg]
        if (currentDeadline == Long.MAX_VALUE) {
            instantIdleDeadlines[pkg] = nowMs + INSTANT_INACTIVITY_TIMEOUT_MS
        }
    }

    suspend fun loadLaunchableApps(context: Context): List<InstalledAppItem> = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        val launcherIntent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        val resolved = pm.queryIntentActivities(launcherIntent, PackageManager.MATCH_ALL)
        val (currentLocked, currentTimeouts) = synchronized(lock) {
            protectedPackages.toSet() to appRelockTimeouts.toMap()
        }
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
                    isLocked = pkg in currentLocked,
                    customRelockTimeoutSeconds = currentTimeouts[pkg]
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

    private fun persistAppTimeoutsLocked() {
        val encoded = appRelockTimeouts.entries.mapTo(HashSet()) { (pkg, sec) -> "$pkg|$sec" }
        prefs.edit().putStringSet(KEY_APP_RELOCK_TIMEOUTS, encoded).apply()
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
            appRelockTimeouts.clear()
            prefs.getStringSet(KEY_APP_RELOCK_TIMEOUTS, emptySet())?.forEach { entry ->
                val sep = entry.lastIndexOf('|')
                if (sep > 0) {
                    val pkg = entry.substring(0, sep)
                    val sec = entry.substring(sep + 1).toIntOrNull()
                    if (pkg.isNotBlank() && sec != null && sec >= 0) {
                        appRelockTimeouts[pkg] = sec
                    }
                }
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
            appRelockTimeouts = appRelockTimeouts.toMap(),
            totalUnlockCount = totalUnlockCount,
            totalInterceptionCount = totalInterceptionCount
        )
    }

    companion object {
        private const val PREFS_NAME = "dns_pulse_app_auth"
        private const val KEY_MASTER_GUARD = "key_master_guard"
        private const val KEY_PROTECTED_PACKAGES = "key_protected_packages"
        private const val KEY_APP_RELOCK_TIMEOUTS = "key_app_relock_timeouts"
        private const val KEY_RELOCK_TIMEOUT_SEC = "key_relock_timeout_sec"
        private const val KEY_ALLOW_CREDENTIAL_FALLBACK = "key_allow_credential_fallback"
        private const val KEY_TOTAL_UNLOCKS = "key_total_unlocks"
        private const val KEY_TOTAL_INTERCEPTIONS = "key_total_interceptions"
        internal const val POST_UNLOCK_SETTLE_MS = 1_500L
        internal const val PROMPT_LAUNCH_GRACE_MS = 1_200L
        internal const val LOCK_PROMPT_STALE_MS = 20_000L
        internal const val INSTANT_INACTIVITY_TIMEOUT_MS = 60_000L

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
