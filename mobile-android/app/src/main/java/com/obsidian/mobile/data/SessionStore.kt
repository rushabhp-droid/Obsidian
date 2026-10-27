package com.obsidian.mobile.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.obsidian.mobile.BuildConfig
import com.obsidian.mobile.network.ConsumerProfile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** Appearance: a palette plus light / dark mode. */
enum class ThemeChoice(val label: String) {
    MATERIAL_YOU("Material You"),
    OBSIDIAN("Obsidian"),
    CATPPUCCIN("Catppuccin"),
    NORD("Nord"),
    DRACULA("Dracula"),
    SOLARIZED("Solarized"),
}

enum class DarkMode(val label: String) { SYSTEM("System"), LIGHT("Light"), DARK("Dark") }

/**
 * Everything the app keeps on the phone, in Keystore-backed encrypted
 * preferences: the bearer token, the server it belongs to, the app PIN hash and
 * the protection switches. There is no unencrypted fallback: if the Keystore
 * entry is unusable the file is recreated (signed out) rather than written in
 * the clear.
 */
class SessionStore(context: Context) {

    private val prefs: SharedPreferences = open(context.applicationContext)
    private val json = Json { ignoreUnknownKeys = true }

    private val _profile = MutableStateFlow(readProfile())
    val profile: StateFlow<ConsumerProfile?> = _profile.asStateFlow()

    private val _settings = MutableStateFlow(readSettings())
    /** Observable copy of the switches the UI and services react to. */
    val settings: StateFlow<Settings> = _settings.asStateFlow()

    data class Settings(
        val serverUrl: String,
        val theme: ThemeChoice,
        val darkMode: DarkMode,
        val browserDefense: Boolean,
        val notificationAuditor: Boolean,
        val firewallWanted: Boolean,
        val fingerprintLock: Boolean,
        val preferredBrowser: String?,
    )

    val token: String? get() = prefs.getString(KEY_TOKEN, null)

    val isSignedIn: Boolean get() = token != null && _profile.value != null

    fun signedIn(token: String, profile: ConsumerProfile) {
        prefs.edit().putString(KEY_TOKEN, token).putString(KEY_PROFILE, json.encodeToString(profile)).apply()
        _profile.value = profile
    }

    fun updateProfile(profile: ConsumerProfile) {
        prefs.edit().putString(KEY_PROFILE, json.encodeToString(profile)).apply()
        _profile.value = profile
    }

    /** Signing out forgets the account, its lock and its switches; the server URL and theme stay. */
    fun signOut() {
        val server = _settings.value.serverUrl
        val theme = _settings.value.theme
        val dark = _settings.value.darkMode
        prefs.edit().clear()
            .putString(KEY_SERVER_URL, server)
            .putString(KEY_THEME, theme.name)
            .putString(KEY_DARK_MODE, dark.name)
            .apply()
        _profile.value = null
        _hasPin.value = false
        _settings.value = readSettings()
    }

    // ---- app lock ---------------------------------------------------------------------------------

    private val _hasPin = MutableStateFlow(prefs.getString(KEY_PIN_HASH, null) != null)
    /** False until the lock setup after sign-in has created the app PIN. */
    val hasPin: StateFlow<Boolean> = _hasPin.asStateFlow()

    fun savePin(hash: String) {
        prefs.edit().putString(KEY_PIN_HASH, hash).putInt(KEY_PIN_FAILURES, 0).putLong(KEY_PIN_LOCKED_UNTIL, 0).apply()
        _hasPin.value = true
    }

    val pinHash: String? get() = prefs.getString(KEY_PIN_HASH, null)

    var pinFailures: Int
        get() = prefs.getInt(KEY_PIN_FAILURES, 0)
        set(value) = prefs.edit().putInt(KEY_PIN_FAILURES, value).apply()

    /** Epoch millis before which the PIN pad stays disabled after repeated wrong PINs. */
    var pinLockedUntil: Long
        get() = prefs.getLong(KEY_PIN_LOCKED_UNTIL, 0)
        set(value) = prefs.edit().putLong(KEY_PIN_LOCKED_UNTIL, value).apply()

    // ---- switches ---------------------------------------------------------------------------------

