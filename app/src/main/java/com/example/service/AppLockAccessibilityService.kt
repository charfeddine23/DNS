package com.example.service

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.content.pm.PackageManager
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

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null || event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString()?.takeIf { it.isNotBlank() } ?: return

        // Never lock DNS Pulse itself, SystemUI, or soft keyboards/IME windows
        if (pkg == packageName || isSystemTransientPackage(pkg)) {
            return
        }

        val now = System.currentTimeMillis()
        if (authRepo.shouldPromptLock(pkg, now)) {
            // Debounce rapid consecutive window events for the same app launch
            if (lastLaunchAttemptPkg == pkg && now - lastLaunchAttemptMs < 700L) {
                return
            }
            lastLaunchAttemptPkg = pkg
            lastLaunchAttemptMs = now
            authRepo.recordInterception()

            val appLabel = resolveAppName(pkg)
            val lockIntent = Intent(this, AppLockActivity::class.java).apply {
                addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP
                )
                putExtra(AppLockActivity.EXTRA_TARGET_PACKAGE, pkg)
                putExtra(AppLockActivity.EXTRA_TARGET_APP_NAME, appLabel)
            }
            startActivity(lockIntent)
        } else {
            authRepo.onNonProtectedAppForegrounded(pkg)
        }
    }

    override fun onInterrupt() {
        // No persistent state to clean up
    }

    private fun resolveAppName(pkg: String): String {
        return try {
            val info = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
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
        return pkg == "com.android.systemui" ||
            pkg.contains("inputmethod", ignoreCase = true) ||
            pkg.contains("keyboard", ignoreCase = true) ||
            pkg == "com.google.android.inputmethod.latin" ||
            pkg == "com.android.permissioncontroller"
    }
}
