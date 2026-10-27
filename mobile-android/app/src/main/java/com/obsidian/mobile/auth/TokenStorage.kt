package com.obsidian.mobile.auth

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

class TokenStorage(context: Context) {

    private val prefs: SharedPreferences by lazy {
        try {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()

            EncryptedSharedPreferences.create(
                context,
                "obsidian_secure_prefs",
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
            // Fallback for testing environments
            context.getSharedPreferences("obsidian_prefs_fallback", Context.MODE_PRIVATE)
        }
    }

    var bearerToken: String?
        get() = prefs.getString(KEY_BEARER_TOKEN, null)
        set(value) = prefs.edit().putString(KEY_BEARER_TOKEN, value).apply()

    var userEmail: String
        get() = prefs.getString(KEY_USER_EMAIL, "priya@example.com") ?: "priya@example.com"
        set(value) = prefs.edit().putString(KEY_USER_EMAIL, value).apply()

    var isFingerprintLockEnabled: Boolean
        get() = prefs.getBoolean(KEY_FINGERPRINT_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_FINGERPRINT_ENABLED, value).apply()

    var isNotificationAccessGranted: Boolean
        get() = prefs.getBoolean(KEY_NOTIFICATION_ACCESS, false)
        set(value) = prefs.edit().putBoolean(KEY_NOTIFICATION_ACCESS, value).apply()

    var isHighContrastBlackEnabled: Boolean
        get() = prefs.getBoolean(KEY_HIGH_CONTRAST, false)
        set(value) = prefs.edit().putBoolean(KEY_HIGH_CONTRAST, value).apply()

    var simFingerprintHash: String?
        get() = prefs.getString(KEY_SIM_FINGERPRINT, null)
        set(value) = prefs.edit().putString(KEY_SIM_FINGERPRINT, value).apply()

    fun clear() {
        prefs.edit().clear().apply()
    }

    companion object {
        private const val KEY_BEARER_TOKEN = "bearer_token"
        private const val KEY_USER_EMAIL = "user_email"
        private const val KEY_FINGERPRINT_ENABLED = "fingerprint_lock_enabled"
        private const val KEY_NOTIFICATION_ACCESS = "notification_access_granted"
        private const val KEY_HIGH_CONTRAST = "high_contrast_black"
        private const val KEY_SIM_FINGERPRINT = "sim_fingerprint_hash"
    }
}
