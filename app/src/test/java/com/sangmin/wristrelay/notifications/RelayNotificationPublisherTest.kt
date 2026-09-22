package com.sangmin.wristrelay.notifications

import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.sangmin.wristrelay.domain.NormalizedNotification
import com.sangmin.wristrelay.domain.SmartRule
import com.sangmin.wristrelay.domain.VibrationPreset
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class RelayNotificationPublisherTest {
    private lateinit var manager: NotificationManager
    private lateinit var publisher: RelayNotificationPublisher

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        manager = context.getSystemService(NotificationManager::class.java)
        publisher = RelayNotificationPublisher(context, notificationIdProvider = { 7001 })
    }

    @Test
    fun createsFourStableHighImportanceDistinctChannels() {
        publisher.ensureChannels()

        val channels = VibrationPreset.entries.map { preset ->
            manager.getNotificationChannel(RelayNotificationPublisher.channelId(preset))
        }
        assertTrue(channels.all { it != null })
        assertTrue(channels.all { it!!.importance == NotificationManager.IMPORTANCE_HIGH })
        assertTrue(channels.all { it!!.shouldVibrate() })
        assertTrue(channels.all { it!!.sound != null })
        assertEquals(4, channels.map { it!!.vibrationPattern!!.contentHashCode() }.distinct().size)
        assertEquals("relay_short_once_v2", RelayNotificationPublisher.channelId(VibrationPreset.SHORT_ONCE))
        assertEquals("relay_short_twice_v2", RelayNotificationPublisher.channelId(VibrationPreset.SHORT_TWICE))
        assertEquals("relay_long_once_v2", RelayNotificationPublisher.channelId(VibrationPreset.LONG_ONCE))
        assertEquals(
            "relay_emphasis_three_v2",
            RelayNotificationPublisher.channelId(VibrationPreset.EMPHASIZED_THREE),
        )
    }

    @Test
    fun existingUserChannelSettingsAreNotOverwritten() {
        publisher.ensureChannels()
        val id = RelayNotificationPublisher.channelId(VibrationPreset.SHORT_ONCE)
        val channel = manager.getNotificationChannel(id).apply { enableVibration(false) }
        manager.createNotificationChannel(channel)

        publisher.ensureChannels()

        assertFalse(manager.getNotificationChannel(id).shouldVibrate())
    }

    @Test
    fun relayIsBridgeableAndPublicVersionContainsNoSourceContent() {
        publisher.publish(openDoorRule(), openDoorEvent())

        val notification = manager.activeNotifications.single().notification
        assertEquals(0, notification.flags and Notification.FLAG_LOCAL_ONLY)
        assertEquals(Notification.VISIBILITY_PRIVATE, notification.visibility)
        assertNotNull(notification.publicVersion)
        val publicVersion = requireNotNull(notification.publicVersion)
        val publicText = listOf(
            publicVersion.extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty(),
            publicVersion.extras.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty(),
        ).joinToString(" ")
        assertFalse(publicText.contains("차량"))
        assertFalse(publicText.contains("열렸습니다"))
    }

    private fun openDoorEvent() = NormalizedNotification(
        packageName = WALLET_PACKAGE,
        channelId = "digital_key",
        title = "차량 문이 열렸습니다",
        body = "문이 열렸습니다",
        notificationKey = "key-1",
        postedAt = Instant.parse("2026-08-15T00:00:00Z"),
    )

    private fun openDoorRule() = SmartRule(
        id = "rule-open-door",
        name = "차량 문 열림",
        packageName = WALLET_PACKAGE,
        channelId = "digital_key",
        useChannel = true,
        titlePhrase = "차량 문이 열렸습니다",
        useTitle = true,
        bodyPhrase = "문이 열렸습니다",
        useBody = true,
        preset = VibrationPreset.SHORT_TWICE,
        enabled = true,
    )

    private companion object {
        const val WALLET_PACKAGE = "com.samsung.android.spay"
    }
}
