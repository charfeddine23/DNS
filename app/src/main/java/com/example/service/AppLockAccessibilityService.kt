package com.example.service

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import com.example.MainActivity
import com.example.data.repository.AppAuthenticatorRepository
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs

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

    // Recents tracking for Instant (0s) mode: close immediately when removed from Recents
    @Volatile
    private var inRecentsMode: Boolean = false
    @Volatile
    private var lastRecentsScanMs: Long = 0L
    @Volatile
    private var lastHorizontalScrollMs: Long = 0L
    @Volatile
    private var lastObservedRecentsNodeCount: Int = 0
    private val trackedInstantLabels = ConcurrentHashMap<String, String>()
    private val observedInstantPkgsInRecents = ConcurrentHashMap.newKeySet<String>()

    private val screenOffReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == Intent.ACTION_SCREEN_OFF) {
                inRecentsMode = false
                observedInstantPkgsInRecents.clear()
                trackedInstantLabels.clear()
                lastLaunchAttemptPkg = null
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

    @Suppress("DEPRECATION")
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        val eventType = event.eventType
        val now = System.currentTimeMillis()

        // Handle Recents dismissal clicks, announcements, and swipes while Recents is active
        if (inRecentsMode && (
                eventType == AccessibilityEvent.TYPE_VIEW_CLICKED ||
                    eventType == AccessibilityEvent.TYPE_ANNOUNCEMENT ||
                    eventType == AccessibilityEvent.TYPE_VIEW_SCROLLED ||
                    eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
                )
        ) {
            handleRecentsInteractionEvent(event, eventType, now)
            return
        }

        if (eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
            eventType != AccessibilityEvent.TYPE_WINDOWS_CHANGED
        ) {
            return
        }

        if (now - lastPackageCacheRefreshMs > PACKAGE_CACHE_TTL_MS || homePackages.isEmpty()) {
            refreshInstalledCategoryCaches(force = false)
        }

        // Check Recents/Overview BEFORE filtering SystemUI/Launcher so task closures are detected
        if (isRecentsCurrentlyActive(event, eventType)) {
            if (!inRecentsMode) {
                enterRecentsMode(now)
            } else {
                scanRecentsHierarchy(now, isInitial = false)
            }
            return
        }

        if (inRecentsMode) {
            // Confirm we genuinely left Recents (don't exit Recents mode on transient empty TYPE_WINDOWS_CHANGED)
            val activePkg = resolveForegroundAppPackage(event, eventType)
            if (activePkg == null) return
            exitRecentsMode()
            processForegroundPackage(activePkg, event, eventType, now)
            return
        }

        val activePkg = resolveForegroundAppPackage(event, eventType) ?: return
        processForegroundPackage(activePkg, event, eventType, now)
    }

    private fun processForegroundPackage(
        pkg: String,
        event: AccessibilityEvent,
        eventType: Int,
        now: Long
    ) {
        if (pkg == packageName) {
            if (!AppLockActivity.isActivityForeground &&
                eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
                event.className?.toString() == MainActivity::class.java.name
            ) {
                lastLaunchAttemptPkg = null
                authRepo.onNonProtectedAppForegrounded(pkg, now, forceOwnApp = true)
            }
            return
        }

        // Never lock or revoke sessions for SystemUI overlays, Biometric dialogs, permission sheets, or IMEs
        if (isSystemTransientPackage(pkg)) {
            return
        }

        if (authRepo.isPackageProtected(pkg)) {
            val isOverlayVisible = isLockOverlayCurrentlyVisible()
            if (authRepo.shouldPromptLock(pkg, now, isLockOverlayVisible = isOverlayVisible)) {
                if (lastLaunchAttemptPkg == pkg && (now - lastLaunchAttemptMs) < LAUNCH_DEBOUNCE_MS) {
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
                runCatching { startActivity(lockIntent) }
            }
            return
        }

        // Non-protected foreground app or Home launcher took real active focus
        val isHome = pkg in homePackages
        val isRealAppWindow = pkg in launcherPackages || hasActiveAppWindow(pkg)
        if (isHome || isRealAppWindow) {
            lastLaunchAttemptPkg = null
            authRepo.onNonProtectedAppForegrounded(pkg, now)
        }
    }

    private fun resolveForegroundAppPackage(event: AccessibilityEvent, eventType: Int): String? {
        val windowPkg = runCatching {
            val appWindows = windows.filter { win ->
                win.type == AccessibilityWindowInfo.TYPE_APPLICATION
            }
            val focusedWin = appWindows.firstOrNull { it.isFocused }
                ?: appWindows.firstOrNull { it.isActive }
                ?: appWindows.maxByOrNull { it.layer }
            val rootPkg = focusedWin?.root?.packageName?.toString()?.takeIf { it.isNotBlank() }
            rootPkg
        }.getOrNull()

        if (windowPkg != null && !isSystemTransientPackage(windowPkg)) {
            return windowPkg
        }

        if (eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            val eventPkg = event.packageName?.toString()?.takeIf { it.isNotBlank() } ?: return null
            val className = event.className?.toString().orEmpty()
            // Ignore transient toasts/tooltips that don't represent a real app screen
            if (className == "android.widget.Toast" || className == "android.widget.PopupWindow") {
                return null
            }
            return eventPkg
        }

        return windowPkg
    }

    private fun hasActiveAppWindow(pkg: String): Boolean {
        return runCatching {
            windows.any { win ->
                win.type == AccessibilityWindowInfo.TYPE_APPLICATION &&
                    (win.isFocused || win.isActive) &&
                    win.root?.packageName?.toString() == pkg
            }
        }.getOrDefault(false)
    }

    private fun isLockOverlayCurrentlyVisible(): Boolean {
        if (AppLockActivity.isActivityForeground) return true
        // Only treat our package window as the lock overlay if it was just launched
        val sinceLaunch = System.currentTimeMillis() - lastLaunchAttemptMs
        if (sinceLaunch > AppAuthenticatorRepository.PROMPT_LAUNCH_GRACE_MS) {
            return false
        }
        return runCatching {
            windows.any { win ->
                win.type == AccessibilityWindowInfo.TYPE_APPLICATION &&
                    win.root?.packageName?.toString() == packageName
            }
        }.getOrDefault(false)
    }

    private fun isRecentsCurrentlyActive(event: AccessibilityEvent, eventType: Int): Boolean {
        val eventPkg = event.packageName?.toString().orEmpty()
        val className = event.className?.toString().orEmpty()
        if (eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
            isRecentsClassName(eventPkg, className)
        ) {
            return true
        }

        return runCatching {
            val activeWin = windows.firstOrNull { it.isFocused || it.isActive } ?: return false
            val root = activeWin.root ?: return false
            val rootPkg = root.packageName?.toString().orEmpty()
            if (rootPkg !in homePackages && !isKnownRecentsHost(rootPkg)) {
                return false
            }
            hasVisibleRecentsMarkers(root)
        }.getOrDefault(false)
    }

    private fun isRecentsClassName(pkg: String, className: String?): Boolean {
        if (className.isNullOrBlank()) return false
        val lower = className.lowercase()
        val looksLikeRecentsClass = lower.contains("recents") ||
            lower.contains("overview") ||
            lower.contains("taskswitcher") ||
            lower.contains("recenttasks") ||
            lower.contains("tasklist")
        if (!looksLikeRecentsClass) return false
        return pkg in homePackages || isKnownRecentsHost(pkg) || pkg == "com.android.systemui"
    }

    private fun isKnownRecentsHost(pkg: String): Boolean {
        val lower = pkg.lowercase()
        return lower == "com.android.systemui" ||
            lower.contains("launcher") ||
            lower == "com.miui.home" ||
            lower == "com.mi.android.globallauncher"
    }

    private fun hasVisibleRecentsMarkers(root: AccessibilityNodeInfo): Boolean {
        val recentsIds = listOf(
            "clear_all",
            "clear_recents",
            "btn_clear",
            "clearAnimView",
            "overview_actions_view",
            "recents_view",
            "snapshot"
        )
        for (id in recentsIds) {
            val nodes = root.findAccessibilityNodeInfosByViewId("${root.packageName}:id/$id")
            if (!nodes.isNullOrEmpty() && nodes.any { it.isVisibleToUser }) {
                return true
            }
        }
        return false
    }

    private fun enterRecentsMode(now: Long) {
        inRecentsMode = true
        lastLaunchAttemptPkg = null
        lastRecentsScanMs = 0L
        lastHorizontalScrollMs = 0L
        lastObservedRecentsNodeCount = 0
        observedInstantPkgsInRecents.clear()
        trackedInstantLabels.clear()

        authRepo.onProtectedAppSentToBackground(now)
        val instantPkgs = authRepo.getInstantUnlockedPackages()
        for (pkg in instantPkgs) {
            trackedInstantLabels[pkg] = normalizeLabel(resolveAppName(pkg))
        }
        scanRecentsHierarchy(now, isInitial = true)
    }

    private fun exitRecentsMode() {
        inRecentsMode = false
        observedInstantPkgsInRecents.clear()
        trackedInstantLabels.clear()
        lastObservedRecentsNodeCount = 0
    }

    @Suppress("DEPRECATION")
    private fun handleRecentsInteractionEvent(
        event: AccessibilityEvent,
        eventType: Int,
        now: Long
    ) {
        if (eventType == AccessibilityEvent.TYPE_VIEW_SCROLLED) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val dx = abs(event.scrollDeltaX)
                val dy = abs(event.scrollDeltaY)
                if (dx > dy && dx > 0) {
                    lastHorizontalScrollMs = now
                }
            }
            return
        }

        if (eventType == AccessibilityEvent.TYPE_VIEW_CLICKED) {
            val clickedLabel = buildString {
                event.text?.forEach { append(it).append(' ') }
                event.contentDescription?.let { append(it).append(' ') }
                runCatching {
                    val src = event.source
                    src?.viewIdResourceName?.let { append(it).append(' ') }
                    src?.contentDescription?.let { append(it).append(' ') }
                    src?.text?.let { append(it).append(' ') }
                }
            }.lowercase()

            if (isClearAllTextOrId(clickedLabel)) {
                authRepo.onAllAppsClosedFromRecents(now)
                observedInstantPkgsInRecents.clear()
                trackedInstantLabels.clear()
                return
            }

            if (isSingleCloseAction(clickedLabel)) {
                for ((pkg, normLabel) in trackedInstantLabels) {
                    if (normLabel.isNotBlank() && clickedLabel.contains(normLabel)) {
                        authRepo.onAppClosedFromRecents(pkg, now)
                        observedInstantPkgsInRecents.remove(pkg)
                        trackedInstantLabels.remove(pkg)
                    }
                }
            }
        }

        if (eventType == AccessibilityEvent.TYPE_ANNOUNCEMENT) {
            val announcement = event.text?.joinToString(" ")?.lowercase().orEmpty()
            if (isDismissalAnnouncement(announcement)) {
                if (announcement.contains("all") || announcement.contains("recents") || announcement.contains("الكل")) {
                    authRepo.onAllAppsClosedFromRecents(now)
                    observedInstantPkgsInRecents.clear()
                    trackedInstantLabels.clear()
                    return
                }
                for ((pkg, normLabel) in trackedInstantLabels) {
                    if (normLabel.isNotBlank() && announcement.contains(normLabel)) {
                        authRepo.onAppClosedFromRecents(pkg, now)
                        observedInstantPkgsInRecents.remove(pkg)
                        trackedInstantLabels.remove(pkg)
                    }
                }
            }
        }

        if (eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED ||
            eventType == AccessibilityEvent.TYPE_VIEW_CLICKED
        ) {
            if (now - lastRecentsScanMs >= RECENTS_SCAN_THROTTLE_MS) {
                scanRecentsHierarchy(now, isInitial = false)
            }
        }
    }

    private fun scanRecentsHierarchy(now: Long, isInitial: Boolean) {
        if (trackedInstantLabels.isEmpty()) return
        lastRecentsScanMs = now

        val root = runCatching { rootInActiveWindow }.getOrNull() ?: return
        val visibleLabels = ArrayList<String>(24)
        collectNodeLabels(root, visibleLabels, depth = 0)

        if (visibleLabels.any { isEmptyRecentsText(it) }) {
            authRepo.onAllAppsClosedFromRecents(now)
            observedInstantPkgsInRecents.clear()
            trackedInstantLabels.clear()
            return
        }

        val currentlyVisible = HashSet<String>()
        for ((pkg, normLabel) in trackedInstantLabels) {
            if (normLabel.isNotBlank() && visibleLabels.any { it.contains(normLabel) }) {
                currentlyVisible.add(pkg)
                observedInstantPkgsInRecents.add(pkg)
            }
        }

        if (!isInitial &&
            (now - lastHorizontalScrollMs) > HORIZONTAL_SCROLL_GRACE_MS &&
            visibleLabels.size <= lastObservedRecentsNodeCount
        ) {
            val iterator = observedInstantPkgsInRecents.iterator()
            while (iterator.hasNext()) {
                val pkg = iterator.next()
                if (pkg !in currentlyVisible) {
                    authRepo.onAppClosedFromRecents(pkg, now)
                    iterator.remove()
                    trackedInstantLabels.remove(pkg)
                }
            }
        }
        lastObservedRecentsNodeCount = visibleLabels.size
    }

    private fun collectNodeLabels(
        node: AccessibilityNodeInfo?,
        out: MutableList<String>,
        depth: Int
    ) {
        if (node == null || depth > 8 || out.size >= 80) return
        if (node.isVisibleToUser) {
            node.contentDescription?.toString()?.let { desc ->
                val norm = normalizeLabel(desc)
                if (norm.isNotBlank()) out.add(norm)
            }
            node.text?.toString()?.let { text ->
                val norm = normalizeLabel(text)
                if (norm.isNotBlank()) out.add(norm)
            }
        }
        val count = node.childCount.coerceAtMost(24)
        for (i in 0 until count) {
            val child = runCatching { node.getChild(i) }.getOrNull() ?: continue
            collectNodeLabels(child, out, depth + 1)
        }
    }

    private fun normalizeLabel(raw: String): String {
        return raw.trim().lowercase().replace(Regex("\\s+"), " ")
    }

    private fun isClearAllTextOrId(lowerText: String): Boolean {
        return lowerText.contains("clear all") ||
            lowerText.contains("close all") ||
            lowerText.contains("dismiss all") ||
            lowerText.contains("clear_all") ||
            lowerText.contains("clear_recents") ||
            lowerText.contains("clean_up") ||
            lowerText.contains("clearanimview") ||
            lowerText.contains("مسح الكل") ||
            lowerText.contains("إغلاق الكل")
    }

    private fun isSingleCloseAction(lowerText: String): Boolean {
        return lowerText.contains("dismiss") ||
            lowerText.contains("close") ||
            lowerText.contains("remove") ||
            lowerText.contains("إغلاق") ||
            lowerText.contains("إزالة")
    }

    private fun isDismissalAnnouncement(lowerText: String): Boolean {
        return lowerText.contains("dismissed") ||
            lowerText.contains("removed") ||
            lowerText.contains("closed") ||
            lowerText.contains("cleared") ||
            lowerText.contains("تمت إزالة") ||
            lowerText.contains("تم إغلاق") ||
            lowerText.contains("تم مسح")
    }

    private fun isEmptyRecentsText(normText: String): Boolean {
        return normText.contains("no recent items") ||
            normText.contains("no recently used apps") ||
            normText.contains("لا توجد عناصر حديثة") ||
            normText.contains("لا توجد تطبيقات حديثة")
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
                PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_ALL.toLong())
            )
        } else {
            @Suppress("DEPRECATION")
            packageManager.queryIntentActivities(intent, PackageManager.MATCH_ALL)
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
            lower.contains("keyguard") ||
            lower.contains("screenshot")
    }

    companion object {
        private const val PACKAGE_CACHE_TTL_MS = 60_000L
        private const val LAUNCH_DEBOUNCE_MS = 650L
        private const val RECENTS_SCAN_THROTTLE_MS = 120L
        private const val HORIZONTAL_SCROLL_GRACE_MS = 450L
    }
}
