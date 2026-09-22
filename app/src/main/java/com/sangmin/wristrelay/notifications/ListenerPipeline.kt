package com.sangmin.wristrelay.notifications

import android.app.Notification
import android.service.notification.StatusBarNotification
import com.sangmin.wristrelay.domain.NormalizedNotification
import com.sangmin.wristrelay.domain.NotificationDeduplicator
import com.sangmin.wristrelay.domain.NotificationEnvelope
import com.sangmin.wristrelay.domain.NotificationNormalizer
import com.sangmin.wristrelay.domain.RuleMatcher
import com.sangmin.wristrelay.domain.SmartRule
import java.time.Clock
import java.time.Instant

class ListenerPipeline(
    selfPackageName: String,
    private val canCapture: suspend (Instant) -> Boolean,
    private val capture: suspend (NormalizedNotification) -> Unit,
    private val findEnabledRules: suspend () -> List<SmartRule>,
    private val relay: suspend (SmartRule, NormalizedNotification) -> Unit,
    private val clock: Clock = Clock.systemUTC(),
) {
    private val deduplicator = NotificationDeduplicator(selfPackageName)

    suspend fun onNotificationPosted(statusBarNotification: StatusBarNotification) {
        val envelope = NotificationEventExtractor.extract(statusBarNotification)
        val receivedAt = clock.instant()
        if (!deduplicator.shouldAccept(envelope, receivedAt)) return

        val normalized = NotificationNormalizer.normalize(envelope)
        if (canCapture(normalized.postedAt)) capture(normalized)

        findEnabledRules()
            .firstOrNull { rule -> RuleMatcher.matches(rule, normalized) }
            ?.let { matchingRule -> relay(matchingRule, normalized) }
    }

    fun onNotificationRemoved(notificationKey: String) {
        deduplicator.onRemoved(notificationKey)
    }
}

object NotificationEventExtractor {
    fun extract(statusBarNotification: StatusBarNotification): NotificationEnvelope {
        val notification = statusBarNotification.notification
        val extras = notification.extras
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()
        val body = (
            extras.getCharSequence(Notification.EXTRA_BIG_TEXT)
                ?: extras.getCharSequence(Notification.EXTRA_TEXT)
            )?.toString()

        return NotificationEnvelope(
            packageName = statusBarNotification.packageName,
            channelId = notification.channelId,
            title = title,
            body = body,
            notificationKey = statusBarNotification.key,
            postedAt = Instant.ofEpochMilli(statusBarNotification.postTime),
            isOngoing = notification.flags and Notification.FLAG_ONGOING_EVENT != 0,
            isGroupSummary = notification.flags and Notification.FLAG_GROUP_SUMMARY != 0,
        )
    }
}
