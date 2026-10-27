package com.obsidian.mobile.auth

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

/**
 * Fingerprint unlock (class 3 biometrics only). The app PIN is the fallback,
 * so the prompt's negative button is "Use PIN" rather than the device screen lock.
 */
class BiometricAuthManager(private val context: Context) {

    enum class Availability { READY, NOT_ENROLLED, UNAVAILABLE }

    fun availability(): Availability = when (BiometricManager.from(context).canAuthenticate(BIOMETRIC_STRONG)) {
        BiometricManager.BIOMETRIC_SUCCESS -> Availability.READY
        BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> Availability.NOT_ENROLLED
        else -> Availability.UNAVAILABLE
    }

    /**
     * [onFallback] runs when the user picks "Use PIN" or the sensor can't be used;
     * a dismissed prompt calls nothing (the lock screen stays).
     */
    fun prompt(
        activity: FragmentActivity,
        title: String,
        subtitle: String,
        onSuccess: () -> Unit,
        onFallback: (message: String?) -> Unit,
    ) {
        val callback = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) = onSuccess()

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                when (errorCode) {
                    BiometricPrompt.ERROR_USER_CANCELED, BiometricPrompt.ERROR_CANCELED -> Unit
                    BiometricPrompt.ERROR_NEGATIVE_BUTTON -> onFallback(null)
                    else -> onFallback(errString.toString())
                }
            }
        }
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)
            .setAllowedAuthenticators(BIOMETRIC_STRONG)
            .setNegativeButtonText("Use PIN")
            .build()
        BiometricPrompt(activity, ContextCompat.getMainExecutor(activity), callback).authenticate(info)
    }
}