    fun setServerUrl(url: String) = update { putString(KEY_SERVER_URL, normalizeServerUrl(url)) }
    fun setTheme(theme: ThemeChoice) = update { putString(KEY_THEME, theme.name) }
    fun setDarkMode(mode: DarkMode) = update { putString(KEY_DARK_MODE, mode.name) }
    fun setBrowserDefense(on: Boolean) = update { putBoolean(KEY_BROWSER_DEFENSE, on) }
    fun setNotificationAuditor(on: Boolean) = update { putBoolean(KEY_NOTIFICATION_AUDITOR, on) }
    fun setFirewallWanted(on: Boolean) = update { putBoolean(KEY_FIREWALL, on) }
    fun setFingerprintLock(on: Boolean) = update { putBoolean(KEY_FINGERPRINT, on) }
    fun setPreferredBrowser(pkg: String?) = update { putString(KEY_PREFERRED_BROWSER, pkg) }

    /** [P1] SIM change detection; kept for SimChangeMonitor, which is off until P1 opens. */
    var simFingerprintHash: String?
        get() = prefs.getString(KEY_SIM_FINGERPRINT, null)
        set(value) = prefs.edit().putString(KEY_SIM_FINGERPRINT, value).apply()

    private fun update(change: SharedPreferences.Editor.() -> Unit) {
        prefs.edit().apply(change).apply()
        _settings.value = readSettings()
    }

    private fun readSettings() = Settings(
        serverUrl = prefs.getString(KEY_SERVER_URL, null) ?: BuildConfig.DEFAULT_SERVER_URL,
        theme = enumOr(prefs.getString(KEY_THEME, null), ThemeChoice.OBSIDIAN),
        darkMode = enumOr(prefs.getString(KEY_DARK_MODE, null), DarkMode.SYSTEM),
        browserDefense = prefs.getBoolean(KEY_BROWSER_DEFENSE, true),
        notificationAuditor = prefs.getBoolean(KEY_NOTIFICATION_AUDITOR, true),
        firewallWanted = prefs.getBoolean(KEY_FIREWALL, false),
        fingerprintLock = prefs.getBoolean(KEY_FINGERPRINT, true),
        preferredBrowser = prefs.getString(KEY_PREFERRED_BROWSER, null),
    )

    private fun readProfile(): ConsumerProfile? = prefs.getString(KEY_PROFILE, null)?.let {
        runCatching { json.decodeFromString<ConsumerProfile>(it) }.getOrNull()
    }

    companion object {
        private const val FILE = "obsidian_b2c_secure"
        private const val KEY_TOKEN = "token"
        private const val KEY_PROFILE = "profile"
        private const val KEY_SERVER_URL = "server_url"
        private const val KEY_THEME = "theme"
        private const val KEY_DARK_MODE = "dark_mode"
        private const val KEY_BROWSER_DEFENSE = "browser_defense"
        private const val KEY_NOTIFICATION_AUDITOR = "notification_auditor"
        private const val KEY_FIREWALL = "firewall"
        private const val KEY_FINGERPRINT = "fingerprint_lock"
        private const val KEY_PREFERRED_BROWSER = "preferred_browser"
        private const val KEY_PIN_HASH = "pin_hash"
        private const val KEY_PIN_FAILURES = "pin_failures"
        private const val KEY_PIN_LOCKED_UNTIL = "pin_locked_until"
        private const val KEY_SIM_FINGERPRINT = "sim_fingerprint_hash"

        /** Trims and drops a trailing slash; adds https:// when no scheme was typed. */
        fun normalizeServerUrl(raw: String): String {
            val url = raw.trim().trimEnd('/')
            if (url.isEmpty()) return url
            return if (url.startsWith("http://", true) || url.startsWith("https://", true)) url else "https://$url"
        }

        private inline fun <reified T : Enum<T>> enumOr(name: String?, fallback: T): T =
            name?.let { runCatching { enumValueOf<T>(it) }.getOrNull() } ?: fallback

        private fun open(context: Context): SharedPreferences {
            fun create(): SharedPreferences {
                val key = MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
                return EncryptedSharedPreferences.create(
                    context, FILE, key,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
                )
            }
            return try {
                create()
            } catch (e: Exception) {
                // Keystore key lost (e.g. restored backup): the old file can't be read. Start over, still encrypted.
                context.deleteSharedPreferences(FILE)
                create()
            }
        }
    }
}
