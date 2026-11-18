package com.example

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.repository.AppAuthenticatorRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AppAuthenticatorRepositoryTest {

    private lateinit var context: Application

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("dns_pulse_app_auth", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
    }

    @Test
    fun `master guard and toggleAppLock gate protected packages accurately`() {
        val repo = AppAuthenticatorRepository.createIsolatedForTesting(context)
        val whatsapp = "com.whatsapp"
        val telegram = "org.telegram.messenger"

        assertFalse(repo.configFlow.value.isMasterGuardEnabled)
        assertFalse(repo.shouldPromptLock(whatsapp))

        repo.toggleAppLock(whatsapp, true)
        assertTrue(whatsapp in repo.configFlow.value.protectedPackages)
        assertFalse(repo.shouldPromptLock(whatsapp))

        repo.setMasterGuardEnabled(true)
        assertTrue(repo.shouldPromptLock(whatsapp))
        assertFalse(repo.shouldPromptLock(telegram))

        repo.toggleAppLock(whatsapp, false)
        assertFalse(repo.shouldPromptLock(whatsapp))
    }

    @Test
    fun `relock timeout grants temporary grace then re-locks after expiry`() {
        val repo = AppAuthenticatorRepository.createIsolatedForTesting(context)
        val bankApp = "com.example.bank"
        repo.toggleAppLock(bankApp, true)
        repo.setRelockTimeoutSeconds(30)
        repo.setMasterGuardEnabled(true)

        val startMs = 1_760_000_000_000L
        assertTrue(repo.shouldPromptLock(bankApp, nowMs = startMs))

        repo.markAppUnlocked(bankApp, nowMs = startMs)
        assertEquals(1L, repo.configFlow.value.totalUnlockCount)

        // Same active foreground session -> no prompt
        assertFalse(repo.shouldPromptLock(bankApp, nowMs = startMs + 5_000L))

        // Switch away to another app and come back within 30s grace window -> still unlocked
        repo.onNonProtectedAppForegrounded("com.android.launcher3", nowMs = startMs + 10_000L)
        assertFalse(repo.shouldPromptLock(bankApp, nowMs = startMs + 20_000L))

        // Leave again and return after 31s -> prompts again
        repo.onNonProtectedAppForegrounded("com.android.launcher3", nowMs = startMs + 25_000L)
        assertTrue(repo.shouldPromptLock(bankApp, nowMs = startMs + 31_000L))
    }

    @Test
    fun `instant relock stays unlocked until closed from recents or 1 minute of inactivity`() {
        val repo = AppAuthenticatorRepository.createIsolatedForTesting(context)
        val gallery = "com.google.android.apps.photos"
        repo.toggleAppLock(gallery, true)
        repo.setRelockTimeoutSeconds(0)
        repo.setMasterGuardEnabled(true)

        val now = 1_760_000_100_000L
        assertTrue(repo.shouldPromptLock(gallery, nowMs = now))

        // Simulate active prompt preventing duplicate stacked prompts while scanning fingerprint
        repo.setLockPromptActive(gallery, nowMs = now)
        assertFalse(repo.shouldPromptLock(gallery, nowMs = now + 500L, isLockOverlayVisible = true))
        assertFalse(repo.shouldPromptLock(gallery, nowMs = now + 1_200L, isLockOverlayVisible = true))

        // User unlocks via fingerprint
        repo.markAppUnlocked(gallery, nowMs = now + 1_400L)
        assertFalse(repo.shouldPromptLock(gallery, nowMs = now + 1_500L))

        // Transient exit flicker right during AppLockActivity/BiometricPrompt close does NOT revoke unlock
        repo.onNonProtectedAppForegrounded("com.android.launcher3", nowMs = now + 1_800L)
        assertFalse(repo.shouldPromptLock(gallery, nowMs = now + 2_000L))

        // Repeated in-app window state events keep the app unlocked indefinitely while actively in use
        assertFalse(repo.shouldPromptLock(gallery, nowMs = now + 30_000L))
        assertFalse(repo.shouldPromptLock(gallery, nowMs = now + 120_000L))

        // User leaves to Chrome without closing Gallery from Recents -> stays unlocked if returning within 1 minute
        repo.onNonProtectedAppForegrounded("com.android.chrome", nowMs = now + 125_000L)
        assertFalse(repo.shouldPromptLock(gallery, nowMs = now + 165_000L))

        // User leaves again (or opens Recents) and stays inactive for >= 60 seconds -> auto re-locks!
        repo.onProtectedAppSentToBackground(nowMs = now + 170_000L)
        assertTrue(repo.shouldPromptLock(gallery, nowMs = now + 230_500L))

        // Unlock again, then close explicitly from Recents -> re-locks IMMEDIATELY even 2s later
        val unlock2 = now + 231_000L
        repo.markAppUnlocked(gallery, nowMs = unlock2)
        assertTrue(gallery in repo.getInstantUnlockedPackages())
        repo.onProtectedAppSentToBackground(nowMs = unlock2 + 1_000L)
        repo.onAppClosedFromRecents(gallery, nowMs = unlock2 + 1_800L)
        assertTrue(repo.shouldPromptLock(gallery, nowMs = unlock2 + 2_500L))
    }

    @Test
    fun `clear all in recents and stale prompt self-healing enforce strict relock`() {
        val repo = AppAuthenticatorRepository.createIsolatedForTesting(context)
        val whatsapp = "com.whatsapp"
        val telegram = "org.telegram.messenger"
        repo.setMasterGuardEnabled(true)
        repo.setRelockTimeoutSeconds(0)
        repo.toggleAppLock(whatsapp, true)
        repo.toggleAppLock(telegram, true)

        val t0 = 1_760_000_500_000L
        repo.markAppUnlocked(whatsapp, nowMs = t0)
        repo.onProtectedAppSentToBackground(nowMs = t0 + 5_000L)
        repo.markAppUnlocked(telegram, nowMs = t0 + 6_000L)
        repo.onProtectedAppSentToBackground(nowMs = t0 + 10_000L)

        // Clear all from Recents must re-lock both apps immediately
        repo.onAllAppsClosedFromRecents(nowMs = t0 + 11_000L)
        assertTrue(repo.shouldPromptLock(whatsapp, nowMs = t0 + 12_000L))
        assertTrue(repo.shouldPromptLock(telegram, nowMs = t0 + 12_500L))

        // If AppLockActivity is no longer visible after initial launch grace window, stale prompt state heals instead of allowing bypass
        repo.setLockPromptActive(whatsapp, nowMs = t0 + 20_000L)
        assertFalse(repo.shouldPromptLock(whatsapp, nowMs = t0 + 20_500L, isLockOverlayVisible = false))
        assertTrue(repo.shouldPromptLock(whatsapp, nowMs = t0 + 22_000L, isLockOverlayVisible = false))
    }

    @Test
    fun `stats, preferences, and protected apps persist cleanly across instances`() {
        val repo = AppAuthenticatorRepository.createIsolatedForTesting(context)
        repo.setMasterGuardEnabled(true)
        repo.setRelockTimeoutSeconds(60)
        repo.setAllowDeviceCredentialFallback(false)
        repo.toggleAppLock("com.whatsapp", true)
        repo.toggleAppLock("com.instagram.android", true)
        repo.recordInterception()
        repo.recordInterception()
        repo.markAppUnlocked("com.whatsapp")

        val reloaded = AppAuthenticatorRepository.createIsolatedForTesting(context)
        val config = reloaded.configFlow.value
        assertTrue(config.isMasterGuardEnabled)
        assertEquals(60, config.relockTimeoutSeconds)
        assertFalse(config.allowDeviceCredentialFallback)
        assertEquals(setOf("com.whatsapp", "com.instagram.android"), config.protectedPackages)
        assertEquals(2L, config.totalInterceptionCount)
        assertEquals(1L, config.totalUnlockCount)
    }

    @Test
    fun `per-app relock timeouts operate independently and persist across instances`() {
        val repo = AppAuthenticatorRepository.createIsolatedForTesting(context)
        val whatsapp = "com.whatsapp"
        val telegram = "org.telegram.messenger"
        val notes = "com.example.notes"

        repo.setMasterGuardEnabled(true)
        repo.setRelockTimeoutSeconds(30) // Global fallback
        repo.toggleAppLock(whatsapp, true)
        repo.toggleAppLock(telegram, true)
        repo.toggleAppLock(notes, true)

        // Customize WhatsApp = Instant (0s), Telegram = 60s, Notes uses fallback (30s)
        repo.setAppRelockTimeoutSeconds(whatsapp, 0)
        repo.setAppRelockTimeoutSeconds(telegram, 60)

        assertEquals(0, repo.getEffectiveRelockTimeoutSeconds(whatsapp))
        assertEquals(60, repo.getEffectiveRelockTimeoutSeconds(telegram))
        assertEquals(30, repo.getEffectiveRelockTimeoutSeconds(notes))

        val startMs = 1_760_000_200_000L
        assertTrue(repo.shouldPromptLock(whatsapp, nowMs = startMs))
        assertTrue(repo.shouldPromptLock(telegram, nowMs = startMs))

        // Unlock WhatsApp (0s) and Telegram (60s)
        repo.markAppUnlocked(whatsapp, nowMs = startMs)
        repo.markAppUnlocked(telegram, nowMs = startMs + 5_000L)

        // Exit to launcher after settle window without closing from Recents
        repo.onNonProtectedAppForegrounded("com.android.launcher3", nowMs = startMs + 10_000L)

        // WhatsApp (Instant 0s) stays unlocked within <60s if not closed from Recents, and Telegram (60s) stays unlocked
        assertFalse(repo.shouldPromptLock(whatsapp, nowMs = startMs + 15_000L))
        assertFalse(repo.shouldPromptLock(telegram, nowMs = startMs + 15_000L))

        // Closing WhatsApp from Recents immediately locks WhatsApp on next open
        repo.onAppClosedFromRecents(whatsapp, nowMs = startMs + 16_000L)
        assertTrue(repo.shouldPromptLock(whatsapp, nowMs = startMs + 16_500L))
        assertFalse(repo.shouldPromptLock(telegram, nowMs = startMs + 16_500L))

        // Notes (fallback 30s) was never unlocked -> prompts; after unlock, stays unlocked for 30s
        assertTrue(repo.shouldPromptLock(notes, nowMs = startMs + 17_000L))
        repo.markAppUnlocked(notes, nowMs = startMs + 17_000L)
        repo.onNonProtectedAppForegrounded("com.android.launcher3", nowMs = startMs + 20_000L)
        assertFalse(repo.shouldPromptLock(notes, nowMs = startMs + 35_000L))
        // Leave again and return after the 30s grace window expires -> prompts again
        repo.onNonProtectedAppForegrounded("com.android.launcher3", nowMs = startMs + 40_000L)
        assertTrue(repo.shouldPromptLock(notes, nowMs = startMs + 48_000L))

        // Persistence check
        val reloaded = AppAuthenticatorRepository.createIsolatedForTesting(context)
        assertEquals(0, reloaded.getEffectiveRelockTimeoutSeconds(whatsapp))
        assertEquals(60, reloaded.getEffectiveRelockTimeoutSeconds(telegram))
        assertEquals(30, reloaded.getEffectiveRelockTimeoutSeconds(notes))
        assertEquals(mapOf(whatsapp to 0, telegram to 60), reloaded.configFlow.value.appRelockTimeouts)
    }
}
