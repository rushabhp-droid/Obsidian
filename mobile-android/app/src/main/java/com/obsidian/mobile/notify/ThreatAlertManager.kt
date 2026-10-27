package com.obsidian.mobile.notify

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.obsidian.mobile.MainActivity
import com.obsidian.mobile.ObsidianApplication
import com.obsidian.mobile.R

class ThreatAlertManager(private val context: Context) {

    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    fun showMaliciousUrlAlert(
        sender: String,
        url: String,
        threatCategory: String = "Phishing / Scam"
    ) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("OPEN_SCREEN", "message_check")
            putExtra("EXTRA_URL", url)
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            url.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, ObsidianApplication.CHANNEL_THREAT_ALERTS)
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setContentTitle("Obsidian Security: Malicious Link Blocked")
            .setContentText("Link from $sender flagged as $threatCategory: $url")
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText("We blocked a high-risk link sent by $sender before it could open.\nTarget: $url\nThreat: $threatCategory. Tap to review details or report.")
            )
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        notificationManager.notify(url.hashCode(), notification)
    }

    fun showSimChangeAlert(reason: String) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("OPEN_SCREEN", "lock")
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            9999,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, ObsidianApplication.CHANNEL_THREAT_ALERTS)
            .setSmallIcon(android.R.drawable.stat_sys_warning)
            .setContentTitle("Obsidian: SIM Change Detected")
            .setContentText("Security session locked: $reason")
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText("SIM hardware changes or service loss detected. All sensitive threat rules and session tokens have been locked. Biometric re-verification required.")
            )
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        notificationManager.notify(9999, notification)
    }
}
