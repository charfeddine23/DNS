package com.example.data.model

enum class BiometricHardwareStatus {
    READY,
    NO_ENROLLED_BIOMETRICS,
    NO_HARDWARE,
    UNAVAILABLE
}

enum class AppFilterTab {
    ALL,
    PROTECTED,
    USER_APPS,
    SYSTEM_APPS
}

data class InstalledAppItem(
    val packageName: String,
    val appName: String,
    val isSystemApp: Boolean,
    val isLocked: Boolean
)

data class AppAuthenticatorConfig(
    val isMasterGuardEnabled: Boolean = false,
    val relockTimeoutSeconds: Int = 30,
    val allowDeviceCredentialFallback: Boolean = true,
    val protectedPackages: Set<String> = emptySet(),
    val totalUnlockCount: Long = 0L,
    val totalInterceptionCount: Long = 0L
)
