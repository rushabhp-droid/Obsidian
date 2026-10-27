package com.obsidian.mobile

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import androidx.lifecycle.ProcessLifecycleOwner
import com.obsidian.mobile.security.AppLock

class ObsidianApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        AppGraph.init(this)
        ProcessLifecycleOwner.get().lifecycle.addObserver(AppLock)
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_THREAT_ALERTS, getString(R.string.notification_channel_alerts),
                NotificationManager.IMPORTANCE_HIGH).apply {
                description = getString(R.string.notification_channel_alerts_desc)
                enableVibration(true)
            })
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_FIREWALL, getString(R.string.notification_channel_firewall),
                NotificationManager.IMPORTANCE_LOW).apply {
                description = getString(R.string.notification_channel_firewall_desc)
                setShowBadge(false)
            })
    }

    companion object {
        const val CHANNEL_THREAT_ALERTS = "obsidian_threat_alerts"
        const val CHANNEL_FIREWALL = "obsidian_firewall"
    }
}
