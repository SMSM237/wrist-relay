package com.sangmin.wristrelay.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.sangmin.wristrelay.data.ActiveCaptureSession
import com.sangmin.wristrelay.data.CapturedNotificationRecord
import com.sangmin.wristrelay.domain.RuleDraft
import com.sangmin.wristrelay.domain.RuleSuggestionEngine
import com.sangmin.wristrelay.domain.SmartRule
import com.sangmin.wristrelay.domain.VibrationPreset
import com.sangmin.wristrelay.notifications.NotificationAccessState
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class AppScreen { HOME, CAPTURE, EDITOR, RULES, DIAGNOSTICS }

data class Readiness(
    val listenerAccessGranted: Boolean = false,
    val postNotificationsGranted: Boolean = false,
    val appNotificationsEnabled: Boolean = false,
    val blockedPresetChannels: Set<VibrationPreset> = emptySet(),
    val listenerConnected: Boolean = false,
) {
    val ready: Boolean
        get() = listenerAccessGranted && postNotificationsGranted &&
            appNotificationsEnabled && blockedPresetChannels.isEmpty() && listenerConnected
}

data class AppUiState(
    val screen: AppScreen = AppScreen.HOME,
    val readiness: Readiness = Readiness(),
    val rules: List<SmartRule> = emptyList(),
    val captured: List<CapturedNotificationRecord> = emptyList(),
    val activeSession: ActiveCaptureSession? = null,
    val remainingSeconds: Long = 0,
    val selectedRecordId: String? = null,
    val editingRuleId: String? = null,
    val draft: RuleDraft? = null,
    val storageHealth: StorageHealth? = null,
    val listenerEvent: ListenerEvent = ListenerEvent.NEVER,
    val listenerRebindRequestedAt: Long? = null,
    val listenerFailureType: String? = null,
    val watchTestSent: Boolean = false,
    val watchTestCountdownSeconds: Int = 0,
    val watchTestConfirmed: Boolean = false,
    val lastWatchTestConfirmedAt: Long? = null,
    val busy: Boolean = false,
    val message: String? = null,
) {
    val readyToCapture: Boolean
        get() = readiness.ready && storageHealth?.let { it.databaseReadable && it.cryptoReady } == true
}

