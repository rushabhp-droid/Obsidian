package com.obsidian.mobile.notify

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.obsidian.mobile.MainActivity
import com.obsidian.mobile.ObsidianApplication
import com.obsidian.mobile.R

/** System notifications for threats found in messages. Tapping one opens the Activity tab. */
class ThreatAlertManager(private val context: Context) {

    fun showThreat(sender: String, app: String, link: String?, dangerous: Boolean) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED && android.os.Build.VERSION.SDK_INT >= 33) return
        val open = PendingIntent.getActivity(
            context,
            (link ?: sender).hashCode(),
            Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                .putExtra(MainActivity.EXTRA_TAB, "ACTIVITY"),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val title = if (dangerous) "Scam link from $sender" else "Suspicious link from $sender"
        val body = buildString {
            append(if (dangerous) "Don't open it. " else "Be careful with it. ")
            append("It came in $app")
            link?.let { append(": ").append(it.replace(".", "[.]")) }
        }
        val notification = NotificationCompat.Builder(context, ObsidianApplication.CHANNEL_THREAT_ALERTS)
            .setSmallIcon(R.drawable.ic_shield)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setAutoCancel(true)
            .setContentIntent(open)
            .build()
        NotificationManagerCompat.from(context).notify((link ?: sender).hashCode(), notification)
    }
}
