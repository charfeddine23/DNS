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
    fun `instant relock stays unlocked inside app and only re-prompts after leaving and re-entering`() {
        val repo = AppAuthenticatorRepository.createIsolatedForTesting(context)
        val gallery = "com.google.android.apps.photos"
        repo.toggleAppLock(gallery, true)
        repo.setRelockTimeoutSeconds(0)
        repo.setMasterGuardEnabled(true)

        val now = 1_760_000_100_000L
        assertTrue(repo.shouldPromptLock(gallery, nowMs = now))

        // Simulate active prompt preventing duplicate stacked prompts while scanning fingerprint
        repo.setLockPromptActive(gallery, nowMs = now)
        assertFalse(repo.shouldPromptLock(gallery, nowMs = now + 500L))
        assertFalse(repo.shouldPromptLock(gallery, nowMs = now + 1_200L))

        // User unlocks via fingerprint
        repo.markAppUnlocked(gallery, nowMs = now + 1_400L)
        assertFalse(repo.shouldPromptLock(gallery, nowMs = now + 1_500L))

        // Transient exit flicker right during AppLockActivity/BiometricPrompt close does NOT revoke unlock
        repo.onNonProtectedAppForegrounded("com.android.launcher3", nowMs = now + 1_800L)
        assertFalse(repo.shouldPromptLock(gallery, nowMs = now + 2_000L))

        // Repeated in-app window state events keep the app unlocked with zero extra prompts
        assertFalse(repo.shouldPromptLock(gallery, nowMs = now + 30_000L))
        assertFalse(repo.shouldPromptLock(gallery, nowMs = now + 120_000L))

        // User genuinely exits to Home launcher or another app -> re-locks immediately for next entry
        repo.onNonProtectedAppForegrounded("com.android.chrome", nowMs = now + 125_000L)
        assertTrue(repo.shouldPromptLock(gallery, nowMs = now + 125_500L))
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
}
