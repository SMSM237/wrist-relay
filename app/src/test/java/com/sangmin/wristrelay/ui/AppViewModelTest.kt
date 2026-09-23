package com.sangmin.wristrelay.ui

import com.sangmin.wristrelay.data.ActiveCaptureSession
import com.sangmin.wristrelay.data.CapturedNotificationRecord
import com.sangmin.wristrelay.domain.NormalizedNotification
import com.sangmin.wristrelay.domain.SmartRule
import com.sangmin.wristrelay.domain.VibrationPreset
import com.sangmin.wristrelay.notifications.NotificationAccessState
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AppViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun captureRuleRequiresUserConfirmedWatchTestBeforeSaving() = runTest(dispatcher) {
        val backend = FakeBackend()
        val viewModel = AppViewModel(backend, FIXED_CLOCK)

        viewModel.startCapture()
        assertEquals(AppScreen.CAPTURE, viewModel.state.value.screen)
        viewModel.chooseCaptured(RECORD.id)
        assertEquals(AppScreen.EDITOR, viewModel.state.value.screen)

        viewModel.saveRule()
        assertTrue(backend.savedRules.isEmpty())
        assertFalse(viewModel.state.value.watchTestConfirmed)

        viewModel.sendWatchTest()
        advanceTimeBy(10_000)
        runCurrent()
        assertEquals(1, backend.watchTests)
        viewModel.confirmWatchTest()
        viewModel.saveRule()

        assertEquals(1, backend.savedRules.size)
        assertEquals(AppScreen.RULES, viewModel.state.value.screen)
        assertTrue(backend.watchConfirmationRecorded)
    }

    @Test
    fun watchTestWaitsTenSecondsSoPhoneCanBeLockedBeforePosting() = runTest(dispatcher) {
        val backend = FakeBackend()
        val viewModel = AppViewModel(backend, FIXED_CLOCK)
        viewModel.startCapture()
        viewModel.chooseCaptured(RECORD.id)

        try {
            viewModel.sendWatchTest()
            assertEquals(0, backend.watchTests)
            assertFalse(viewModel.state.value.watchTestSent)

            advanceTimeBy(9_000)
            runCurrent()
            assertEquals(0, backend.watchTests)

            advanceTimeBy(1_000)
            runCurrent()
            assertEquals(1, backend.watchTests)
            assertTrue(viewModel.state.value.watchTestSent)
        } finally {
            viewModel.cancelCapture()
        }
    }

    @Test
    fun changingRuleWhileTestIsPendingPreventsStaleNotification() = runTest(dispatcher) {
        val backend = FakeBackend()
        val viewModel = AppViewModel(backend, FIXED_CLOCK)
        viewModel.startCapture()
        viewModel.chooseCaptured(RECORD.id)
        try {
            viewModel.sendWatchTest()
            viewModel.updateDraft { it.copy(name = "새 조건") }
            advanceTimeBy(10_000)
            runCurrent()

            assertEquals(0, backend.watchTests)
            assertFalse(viewModel.state.value.watchTestSent)
            assertEquals(0, viewModel.state.value.watchTestCountdownSeconds)
        } finally {
            viewModel.cancelCapture()
        }
    }

    @Test
    fun emptySmartConditionsCannotBeSavedEvenAfterWatchConfirmation() = runTest(dispatcher) {
        val backend = FakeBackend()
        val viewModel = AppViewModel(backend, FIXED_CLOCK)
        viewModel.startCapture()
        viewModel.chooseCaptured(RECORD.id)
        viewModel.updateDraft {
            it.copy(
                channelId = null,
                useChannel = false,
                titlePhrase = "",
                useTitle = true,
                bodyPhrase = "",
                useBody = true,
            )
        }
        viewModel.sendWatchTest()
        advanceTimeBy(10_000)
        runCurrent()
        viewModel.confirmWatchTest()

        viewModel.saveRule()

        assertTrue(backend.savedRules.isEmpty())
        assertTrue(viewModel.state.value.message.orEmpty().contains("조건"))
        viewModel.cancelCapture()
    }

    @Test
    fun diagnosticsNeverContainNotificationContentOrPackageName() {
        val backend = FakeBackend()
        val viewModel = AppViewModel(backend, FIXED_CLOCK)

        val report = viewModel.redactedDiagnostics()

        assertFalse(report.contains("차량 문이 열렸습니다"))
        assertFalse(report.contains("com.samsung.android.spay"))
        assertTrue(report.contains("알림 제목·본문·패키지명은 포함하지 않음"))
    }

    @Test
    fun existingRuleCanBeEditedWithoutCaptureRecordAndKeepsItsId() = runTest(dispatcher) {
        val backend = FakeBackend()
        val viewModel = AppViewModel(backend, FIXED_CLOCK)
        backend.addRule(SAVED_RULE)

        viewModel.editRule(SAVED_RULE.id)
        assertEquals(AppScreen.EDITOR, viewModel.state.value.screen)
        assertEquals(SAVED_RULE.name, viewModel.state.value.draft?.name)
        viewModel.updateDraft { it.copy(name = "수정한 규칙", channelId = "new-channel", preset = VibrationPreset.LONG_ONCE) }
        viewModel.sendWatchTest()
        advanceTimeBy(10_000)
        runCurrent()
        viewModel.confirmWatchTest()
        viewModel.saveRule()

        assertEquals(SAVED_RULE.id, backend.updatedRules.single().id)
        assertEquals("수정한 규칙", backend.updatedRules.single().name)
        assertEquals("new-channel", backend.updatedRules.single().channelId)
        assertEquals(VibrationPreset.LONG_ONCE, backend.updatedRules.single().preset)
        assertTrue(backend.savedRules.isEmpty())
        assertEquals(AppScreen.RULES, viewModel.state.value.screen)
    }

    private class FakeBackend : AppBackend {
        override val appVersion = "0.1.0-test"
        override val listenerConnected = MutableStateFlow(true)
        override val listenerFailure = MutableStateFlow<String?>(null)
        override val listenerEvent = MutableStateFlow(ListenerEvent.CONNECTED)
        override val listenerRebindRequestedAt = MutableStateFlow<Long?>(null)
        private val captured = MutableStateFlow(listOf(RECORD))
        private val rules = MutableStateFlow<List<SmartRule>>(emptyList())
        val savedRules = mutableListOf<SmartRule>()
        val updatedRules = mutableListOf<SmartRule>()
        var watchTests = 0
        var watchConfirmationRecorded = false
        private var session: ActiveCaptureSession? = null

        override fun observeCaptured(): Flow<List<CapturedNotificationRecord>> = captured
        override fun observeRules(): Flow<List<SmartRule>> = rules
        override fun notificationAccess() = NotificationAccessState(
            listenerAccessGranted = true,
            postNotificationsGranted = true,
            appNotificationsEnabled = true,
            blockedPresetChannels = emptySet(),
        )
        override fun requestListenerReconnect() = Unit
        override suspend fun checkStorageHealth() = StorageHealth(true, true)
        fun addRule(rule: SmartRule) { rules.value = rules.value + rule }

        override suspend fun startCapture(): ActiveCaptureSession = ActiveCaptureSession(
            "session",
            NOW,
            NOW.plus(Duration.ofHours(1)),
        ).also { session = it }

        override suspend fun activeCapture(): ActiveCaptureSession? = session
        override suspend fun cancelCapture(sessionId: String) { session = null }
        override suspend fun saveRule(sourceRecordId: String, rule: SmartRule) {
            savedRules += rule
            rules.value = savedRules.toList()
        }
        override suspend fun updateRule(rule: SmartRule) {
            updatedRules += rule
            rules.value = rules.value.map { if (it.id == rule.id) rule else it }
        }
        override suspend fun setRuleEnabled(ruleId: String, enabled: Boolean) = Unit
        override suspend fun deleteRule(ruleId: String) = Unit
        override fun sendWatchTest(rule: SmartRule, event: NormalizedNotification) { watchTests += 1 }
        override fun recordWatchTestConfirmed() { watchConfirmationRecorded = true }
        override fun lastWatchTestConfirmedAt(): Long? = null
    }

    private companion object {
        val NOW: Instant = Instant.parse("2026-08-15T00:00:00Z")
        val FIXED_CLOCK: Clock = Clock.fixed(NOW, ZoneOffset.UTC)
        val RECORD = CapturedNotificationRecord(
            id = "record",
            sessionId = "session",
            notification = NormalizedNotification(
                packageName = "com.samsung.android.spay",
                channelId = "digital_key",
                title = "차량 문이 열렸습니다",
                body = "문이 열렸습니다",
                notificationKey = "hash",
                postedAt = NOW,
            ),
            capturedAt = NOW,
            expiresAt = NOW.plus(Duration.ofHours(1)),
        )
        val SAVED_RULE = SmartRule(
            id = "existing",
            name = "기존 규칙",
            packageName = "com.samsung.android.spay",
            channelId = "digital_key",
            useChannel = true,
            titlePhrase = "차량 문",
            useTitle = true,
            bodyPhrase = null,
            useBody = false,
            preset = VibrationPreset.SHORT_TWICE,
            enabled = true,
        )
    }
}
