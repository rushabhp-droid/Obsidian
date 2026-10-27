package com.obsidian.mobile.ui.home

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.obsidian.mobile.browser.Browsers
import com.obsidian.mobile.data.ProtectionRepository
import com.obsidian.mobile.data.SessionStore

/** One requirement for "your phone is protected". */
data class ProtectionCheck(val kind: Kind, val title: String, val detail: String, val done: Boolean) {
    enum class Kind { MESSAGES, ALERTS, CONTACTS, FIREWALL, LINKS, LOCK, SERVER }
}

/**
 * The phone is protected when every permission is granted and every protection
 * is on: message checks, alerts, contacts (so known senders' texts stay
 * private), the firewall, link checking as the default browser, the app lock,
 * and a reachable server with its database up.
 */
fun protectionChecks(
    context: Context,
    settings: SessionStore.Settings,
    hasPin: Boolean,
    firewallRunning: Boolean,
    connectivity: ProtectionRepository.Connectivity?,
): List<ProtectionCheck> {
    val listener = NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)
    val alerts = Build.VERSION.SDK_INT < 33 || granted(context, Manifest.permission.POST_NOTIFICATIONS)
    val contacts = granted(context, Manifest.permission.READ_CONTACTS)
    val browser = Browsers.isDefault(context)
    val server = connectivity?.status?.let { it.server == "UP" && it.database == "UP" } ?: false
    return listOf(
        ProtectionCheck(ProtectionCheck.Kind.MESSAGES, "Message checks",
            when {
                !settings.notificationAuditor -> "Turned off in Settings"
                !listener -> "Allow notification access so SMS and WhatsApp links get checked"
                else -> "SMS and WhatsApp links are checked"
            }, settings.notificationAuditor && listener),
        ProtectionCheck(ProtectionCheck.Kind.ALERTS, "Threat alerts",
            if (alerts) "You'll be told about scam messages" else "Allow notifications so we can warn you", alerts),
        ProtectionCheck(ProtectionCheck.Kind.CONTACTS, "Contacts",
            if (contacts) "Texts from people you know stay on your phone" else "Allow contacts so texts from people you know are never sent",
            contacts),
        ProtectionCheck(ProtectionCheck.Kind.FIREWALL, "Firewall",
            if (firewallRunning) "Known scam and malware sites are blocked" else "Turn on to block scam sites in every app",
            firewallRunning),
        ProtectionCheck(ProtectionCheck.Kind.LINKS, "Link checking",
            when {
                !settings.browserDefense -> "Turned off in Settings"
                !browser -> "Make Obsidian your default browser so every link is checked first"
                else -> "Every link you open is checked first"
            }, settings.browserDefense && browser),
        ProtectionCheck(ProtectionCheck.Kind.LOCK, "App lock",
            if (settings.fingerprintLock) "Fingerprint, with a PIN backup" else "PIN", hasPin),
        ProtectionCheck(ProtectionCheck.Kind.SERVER, "Obsidian server",
            when {
                connectivity == null -> "Checking…"
                server -> "Connected"
                connectivity.status != null -> "Database is down"
                else -> connectivity.error ?: "Can't connect"
            }, server),
    )
}

private fun granted(context: Context, permission: String) =
    ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

/** Shown at most once per app run, when the last missing piece falls into place. */
object ProtectedToast {
    @Volatile var shown = false
}
