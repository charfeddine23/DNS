package com.example.utils

import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import com.example.data.model.BiometricHardwareStatus

object BiometricHelper {

    private fun biometricAuthenticators(): Int =
        BiometricManager.Authenticators.BIOMETRIC_STRONG or
            BiometricManager.Authenticators.BIOMETRIC_WEAK

    private fun credentialAuthenticators(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            BiometricManager.Authenticators.BIOMETRIC_STRONG or
                BiometricManager.Authenticators.BIOMETRIC_WEAK or
                BiometricManager.Authenticators.DEVICE_CREDENTIAL
        } else {
            // On Android 9/10, androidx.biometric rejects BIOMETRIC_STRONG + DEVICE_CREDENTIAL combinations
            BiometricManager.Authenticators.BIOMETRIC_WEAK or
                BiometricManager.Authenticators.DEVICE_CREDENTIAL
        }

    fun checkHardwareStatus(context: Context): BiometricHardwareStatus {
        val manager = BiometricManager.from(context)
        return when (manager.canAuthenticate(biometricAuthenticators())) {
            BiometricManager.BIOMETRIC_SUCCESS -> BiometricHardwareStatus.READY
            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> BiometricHardwareStatus.NO_ENROLLED_BIOMETRICS
            BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE,
            BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE -> BiometricHardwareStatus.NO_HARDWARE
            else -> BiometricHardwareStatus.UNAVAILABLE
        }
    }

    fun canUseDeviceCredential(context: Context): Boolean {
        val manager = BiometricManager.from(context)
        return manager.canAuthenticate(credentialAuthenticators()) == BiometricManager.BIOMETRIC_SUCCESS
    }

    fun showFingerprintPrompt(
        activity: FragmentActivity,
        title: String,
        subtitle: String,
        negativeButtonText: String,
        allowDeviceCredential: Boolean,
        onSuccess: () -> Unit,
        onErrorOrCancelled: (String?) -> Unit,
        onCancelled: (() -> Unit)? = null
    ) {
        if (activity.isFinishing || activity.isDestroyed ||
            !activity.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
        ) {
            onCancelled?.invoke() ?: onErrorOrCancelled(null)
            return
        }

        val executor = ContextCompat.getMainExecutor(activity)
        val biometricPrompt = BiometricPrompt(
            activity,
            executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    onSuccess()
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)
                    val isCancel = errorCode == BiometricPrompt.ERROR_CANCELED ||
                        errorCode == BiometricPrompt.ERROR_USER_CANCELED ||
                        errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON
                    if (isCancel && onCancelled != null) {
                        onCancelled()
                    } else {
                        onErrorOrCancelled(if (isCancel) null else errString.toString())
                    }
                }

                override fun onAuthenticationFailed() {
                    super.onAuthenticationFailed()
                    // Keep prompt open for retry; BiometricPrompt handles attempt feedback natively
                }
            }
        )

        val builder = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)
            .setConfirmationRequired(false)

        if (allowDeviceCredential && canUseDeviceCredential(activity)) {
            builder.setAllowedAuthenticators(credentialAuthenticators())
        } else {
            builder.setAllowedAuthenticators(biometricAuthenticators())
            builder.setNegativeButtonText(negativeButtonText)
        }

        try {
            biometricPrompt.authenticate(builder.build())
        } catch (e: Exception) {
            onErrorOrCancelled(e.localizedMessage)
        }
    }

    fun buildEnrollFingerprintIntent(): Intent {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Intent(Settings.ACTION_BIOMETRIC_ENROLL).apply {
                putExtra(
                    Settings.EXTRA_BIOMETRIC_AUTHENTICATORS_ALLOWED,
                    biometricAuthenticators()
                )
            }
        } else {
            Intent(Settings.ACTION_SECURITY_SETTINGS)
        }
    }
}