class AppViewModel(
    private val backend: AppBackend,
    private val clock: Clock = Clock.systemUTC(),
) : ViewModel() {
    private val mutableState = MutableStateFlow(AppUiState())
    val state: StateFlow<AppUiState> = mutableState.asStateFlow()
    private var countdownJob: Job? = null
    private var watchTestJob: Job? = null

    init {
        observeBackend()
        refreshReadiness()
        restoreCapture()
        refreshStorageHealth()
        mutableState.update {
            it.copy(lastWatchTestConfirmedAt = backend.lastWatchTestConfirmedAt())
        }
    }

    fun navigate(screen: AppScreen) {
        if (screen != AppScreen.EDITOR) cancelPendingWatchTest()
        mutableState.update { current -> current.copy(screen = screen, message = null) }
    }

    fun refreshReadiness() {
        val access = backend.notificationAccess()
        if (access.listenerAccessGranted && !backend.listenerConnected.value) backend.requestListenerReconnect()
        applyReadiness(access, backend.listenerConnected.value)
    }

    fun requestListenerReconnect() {
        backend.requestListenerReconnect()
        refreshReadiness()
    }

    fun refreshStorageHealth() = launchAction(showBusy = false) {
        mutableState.update { it.copy(storageHealth = backend.checkStorageHealth()) }
    }

    fun startCapture() = launchAction {
        val access = backend.notificationAccess()
        applyReadiness(access, backend.listenerConnected.value)
        val storage = backend.checkStorageHealth()
        mutableState.update { it.copy(storageHealth = storage) }
        require(storage.databaseReadable && storage.cryptoReady) { "로컬 저장소 검사가 실패했습니다. 진단을 확인해 주세요." }
        require(access.listenerAccessGranted) { "알림 접근 권한을 먼저 허용해 주세요." }
        require(backend.listenerConnected.value) { "알림 감지 서비스가 아직 연결되지 않았습니다. 잠시 후 다시 시도해 주세요." }
        require(access.postNotificationsGranted && access.appNotificationsEnabled) {
            "워치 전달을 위해 앱 알림을 허용해 주세요."
        }
        val session = backend.startCapture()
        mutableState.update {
            it.copy(
                screen = AppScreen.CAPTURE,
                activeSession = session,
                selectedRecordId = null,
                editingRuleId = null,
                draft = null,
                watchTestSent = false,
                watchTestConfirmed = false,
                message = "1시간 감지를 시작했습니다.",
            )
        }
        startCountdown(session)
    }

    fun cancelCapture() = launchAction {
        cancelPendingWatchTest()
        val session = mutableState.value.activeSession ?: return@launchAction
        backend.cancelCapture(session.id)
        countdownJob?.cancel()
        mutableState.update {
            it.copy(
                screen = AppScreen.HOME,
                activeSession = null,
                captured = emptyList(),
                remainingSeconds = 0,
                message = "감지 기록을 즉시 삭제했습니다.",
            )
        }
    }

    fun chooseCaptured(recordId: String) {
        val record = mutableState.value.captured.firstOrNull { it.id == recordId } ?: return
        cancelPendingWatchTest()
        mutableState.update {
            it.copy(
                screen = AppScreen.EDITOR,
                selectedRecordId = recordId,
                editingRuleId = null,
                draft = RuleSuggestionEngine.suggest(record.notification),
                watchTestSent = false,
                watchTestConfirmed = false,
                message = null,
            )
        }
    }

    fun editRule(ruleId: String) {
        val rule = mutableState.value.rules.firstOrNull { it.id == ruleId } ?: return
        cancelPendingWatchTest()
        mutableState.update {
            it.copy(
                screen = AppScreen.EDITOR,
                selectedRecordId = null,
                editingRuleId = ruleId,
                draft = rule.toDraft(),
                watchTestSent = false,
                watchTestConfirmed = false,
                message = null,
            )
        }
    }

    fun updateDraft(transform: (RuleDraft) -> RuleDraft) {
        cancelPendingWatchTest()
        mutableState.update { current ->
            current.copy(
                draft = current.draft?.let(transform),
                watchTestSent = false,
                watchTestConfirmed = false,
            )
        }
    }

    fun sendWatchTest() {
        val current = mutableState.value
        val draft = current.draft ?: return
        val rule = draft.toRule(TEST_RULE_ID)
        val event = if (current.editingRuleId != null) {
            com.sangmin.wristrelay.domain.NormalizedNotification(
                packageName = draft.packageName,
                channelId = draft.channelId,
                title = "Wrist Relay 테스트",
                body = "선택한 알림이 워치에 전달되는지 확인합니다.",
                notificationKey = "rule-edit-test",
                postedAt = clock.instant(),
            )
        } else {
            current.captured.firstOrNull { it.id == current.selectedRecordId }?.notification ?: return
        }
        cancelPendingWatchTest()
        watchTestJob = viewModelScope.launch {
            for (seconds in WATCH_TEST_DELAY_SECONDS downTo 1) {
                mutableState.update {
                    it.copy(watchTestCountdownSeconds = seconds, watchTestSent = false,
                        watchTestConfirmed = false, message = null)
                }
                delay(1_000)
            }
            runCatching { backend.sendWatchTest(rule, event) }
                .onFailure { error ->
                    mutableState.update { it.copy(watchTestCountdownSeconds = 0) }
                    showFailure(error)
                    return@launch
                }
            mutableState.update {
                it.copy(watchTestCountdownSeconds = 0, watchTestSent = true,
                    watchTestConfirmed = false, message = "테스트 알림을 게시했습니다. 워치 수신을 확인해 주세요.")
            }
        }
    }

    private fun cancelPendingWatchTest() {
        watchTestJob?.cancel()
        watchTestJob = null
        mutableState.update { it.copy(watchTestCountdownSeconds = 0) }
    }

    fun confirmWatchTest() {
        if (!mutableState.value.watchTestSent) return
        backend.recordWatchTestConfirmed()
        val confirmedAt = clock.millis()
        mutableState.update {
            it.copy(
                watchTestConfirmed = true,
                lastWatchTestConfirmedAt = confirmedAt,
                message = "워치 전달 테스트 완료 · 사용자 확인",
            )
        }
    }

    fun saveRule() = launchAction {
        val current = mutableState.value
        require(current.watchTestConfirmed) { "워치 알림 전달 테스트를 먼저 확인해 주세요." }
        val draft = requireNotNull(current.draft)
        require(draft.hasUsableCondition()) { "하나 이상의 유효한 스마트 조건을 선택해 주세요." }
        val editingRuleId = current.editingRuleId
        if (editingRuleId != null) {
            val old = current.rules.firstOrNull { it.id == editingRuleId }
                ?: throw IllegalArgumentException("수정할 규칙을 찾지 못했습니다.")
            backend.updateRule(draft.toRule(editingRuleId, old.enabled))
        } else {
            val recordId = requireNotNull(current.selectedRecordId)
            backend.saveRule(recordId, draft.toRule(UUID.randomUUID().toString()))
            countdownJob?.cancel()
        }
        mutableState.update {
            it.copy(
                screen = AppScreen.RULES,
                activeSession = if (editingRuleId == null) null else it.activeSession,
                remainingSeconds = if (editingRuleId == null) 0 else it.remainingSeconds,
                selectedRecordId = null,
                editingRuleId = null,
                draft = null,
                watchTestSent = false,
                watchTestConfirmed = false,
                message = if (editingRuleId == null) "규칙을 저장했습니다. 선택 기록은 30분 후 삭제됩니다." else "규칙을 수정했습니다.",
            )
        }
    }

    fun setRuleEnabled(ruleId: String, enabled: Boolean) = launchAction {
        backend.setRuleEnabled(ruleId, enabled)
    }

    fun deleteRule(ruleId: String) = launchAction {
        backend.deleteRule(ruleId)
        mutableState.update { it.copy(message = "규칙을 삭제했습니다.") }
    }

    fun clearMessage() {
        mutableState.update { it.copy(message = null) }
    }

    fun redactedDiagnostics(): String {
        val current = mutableState.value
        return buildString {
            appendLine("Wrist Relay 진단")
            appendLine("앱 버전: ${backend.appVersion}")
            appendLine("알림 접근: ${current.readiness.listenerAccessGranted}")
            appendLine("알림 게시 권한: ${current.readiness.postNotificationsGranted}")
            appendLine("앱 알림 허용: ${current.readiness.appNotificationsEnabled}")
            appendLine("리스너 연결: ${current.readiness.listenerConnected}")
            appendLine("리스너 마지막 이벤트: ${current.listenerEvent.label()}")
            appendLine("재연결 요청 시각: ${current.listenerRebindRequestedAt?.let(::formatTime) ?: "없음"}")
            appendLine("차단 또는 진동 꺼짐 채널 수: ${current.readiness.blockedPresetChannels.size}")
            appendLine("규칙 수: ${current.rules.size}")
            appendLine("활성 규칙 수: ${current.rules.count { it.enabled }}")
            appendLine("임시 기록 수: ${current.captured.size}")
            appendLine("저장소 읽기: ${current.storageHealth?.databaseReadable ?: "미확인"}")
            appendLine("암호화 왕복 검사: ${current.storageHealth?.cryptoReady ?: "미확인"}")
            appendLine("저장소 읽기 오류 유형: ${current.storageHealth?.let { it.databaseFailureType ?: "없음" } ?: "미확인"}")
            appendLine("암호화 오류 유형: ${current.storageHealth?.let { it.cryptoFailureType ?: "없음" } ?: "미확인"}")
            appendLine("런타임 오류 유형: ${current.listenerFailureType ?: "없음"}")
            append("알림 제목·본문·패키지명은 포함하지 않음")
        }
    }

    private fun observeBackend() {
        viewModelScope.launch {
            backend.observeRules()
                .catch { error -> showFailure(error) }
                .collect { rules -> mutableState.update { it.copy(rules = rules) } }
        }
        viewModelScope.launch {
            backend.observeCaptured()
                .catch { error -> showFailure(error) }
                .collect { captured -> mutableState.update { it.copy(captured = captured) } }
        }
        viewModelScope.launch {
            backend.listenerConnected.collect { connected ->
                val access = backend.notificationAccess()
                applyReadiness(access, connected)
            }
        }
        viewModelScope.launch {
            backend.listenerFailure.collect { error ->
                mutableState.update { it.copy(listenerFailureType = error) }
                if (error != null) mutableState.update { it.copy(message = "알림 처리 오류: $error") }
            }
        }
        viewModelScope.launch {
            backend.listenerEvent.collect { event -> mutableState.update { it.copy(listenerEvent = event) } }
        }
        viewModelScope.launch {
            backend.listenerRebindRequestedAt.collect { epoch ->
                mutableState.update { it.copy(listenerRebindRequestedAt = epoch) }
            }
        }
    }

    private fun restoreCapture() = launchAction(showBusy = false) {
        val active = backend.activeCapture() ?: return@launchAction
        mutableState.update { it.copy(activeSession = active, remainingSeconds = remaining(active)) }
        startCountdown(active)
    }

    private fun startCountdown(session: ActiveCaptureSession) {
        countdownJob?.cancel()
        countdownJob = viewModelScope.launch {
            while (true) {
                val seconds = remaining(session)
                mutableState.update { it.copy(remainingSeconds = seconds) }
                if (seconds == 0L) {
                    mutableState.update {
                        it.copy(activeSession = null, message = "감지 시간이 끝나 임시 기록을 삭제했습니다.")
                    }
                    break
                }
                delay(1_000)
            }
        }
    }

    private fun remaining(session: ActiveCaptureSession): Long =
        Duration.between(clock.instant(), session.endsAt).seconds.coerceAtLeast(0)

    private fun applyReadiness(access: NotificationAccessState, connected: Boolean) {
        mutableState.update {
            it.copy(
                readiness = Readiness(
                    listenerAccessGranted = access.listenerAccessGranted,
                    postNotificationsGranted = access.postNotificationsGranted,
                    appNotificationsEnabled = access.appNotificationsEnabled,
                    blockedPresetChannels = access.blockedPresetChannels,
                    listenerConnected = connected,
                ),
            )
        }
    }

    private fun launchAction(showBusy: Boolean = true, block: suspend () -> Unit) {
        viewModelScope.launch {
            if (showBusy) mutableState.update { it.copy(busy = true, message = null) }
            runCatching { block() }
                .onFailure(::showFailure)
            if (showBusy) mutableState.update { it.copy(busy = false) }
        }
    }

    private fun showFailure(error: Throwable) {
        mutableState.update {
            it.copy(message = error.message ?: "작업을 완료하지 못했습니다.", busy = false)
        }
    }

    companion object {
        private const val TEST_RULE_ID = "preview-rule"
        private const val WATCH_TEST_DELAY_SECONDS = 10

        fun factory(backend: AppBackend): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    AppViewModel(backend) as T
            }
    }
}

