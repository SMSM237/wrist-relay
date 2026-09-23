package com.sangmin.wristrelay.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.media.AudioAttributes
import android.media.RingtoneManager
import androidx.core.app.NotificationCompat
import com.sangmin.wristrelay.R
import com.sangmin.wristrelay.domain.NormalizedNotification
import com.sangmin.wristrelay.domain.SmartRule
import com.sangmin.wristrelay.domain.VibrationPreset
import java.util.concurrent.atomic.AtomicInteger

class RelayNotificationPublisher(
    private val context: Context,
    private val notificationIdProvider: () -> Int = DEFAULT_ID_PROVIDER,
) {
    private val notificationManager = context.getSystemService(NotificationManager::class.java)

    fun ensureChannels() {
        val notificationAudio = AudioAttributes.Builder()
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .setUsage(AudioAttributes.USAGE_NOTIFICATION)
            .build()
        val notificationSound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        if (notificationManager.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.relay_channel_name),
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = context.getString(R.string.relay_channel_description)
            enableVibration(true)
            setSound(notificationSound, notificationAudio)
            lockscreenVisibility = NotificationCompat.VISIBILITY_PRIVATE
        }
        notificationManager.createNotificationChannel(channel)
    }

    fun publish(rule: SmartRule, event: NormalizedNotification) {
        ensureChannels()
        val publicVersion = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(context.getColor(R.color.relay_notification_accent))
            .setContentTitle(context.getString(R.string.relay_public_title))
            .setContentText(context.getString(R.string.relay_public_text))
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(context.getColor(R.color.relay_notification_accent))
            .setContentTitle(event.title.ifBlank { rule.name })
            .setContentText(event.body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(event.body))
            .setCategory(NotificationCompat.CATEGORY_EVENT)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(publicVersion)
            .setLocalOnly(false)
            .setOnlyAlertOnce(false)
            .setAutoCancel(true)
            .build()
        notificationManager.notify(notificationIdProvider(), notification)
    }

    companion object {
        private const val CHANNEL_ID = "relay_watch_v3"
        private val notificationSequence = AtomicInteger(10_000)
        private val DEFAULT_ID_PROVIDER: () -> Int = {
            notificationSequence.updateAndGet { current ->
                if (current == Int.MAX_VALUE) 10_000 else current + 1
            }
        }

        // Legacy rules retain their stored preset for database compatibility.
        @Suppress("UNUSED_PARAMETER")
        fun channelId(preset: VibrationPreset): String = CHANNEL_ID
    }
}
