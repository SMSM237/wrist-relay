package com.sangmin.wristrelay.domain

import java.time.Clock
import java.text.Normalizer
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Test

class NotificationDomainTest {
    @Test
    fun variableWhitespaceAndUnicodeCompositionDoNotBreakMatch() {
        val event = normalizedEvent(
            title = Normalizer.normalize("차량  문이\n열렸습니다", Normalizer.Form.NFD),
            body = "내 차량의 문이 열렸습니다",
        )
        val rule = openDoorRule(
            titlePhrase = "차량 문이 열렸습니다",
            bodyPhrase = "문이 열렸습니다",
        )

        assertTrue(RuleMatcher.matches(rule, event))
    }

    @Test
    fun differentWalletEventDoesNotMatch() {
        val event = normalizedEvent(
            title = "결제가 완료되었습니다",
            body = "10,000원",
        )

        assertFalse(RuleMatcher.matches(openDoorRule(), event))
    }

    @Test
    fun packageMatchIsMandatoryAndEnabledConditionsUseLogicalAnd() {
        val wrongPackage = normalizedEvent(packageName = "com.example.wallet")
        val wrongChannel = normalizedEvent(channelId = "payments")

        assertFalse(RuleMatcher.matches(openDoorRule(), wrongPackage))
        assertFalse(RuleMatcher.matches(openDoorRule(useChannel = true), wrongChannel))
    }

    @Test
    fun packageOnlyRuleIsRejectedToPreventBroadRelay() {
        val broadRule = openDoorRule(
            useChannel = false,
            titlePhrase = null,
            bodyPhrase = null,
        )

        assertFalse(RuleMatcher.matches(broadRule, normalizedEvent()))
    }

    @Test
    fun suggestionPreservesVariableNumbersForUserReview() {
        val event = normalizedEvent(
            title = "차량 12가 3456 문 열림",
            body = "2026-08-15 09:10",
        )

        val draft = RuleSuggestionEngine.suggest(event)

        assertEquals("차량 12가 3456 문 열림", draft.titlePhrase)
        assertEquals("2026-08-15 09:10", draft.bodyPhrase)
        assertTrue(draft.useTitle)
        assertTrue(draft.useBody)
    }

    @Test
    fun selfOngoingGroupAndRepeatedKeyAreSuppressed() {
        val deduplicator = NotificationDeduplicator(selfPackageName = SELF_PACKAGE)
        val now = START

        assertFalse(deduplicator.shouldAccept(envelope(packageName = SELF_PACKAGE), now))
        assertFalse(deduplicator.shouldAccept(envelope(isOngoing = true), now))
        assertFalse(deduplicator.shouldAccept(envelope(isGroupSummary = true), now))
        assertTrue(deduplicator.shouldAccept(envelope(notificationKey = "key-1"), now))
        assertFalse(
            deduplicator.shouldAccept(
                envelope(notificationKey = "key-1", title = "업데이트된 제목"),
                now.plusSeconds(10),
            ),
        )
    }

    @Test
    fun removedNotificationKeyCanBeAcceptedAsANewInstance() {
        val deduplicator = NotificationDeduplicator(selfPackageName = SELF_PACKAGE)
        val event = envelope(notificationKey = "key-2")

        assertTrue(deduplicator.shouldAccept(event, START))
        deduplicator.onRemoved("key-2")
        assertTrue(deduplicator.shouldAccept(event, START.plusSeconds(1)))
    }

    @Test
    fun repeatedKeyStaysSuppressedUntilRemovalRegardlessOfElapsedTime() {
        val deduplicator = NotificationDeduplicator(selfPackageName = SELF_PACKAGE)
        val event = envelope(notificationKey = "long-lived-key")

        assertTrue(deduplicator.shouldAccept(event, START))
        assertFalse(deduplicator.shouldAccept(event, START.plus(Duration.ofDays(2))))
    }

    @Test
    fun captureStartsAtButtonPressAndExpiresAfterExactlyOneHour() {
        val clock = MutableClock(START)
        val manager = CaptureSessionManager(clock)
        val session = manager.start()

        assertEquals(START, session.startedAt)
        assertFalse(manager.canCapture(START.minusMillis(1)))
        assertTrue(manager.canCapture(START))

        clock.advance(Duration.ofMinutes(59).plusSeconds(59))
        assertTrue(manager.canCapture(clock.instant()))

        clock.advance(Duration.ofSeconds(1))
        assertFalse(manager.canCapture(clock.instant()))
        assertEquals(CaptureStatus.EXPIRED, manager.currentSession()?.status)
    }

    @Test
    fun cancellationStopsCaptureAndDurationCannotExceedOneHour() {
        val manager = CaptureSessionManager(MutableClock(START))
        manager.start()
        manager.cancel()

        assertFalse(manager.canCapture(START.plusSeconds(1)))
        assertEquals(CaptureStatus.CANCELLED, manager.currentSession()?.status)
        assertThrows(IllegalArgumentException::class.java) {
            manager.start(Duration.ofHours(1).plusSeconds(1))
        }
    }

    private fun normalizedEvent(
        packageName: String = WALLET_PACKAGE,
        channelId: String? = "digital_key",
        title: String = "차량 문이 열렸습니다",
        body: String = "문이 열렸습니다",
    ): NormalizedNotification = NotificationNormalizer.normalize(
        envelope(
            packageName = packageName,
            channelId = channelId,
            title = title,
            body = body,
        ),
    )

    private fun envelope(
        packageName: String = WALLET_PACKAGE,
        channelId: String? = "digital_key",
        title: String? = "차량 문이 열렸습니다",
        body: String? = "문이 열렸습니다",
        notificationKey: String = "key-default",
        isOngoing: Boolean = false,
        isGroupSummary: Boolean = false,
    ) = NotificationEnvelope(
        packageName = packageName,
        channelId = channelId,
        title = title,
        body = body,
        notificationKey = notificationKey,
        postedAt = START,
        isOngoing = isOngoing,
        isGroupSummary = isGroupSummary,
    )

    private fun openDoorRule(
        useChannel: Boolean = false,
        titlePhrase: String? = "차량 문이 열렸습니다",
        bodyPhrase: String? = "문이 열렸습니다",
    ) = SmartRule(
        id = "rule-open-door",
        name = "차량 문 열림",
        packageName = WALLET_PACKAGE,
        channelId = "digital_key",
        useChannel = useChannel,
        titlePhrase = titlePhrase,
        useTitle = titlePhrase != null,
        bodyPhrase = bodyPhrase,
        useBody = bodyPhrase != null,
        preset = VibrationPreset.SHORT_TWICE,
        enabled = true,
    )

    private class MutableClock(
        private var current: Instant,
        private val zone: ZoneId = ZoneOffset.UTC,
    ) : Clock() {
        override fun instant(): Instant = current

        override fun getZone(): ZoneId = zone

        override fun withZone(zone: ZoneId): Clock = MutableClock(current, zone)

        fun advance(duration: Duration) {
            current = current.plus(duration)
        }
    }

    private companion object {
        const val WALLET_PACKAGE = "com.samsung.android.spay"
        const val SELF_PACKAGE = "com.sangmin.wristrelay"
        val START: Instant = Instant.parse("2026-08-15T00:00:00Z")
    }
}
