package com.sangmin.wristrelay.notifications

import android.app.NotificationManager
import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import com.sangmin.wristrelay.domain.VibrationPreset

data class NotificationAccessState(
    val listenerAccessGranted: Boolean,
    val postNotificationsGranted: Boolean,
    val appNotificationsEnabled: Boolean,
    val blockedPresetChannels: Set<VibrationPreset>,
)

class NotificationAccess(
    private val context: Context,
) {
    fun inspect(): NotificationAccessState {
        val component = ComponentName(context, WristNotificationListenerService::class.java)
        val manager = context.getSystemService(NotificationManager::class.java)
        // A package can host multiple listeners; only this service's grant is relevant.
        val listenerAccess = if (android.os.Build.VERSION.SDK_INT >= 27) {
            manager.isNotificationListenerAccessGranted(component)
        } else {
            val enabled = Settings.Secure.getString(
                context.contentResolver,
                "enabled_notification_listeners",
            ).orEmpty()
            enabled.split(':').any { ComponentName.unflattenFromString(it) == component }
        }
        val channel = manager.getNotificationChannel(
            RelayNotificationPublisher.channelId(VibrationPreset.SHORT_TWICE),
        )
        val blocked = if (channel == null || channel.importance < NotificationManager.IMPORTANCE_DEFAULT ||
            !channel.shouldVibrate()) setOf(VibrationPreset.SHORT_TWICE) else emptySet()
        return NotificationAccessState(
            listenerAccessGranted = listenerAccess,
            postNotificationsGranted = android.os.Build.VERSION.SDK_INT < 33 ||
                context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED,
            appNotificationsEnabled = NotificationManagerCompat.from(context).areNotificationsEnabled(),
            blockedPresetChannels = blocked,
        )
    }

    fun listenerSettingsAction(): String = Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS
}
