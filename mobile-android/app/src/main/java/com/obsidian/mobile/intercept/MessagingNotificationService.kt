package com.obsidian.mobile.intercept

import android.app.Notification
import android.os.Build
import android.provider.Telephony
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import androidx.core.app.NotificationCompat
import com.obsidian.mobile.auth.TokenStorage
import com.obsidian.mobile.data.MessageRepository
import com.obsidian.mobile.data.RuleRepository
import com.obsidian.mobile.network.ApiClient
import com.obsidian.mobile.network.ChannelType
import com.obsidian.mobile.network.ThreatScanner
import com.obsidian.mobile.notify.ThreatAlertManager

class MessagingNotificationService : NotificationListenerService() {

    private val recentNotificationHashes = LinkedHashSet<String>()
    private val maxCacheSize = 200

    private val contactResolver by lazy { ContactResolver(applicationContext) }
    private val tokenStorage by lazy { TokenStorage(applicationContext) }
    private val apiClient by lazy { ApiClient(tokenStorage) }
    private val threatAlertManager by lazy { ThreatAlertManager(applicationContext) }
    private val threatScanner by lazy {
        ThreatScanner(
            apiClient = apiClient,
            messageRepository = MessageRepository.instance,
            ruleRepository = RuleRepository.instance,
            threatAlertManager = threatAlertManager
        )
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        tokenStorage.isNotificationAccessGranted = true
        Log.i(TAG, "Obsidian NotificationListenerService connected successfully.")
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        tokenStorage.isNotificationAccessGranted = false
        Log.w(TAG, "Obsidian NotificationListenerService disconnected.")
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        if (sbn == null) return

        val packageName = sbn.packageName
        val channel = when {
            packageName == PACKAGE_WHATSAPP || packageName == PACKAGE_WHATSAPP_BUSINESS -> ChannelType.WHATSAPP
            isDefaultSmsPackage(packageName) -> ChannelType.SMS
            else -> return // Ignore non-messaging notifications
        }

        val notification = sbn.notification ?: return
        val extras = notification.extras ?: return

        // 1. Deduplication (WhatsApp frequently reposts identical notifications)
        val notificationKey = "${sbn.id}_${sbn.postTime}_${sbn.tag}"
        synchronized(recentNotificationHashes) {
            if (recentNotificationHashes.contains(notificationKey)) {
                return
            }
            if (recentNotificationHashes.size >= maxCacheSize) {
                recentNotificationHashes.remove(recentNotificationHashes.first())
            }
            recentNotificationHashes.add(notificationKey)
        }

        // 2. Extract messages from MessagingStyle extras (avoids grouped "3 new messages" summary)
        val extractedMessages = extractMessagesFromNotification(notification)

        for ((senderTitle, messageBody) in extractedMessages) {
            if (messageBody.isBlank()) continue

            // 3. Contact List Triage
            val contactInfo = contactResolver.resolveSender(senderTitle)
            val urls = UrlExtractor.extractUrls(messageBody)

            if (contactInfo.isSavedContact) {
                // Saved contact: Ignore plain conversational text for privacy.
                // Only if URL is present, send URLs with no text.
                if (urls.isNotEmpty()) {
                    Log.d(TAG, "Saved contact link scan: $senderTitle -> $urls")
                    threatScanner.processMessage(
                        sender = contactInfo.displayName,
                        isSavedContact = true,
                        channel = channel,
                        text = null,
                        urls = urls
                    )
                }
            } else {
                // Unknown Number (Not in Contacts): Send full message text + any URLs
                Log.d(TAG, "Unknown sender scan: $senderTitle -> text: $messageBody, urls: $urls")
                threatScanner.processMessage(
                    sender = contactInfo.displayName,
                    isSavedContact = false,
                    channel = channel,
                    text = messageBody,
                    urls = urls
                )
            }
        }
    }

    private fun isDefaultSmsPackage(pkg: String): Boolean {
        val defaultSms = Telephony.Sms.getDefaultSmsPackage(applicationContext)
        return pkg == defaultSms || pkg.contains("sms", ignoreCase = true) || pkg.contains("messaging", ignoreCase = true)
    }

    private fun extractMessagesFromNotification(notification: Notification): List<Pair<String, String>> {
        val results = mutableListOf<Pair<String, String>>()
        val extras = notification.extras ?: return results

        // Try extracting using NotificationCompat.MessagingStyle
        val messagingStyle = NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(notification)
        if (messagingStyle != null && messagingStyle.messages.isNotEmpty()) {
            for (msg in messagingStyle.messages) {
                val senderName = msg.person?.name?.toString()
                    ?: extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()
                    ?: "Unknown"
                val text = msg.text?.toString() ?: ""
                results.add(Pair(senderName, text))
            }
            return results
        }

        // Fallback: Check standard title and text
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: "Unknown"
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""
        if (text.isNotBlank()) {
            results.add(Pair(title, text))
        }

        return results
    }

    companion object {
        private const val TAG = "ObsidianNotifService"
        private const val PACKAGE_WHATSAPP = "com.whatsapp"
        private const val PACKAGE_WHATSAPP_BUSINESS = "com.whatsapp.w4b"
    }
}
