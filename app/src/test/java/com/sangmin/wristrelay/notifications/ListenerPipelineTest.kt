package com.sangmin.wristrelay.notifications

import android.app.Notification
import android.content.Context
import android.os.UserHandle
import android.service.notification.StatusBarNotification
import androidx.test.core.app.ApplicationProvider
import com.sangmin.wristrelay.domain.NormalizedNotification
import com.sangmin.wristrelay.domain.SmartRule
import com.sangmin.wristrelay.domain.VibrationPreset
import java.time.Instant
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ListenerPipelineTest {
    private val captured = mutableListOf<NormalizedNotification>()
    private val relayed = mutableListOf<Pair<SmartRule, NormalizedNotification>>()
    private var captureActive = false
    private var rules: List<SmartRule> = emptyList()
    private val pipeline = ListenerPipeline(
        selfPackageName = SELF_PACKAGE,
        canCapture = { captureActive },
        capture = { event -> captured.add(event) },
        findEnabledRules = { rules },
        relay = { rule, event -> relayed += rule to event },
    )

    @Test
    fun activeSessionCapturesOnlySupportedFields() = runTest {
        captureActive = true

        pipeline.onNotificationPosted(statusBarNotification())

        assertEquals(1, captured.size)
        assertEquals("차량 문이 열렸습니다", captured.single().title)
        assertEquals("문이 열렸습니다", captured.single().body)
        assertEquals("digital_key", captured.single().channelId)
    }

    @Test
    fun inactiveNonmatchingNotificationIsDiscarded() = runTest {
        rules = listOf(openDoorRule())

        pipeline.onNotificationPosted(
            statusBarNotification(title = "결제가 완료되었습니다", text = "10,000원"),
        )

        assertEquals(0, captured.size)
        assertEquals(0, relayed.size)
    }

    @Test
    fun inactiveMatchingNotificationIsRelayed() = runTest {
        rules = listOf(openDoorRule())

        pipeline.onNotificationPosted(statusBarNotification())

        assertEquals(0, captured.size)
        assertEquals(1, relayed.size)
    }

    @Test
    fun selfOngoingAndGroupSummaryNotificationsAreSuppressed() = runTest {
        captureActive = true
        rules = listOf(openDoorRule())

        pipeline.onNotificationPosted(statusBarNotification(packageName = SELF_PACKAGE, id = 1))
        pipeline.onNotificationPosted(statusBarNotification(id = 2, ongoing = true))
        pipeline.onNotificationPosted(statusBarNotification(id = 3, groupSummary = true))

        assertEquals(0, captured.size)
        assertEquals(0, relayed.size)
    }

    @Test
    fun repeatedUpdateIsSuppressedUntilRemoval() = runTest {
        rules = listOf(openDoorRule())
        val notification = statusBarNotification(id = 42)

        pipeline.onNotificationPosted(notification)
        pipeline.onNotificationPosted(notification)
        assertEquals(1, relayed.size)

        pipeline.onNotificationRemoved(notification.key)
        pipeline.onNotificationPosted(notification)
        assertEquals(2, relayed.size)
    }

    private fun statusBarNotification(
        packageName: String = WALLET_PACKAGE,
        id: Int = 7,
        title: String = "차량 문이 열렸습니다",
        text: String = "문이 열렸습니다",
        ongoing: Boolean = false,
        groupSummary: Boolean = false,
    ): StatusBarNotification {
        val flags = (if (ongoing) Notification.FLAG_ONGOING_EVENT else 0) or
            (if (groupSummary) Notification.FLAG_GROUP_SUMMARY else 0)
        val context = ApplicationProvider.getApplicationContext<Context>()
        val notification = Notification.Builder(context, "digital_key")
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .build()
            .also { it.flags = it.flags or flags }
        return StatusBarNotification(
            packageName,
            packageName,
            id,
            "tag-$id",
            1_000,
            2_000,
            0,
            notification,
            UserHandle.getUserHandleForUid(1_000),
            POSTED_AT.toEpochMilli(),
        )
    }

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
        const val SELF_PACKAGE = "com.sangmin.wristrelay"
        val POSTED_AT: Instant = Instant.parse("2026-08-15T00:00:00Z")
    }
}
