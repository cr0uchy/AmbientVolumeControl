package com.ambientvolumecontrol

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build

class AmbientVolumeApp : Application() {

    companion object {
        const val NOTIFICATION_CHANNEL_ID = "ambient_monitoring"
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            NOTIFICATION_CHANNEL_ID,
            getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = getString(R.string.notification_channel_description)
            setShowBadge(false)
        }
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }
}