private fun RuleDraft.toRule(id: String, enabled: Boolean = true): SmartRule = SmartRule(
    id = id,
    name = name.trim(),
    packageName = packageName,
    channelId = channelId,
    useChannel = useChannel,
    titlePhrase = titlePhrase?.trim(),
    useTitle = useTitle,
    bodyPhrase = bodyPhrase?.trim(),
    useBody = useBody,
    preset = preset,
    enabled = enabled,
)

private fun SmartRule.toDraft(): RuleDraft = RuleDraft(
    name = name,
    packageName = packageName,
    channelId = channelId,
    useChannel = useChannel,
    titlePhrase = titlePhrase,
    useTitle = useTitle,
    bodyPhrase = bodyPhrase,
    useBody = useBody,
    preset = preset,
)

private fun ListenerEvent.label(): String = when (this) {
    ListenerEvent.NEVER -> "이 프로세스에서 연결 콜백 없음"
    ListenerEvent.CONNECTED -> "연결 콜백 수신"
    ListenerEvent.DISCONNECTED -> "연결 해제 또는 서비스 종료 감지"
}

private fun formatTime(epochMillis: Long): String = java.time.format.DateTimeFormatter
    .ofPattern("yyyy.MM.dd HH:mm:ss")
    .withZone(java.time.ZoneId.systemDefault())
    .format(Instant.ofEpochMilli(epochMillis))
