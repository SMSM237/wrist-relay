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
        CHANNELS.forEach { definition ->
            if (notificationManager.getNotificationChannel(definition.id) != null) return@forEach
            val channel = NotificationChannel(
                definition.id,
                context.getString(definition.nameResource),
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = context.getString(R.string.relay_channel_description)
                enableVibration(true)
                vibrationPattern = definition.vibrationPattern
                setSound(notificationSound, notificationAudio)
                lockscreenVisibility = NotificationCompat.VISIBILITY_PRIVATE
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    fun publish(rule: SmartRule, event: NormalizedNotification) {
        ensureChannels()
        val publicVersion = NotificationCompat.Builder(context, channelId(rule.preset))
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.relay_public_title))
            .setContentText(context.getString(R.string.relay_public_text))
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()
        val notification = NotificationCompat.Builder(context, channelId(rule.preset))
            .setSmallIcon(R.drawable.ic_notification)
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
        private val notificationSequence = AtomicInteger(10_000)
        private val DEFAULT_ID_PROVIDER: () -> Int = {
            notificationSequence.updateAndGet { current ->
                if (current == Int.MAX_VALUE) 10_000 else current + 1
            }
        }

        private val CHANNELS = listOf(
            ChannelDefinition(
                VibrationPreset.SHORT_ONCE,
                "relay_short_once_v2",
                R.string.relay_channel_short_once,
                longArrayOf(0, 160),
            ),
            ChannelDefinition(
                VibrationPreset.SHORT_TWICE,
                "relay_short_twice_v2",
                R.string.relay_channel_short_twice,
                longArrayOf(0, 140, 120, 140),
            ),
            ChannelDefinition(
                VibrationPreset.LONG_ONCE,
                "relay_long_once_v2",
                R.string.relay_channel_long_once,
                longArrayOf(0, 480),
            ),
            ChannelDefinition(
                VibrationPreset.EMPHASIZED_THREE,
                "relay_emphasis_three_v2",
                R.string.relay_channel_emphasis_three,
                longArrayOf(0, 220, 120, 220, 120, 380),
            ),
        )

        fun channelId(preset: VibrationPreset): String = CHANNELS
            .first { definition -> definition.preset == preset }
            .id
    }
}

private data class ChannelDefinition(
    val preset: VibrationPreset,
    val id: String,
    val nameResource: Int,
    val vibrationPattern: LongArray,
)
