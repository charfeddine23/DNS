package com.example.service

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.view.accessibility.AccessibilityEvent
import com.example.data.repository.AppAuthenticatorRepository

class AppLockAccessibilityService : AccessibilityService() {

    private val authRepo: AppAuthenticatorRepository by lazy {
        AppAuthenticatorRepository.getInstance(applicationContext)
    }

    @Volatile
    private var lastLaunchAttemptPkg: String? = null
    @Volatile
    private var lastLaunchAttemptMs: Long = 0L
    @Volatile
    private var homePackages: Set<String> = emptySet()
    @Volatile
    private var launcherPackages: Set<String> = emptySet()
    @Volatile
    private var lastPackageCacheRefreshMs: Long = 0L

    private val screenOffReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == Intent.ACTION_SCREEN_OFF) {
                authRepo.clearUnlockSessions()
            }
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        refreshInstalledCategoryCaches(force = true)
        val filter = IntentFilter(Intent.ACTION_SCREEN_OFF)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(screenOffReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            registerReceiver(screenOffReceiver, filter)
        }
    }

    override fun onDestroy() {
        runCatching { unregisterReceiver(screenOffReceiver) }
        super.onDestroy()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null || event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString()?.takeIf { it.isNotBlank() } ?: return

        val now = System.currentTimeMillis()
        if (pkg == packageName) {
            if (event.className?.toString() == "com.example.MainActivity") {
                authRepo.onNonProtectedAppForegrounded(pkg, now, forceOwnApp = true)
            }
            return
        }

        // Never lock or revoke sessions for SystemUI, Biometric dialogs, or IMEs
        if (isSystemTransientPackage(pkg)) {
            return
        }

        if (now - lastPackageCacheRefreshMs > PACKAGE_CACHE_TTL_MS || homePackages.isEmpty()) {
            refreshInstalledCategoryCaches(force = false)
        }

        if (authRepo.isPackageProtected(pkg)) {
            if (authRepo.shouldPromptLock(pkg, now)) {
                if (lastLaunchAttemptPkg == pkg && now - lastLaunchAttemptMs < 900L) {
                    return
                }
                lastLaunchAttemptPkg = pkg
                lastLaunchAttemptMs = now
                authRepo.setLockPromptActive(pkg, now)
                authRepo.recordInterception()

                val appLabel = resolveAppName(pkg)
                val lockIntent = Intent(this, AppLockActivity::class.java).apply {
                    addFlags(
                        Intent.FLAG_ACTIVITY_NEW_TASK or
                            Intent.FLAG_ACTIVITY_CLEAR_TOP or
                            Intent.FLAG_ACTIVITY_SINGLE_TOP or
                            Intent.FLAG_ACTIVITY_NO_ANIMATION
                    )
                    putExtra(AppLockActivity.EXTRA_TARGET_PACKAGE, pkg)
                    putExtra(AppLockActivity.EXTRA_TARGET_APP_NAME, appLabel)
                }
                startActivity(lockIntent)
            }
            return
        }

        // Only consider the user to have exited an unlocked protected app when a Home launcher
        // or another full-screen launchable app genuinely takes the foreground.
        val isHomeLauncher = pkg in homePackages
        val isOtherLaunchableFullscreen = event.isFullScreen && pkg in launcherPackages
        if (isHomeLauncher || isOtherLaunchableFullscreen) {
            authRepo.onNonProtectedAppForegrounded(pkg, now)
        }
    }

    override fun onInterrupt() {
        // No persistent state to clean up
    }

    private fun refreshInstalledCategoryCaches(force: Boolean) {
        val now = System.currentTimeMillis()
        if (!force && now - lastPackageCacheRefreshMs < PACKAGE_CACHE_TTL_MS && homePackages.isNotEmpty()) {
            return
        }
        lastPackageCacheRefreshMs = now

        runCatching {
            val homeIntent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_HOME)
            }
            val homes = queryPackagesForIntent(homeIntent)
            if (homes.isNotEmpty()) {
                homePackages = homes
            }
        }
        runCatching {
            val launcherIntent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
            }
            val launchers = queryPackagesForIntent(launcherIntent)
            if (launchers.isNotEmpty()) {
                launcherPackages = launchers
            }
        }
    }

    private fun queryPackagesForIntent(intent: Intent): Set<String> {
        val resolved = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.queryIntentActivities(
                intent,
                PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_DEFAULT_ONLY.toLong())
            )
        } else {
            @Suppress("DEPRECATION")
            packageManager.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)
        }
        return resolved.mapNotNull { it.activityInfo?.packageName }.filter { it != packageName }.toSet()
    }

    private fun resolveAppName(pkg: String): String {
        return try {
            val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                packageManager.getApplicationInfo(pkg, PackageManager.ApplicationInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                packageManager.getApplicationInfo(pkg, 0)
            }
            packageManager.getApplicationLabel(info).toString()
        } catch (_: Throwable) {
            pkg
        }
    }

    private fun isSystemTransientPackage(pkg: String): Boolean {
        val lower = pkg.lowercase()
        return lower == "android" ||
            lower == "com.android.systemui" ||
            lower == "com.android.permissioncontroller" ||
            lower == "com.google.android.permissioncontroller" ||
            lower == "com.google.android.gms" ||
            lower == "com.google.android.ext.services" ||
            lower.contains("inputmethod") ||
            lower.contains("keyboard") ||
            lower.contains("biometric") ||
            lower.contains("fingerprint") ||
            lower.contains("faceunlock") ||
            lower.contains("keyguard")
    }

    companion object {
        private const val PACKAGE_CACHE_TTL_MS = 60_000L
    }
}
