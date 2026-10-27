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

    var userDisplayName: String?
        get() = prefs.getString(KEY_USER_DISPLAY_NAME, "Priya Sharma")
        set(value) = prefs.edit().putString(KEY_USER_DISPLAY_NAME, value).apply()

    var userRole: String
        get() = prefs.getString(KEY_USER_ROLE, "CONSUMER") ?: "CONSUMER"
        set(value) = prefs.edit().putString(KEY_USER_ROLE, value).apply()

    var organizationName: String?
        get() = prefs.getString(KEY_ORG_NAME, null)
        set(value) = prefs.edit().putString(KEY_ORG_NAME, value).apply()

    var serverUrl: String
        get() {
            val defaultUrl = if (isEmulator()) "http://10.0.2.2:8080" else "http://localhost:8080"
            val stored = prefs.getString(KEY_SERVER_URL, null)
            return if (stored == null || (!isEmulator() && stored == "http://10.0.2.2:8080")) {
                defaultUrl
            } else {
                stored
            }
        }
        set(value) = prefs.edit().putString(KEY_SERVER_URL, value).apply()

    private fun isEmulator(): Boolean {
        return android.os.Build.FINGERPRINT.startsWith("generic") ||
                android.os.Build.MODEL.contains("google_sdk") ||
                android.os.Build.MODEL.contains("Emulator") ||
                android.os.Build.HARDWARE.contains("goldfish") ||
                android.os.Build.HARDWARE.contains("ranchu")
    }

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
        val url = serverUrl
        prefs.edit().clear().apply()
        serverUrl = url
    }

    companion object {
        private const val KEY_BEARER_TOKEN = "bearer_token"
        private const val KEY_USER_EMAIL = "user_email"
        private const val KEY_USER_DISPLAY_NAME = "user_display_name"
        private const val KEY_USER_ROLE = "user_role"
        private const val KEY_ORG_NAME = "org_name"
        private const val KEY_SERVER_URL = "server_url"
        private const val KEY_FINGERPRINT_ENABLED = "fingerprint_lock_enabled"
        private const val KEY_NOTIFICATION_ACCESS = "notification_access_granted"
        private const val KEY_HIGH_CONTRAST = "high_contrast_black"
        private const val KEY_SIM_FINGERPRINT = "sim_fingerprint_hash"
    }
}
