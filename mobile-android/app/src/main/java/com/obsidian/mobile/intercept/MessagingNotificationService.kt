package com.obsidian.mobile.intercept

import android.app.Notification
import android.provider.Telephony
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationCompat
import com.obsidian.mobile.AppGraph
import com.obsidian.mobile.data.AlertStore
import com.obsidian.mobile.network.MessageScanRequest
import com.obsidian.mobile.network.Verdict
import com.obsidian.mobile.notify.ThreatAlertManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.security.MessageDigest

/**
 * The notification auditor. Reads incoming message notifications from WhatsApp,
 * WhatsApp Business and the default SMS app only, and checks their links (and,
 * for unknown senders, their text) with the server. Saved contacts' text never
 * leaves the phone. Nothing is logged.
 */
class MessagingNotificationService : NotificationListenerService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val contacts by lazy { ContactResolver(applicationContext) }
    private val alerts by lazy { ThreatAlertManager(applicationContext) }
    private val seen = object : LinkedHashMap<String, Unit>(MAX_SEEN, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Unit>?) = size > MAX_SEEN
    }

    override fun onListenerConnected() {
        _connected = true
    }

    override fun onListenerDisconnected() {
        _connected = false
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        val notification = sbn?.notification ?: return
        val session = AppGraph.session
        if (!session.settings.value.notificationAuditor || !session.isSignedIn) return
        if (!isWatched(sbn.packageName)) return
        if (notification.flags and Notification.FLAG_GROUP_SUMMARY != 0) return

        for (message in messages(notification)) {
            if (message.text.isBlank()) continue
            // WhatsApp reposts the whole conversation with each new message: skip ones already checked.
            if (!firstTime(sbn.packageName, message)) continue
            val contact = contacts.resolve(message.sender)
            val urls = UrlExtractor.extractUrls(message.text)
            if (contact.isSavedContact && urls.isEmpty()) continue
            val request = MessageScanRequest(
                app = sbn.packageName,
                sender = contact.displayName,
                savedContact = contact.isSavedContact,
                text = if (contact.isSavedContact) null else message.text,
                urls = urls,
            )
            scope.launch { check(request, appLabel(sbn.packageName)) }
        }
    }

    private suspend fun check(request: MessageScanRequest, appLabel: String) {
        val result = runCatching { AppGraph.protection.scanMessage(request) }.getOrNull() ?: return
        val link = result.indicators.firstOrNull { it.verdict == result.verdict }?.value ?: request.urls.firstOrNull()
        when (result.verdict) {
            Verdict.MALICIOUS -> {
                alerts.showThreat(request.sender ?: "Unknown sender", appLabel, link, dangerous = true)
                AppGraph.alerts.add("Scam message blocked", "From ${request.sender} in $appLabel${link?.let { ": $it" } ?: ""}",
                    AlertStore.Severity.DANGER)
            }
            Verdict.SUSPICIOUS -> {
                alerts.showThreat(request.sender ?: "Unknown sender", appLabel, link, dangerous = false)
                AppGraph.alerts.add("Suspicious message", "From ${request.sender} in $appLabel${link?.let { ": $it" } ?: ""}",
                    AlertStore.Severity.WARNING)
            }
            else -> Unit
        }
    }

    private fun isWatched(pkg: String): Boolean =
        pkg == WHATSAPP || pkg == WHATSAPP_BUSINESS || pkg == Telephony.Sms.getDefaultSmsPackage(applicationContext)

    private data class Message(val sender: String, val text: String, val time: Long)

    /** Individual messages from MessagingStyle; the plain title/text only when the app doesn't use it. */
    private fun messages(notification: Notification): List<Message> {
        val extras = notification.extras ?: return emptyList()
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: "Unknown"
        val style = NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(notification)
        if (style != null && style.messages.isNotEmpty()) {
            return style.messages.map {
                Message(it.person?.name?.toString() ?: title, it.text?.toString() ?: "", it.timestamp)
            }
        }
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: return emptyList()
        return listOf(Message(title, text, notification.`when`))
    }

    private fun firstTime(pkg: String, message: Message): Boolean {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest("$pkg\u0000${message.sender}\u0000${message.time}\u0000${message.text}".toByteArray())
        val key = digest.joinToString("") { "%02x".format(it) }
        synchronized(seen) {
            if (seen.containsKey(key)) return false
            seen[key] = Unit
            return true
        }
    }

    private fun appLabel(pkg: String): String = runCatching {
        packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString()
    }.getOrDefault(pkg)

    companion object {
        private const val WHATSAPP = "com.whatsapp"
        private const val WHATSAPP_BUSINESS = "com.whatsapp.w4b"
        private const val MAX_SEEN = 1000

        @Volatile private var _connected = false
        /** True while Android has the listener bound (notification access granted and the service running). */
        val connected: Boolean get() = _connected
    }
}
