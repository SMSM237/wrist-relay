@file:Suppress("LongMethod", "MagicNumber")

package com.sangmin.wristrelay.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sangmin.wristrelay.data.CapturedNotificationRecord
import com.sangmin.wristrelay.domain.RuleDraft
import com.sangmin.wristrelay.domain.SmartRule
import com.sangmin.wristrelay.domain.VibrationPreset
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/*
 DIRECTION CONTRACT
 THESIS: Make the capture-to-watch path unmistakable; refuse a generic dashboard of cards.
 OWN-WORLD: Deep navy field, one mint signal seam, flat tonal planes, Wanted Sans.
 STORY: Ready state -> bounded capture -> smart condition -> verified watch pulse -> durable rule.
 FIRST VIEWPORT: Readiness truth, one-hour promise, one primary capture action.
 FORM: Operate mode. Seed: approved-spec-20260815.
 FINISH: Quiet, precise, trustworthy; every state explains the next safe action.
*/

@Composable
fun WristRelayApp(
    state: AppUiState,
    onNavigate: (AppScreen) -> Unit,
    onStartCapture: () -> Unit,
    onCancelCapture: () -> Unit,
    onChooseCaptured: (String) -> Unit,
    onUpdateDraft: ((RuleDraft) -> RuleDraft) -> Unit,
    onSendWatchTest: () -> Unit,
    onConfirmWatchTest: () -> Unit,
    onSaveRule: () -> Unit,
    onSetRuleEnabled: (String, Boolean) -> Unit,
    onEditRule: (String) -> Unit,
    onDeleteRule: (String) -> Unit,
    onRequestListenerReconnect: () -> Unit,
    onOpenListenerSettings: () -> Unit,
    onRequestNotificationPermission: () -> Unit,
    onOpenNotificationSettings: () -> Unit,
    onShareDiagnostics: () -> Unit,
    onDismissMessage: () -> Unit,
) {
    WristRelayTheme {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            bottomBar = {
                if (state.screen in listOf(AppScreen.HOME, AppScreen.RULES, AppScreen.DIAGNOSTICS)) {
                    AppNavigation(state.screen, onNavigate)
                }
            },
        ) { padding ->
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .background(MaterialTheme.colorScheme.background),
            ) {
                when (state.screen) {
                    AppScreen.HOME -> HomeScreen(
                        state,
                        onStartCapture,
                        onOpenListenerSettings,
                        onRequestListenerReconnect,
                        onRequestNotificationPermission,
                        onOpenNotificationSettings,
                        { onNavigate(AppScreen.DIAGNOSTICS) },
                    )
                    AppScreen.CAPTURE -> CaptureScreen(state, onCancelCapture, onChooseCaptured)
                    AppScreen.EDITOR -> RuleEditorScreen(
                        state,
                        onUpdateDraft,
                        onSendWatchTest,
                        onConfirmWatchTest,
                        onSaveRule,
                        onCancel = {
                            onNavigate(when {
                                state.editingRuleId != null -> AppScreen.RULES
                                state.activeSession != null -> AppScreen.CAPTURE
                                else -> AppScreen.HOME
                            })
                        },
                    )
                    AppScreen.RULES -> RulesScreen(state, onSetRuleEnabled, onEditRule, onDeleteRule, onStartCapture)
                    AppScreen.DIAGNOSTICS -> DiagnosticsScreen(
                        state,
                        onOpenListenerSettings,
                        onOpenNotificationSettings,
                        onRequestListenerReconnect,
                        onShareDiagnostics,
                    )
                }
                if (state.busy) {
                    Box(
                        Modifier.fillMaxSize().background(Color(0x99262721)),
                        contentAlignment = Alignment.Center,
                    ) { CircularProgressIndicator(color = MaterialTheme.colorScheme.primary) }
                }
                state.message?.let { message ->
                    MessageBar(message, onDismissMessage, Modifier.align(Alignment.BottomCenter))
                }
            }
        }
    }
}

@Composable
private fun HomeScreen(
    state: AppUiState,
    onStartCapture: () -> Unit,
    onOpenListenerSettings: () -> Unit,
    onRequestListenerReconnect: () -> Unit,
    onRequestNotificationPermission: () -> Unit,
    onOpenNotificationSettings: () -> Unit,
    onOpenDiagnostics: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().statusBarsPadding().testTag("home_screen"),
        contentPadding = PaddingValues(start = 24.dp, end = 24.dp, top = 22.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Wrist Relay", style = MaterialTheme.typography.titleMedium, color = DeepNavy)
                Text("  ·  원하는 알림을 손목으로", style = MaterialTheme.typography.labelMedium, color = Muted)
            }
            Text("알림 전달", style = MaterialTheme.typography.headlineLarge, modifier = Modifier.padding(top = 30.dp))
            Text(
                "감지 버튼을 누른 뒤 1시간 동안 새 알림만 살핍니다.",
                color = Muted,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(top = 10.dp),
            )
        }
        item { SignalPanel(state) }
        item {
            Button(
                onClick = onStartCapture,
                enabled = state.readyToCapture && !state.busy,
                modifier = Modifier.fillMaxWidth().height(58.dp).testTag("start_capture"),
                shape = RoundedCornerShape(18.dp),
            ) {
                Text(if (state.activeSession == null) "1시간 알림 감지" else "감지 화면으로 이동")
            }
        }
        item {
            Text("준비 상태", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(10.dp))
            StatusLine("알림 접근", state.readiness.listenerAccessGranted,
                if (state.readiness.listenerAccessGranted) "이 앱의 알림 접근 권한이 허용됨" else "이 앱의 알림 접근 권한이 허용되지 않음", onOpenListenerSettings)
            StatusLine("알림 게시", state.readiness.postNotificationsGranted,
                if (state.readiness.postNotificationsGranted) "알림 게시 권한이 허용됨" else "알림 게시 권한이 허용되지 않음", onRequestNotificationPermission)
            StatusLine("앱 알림", state.readiness.appNotificationsEnabled,
                if (state.readiness.appNotificationsEnabled) "앱 알림이 허용됨" else "앱 알림이 차단됨", onOpenNotificationSettings)
            StatusLine("감지 서비스", state.readiness.listenerConnected,
                listenerStatusDetail(state),
                if (state.readiness.listenerAccessGranted) onRequestListenerReconnect else onOpenListenerSettings,
                actionLabel = if (state.readiness.listenerAccessGranted) "재연결" else "설정",
                successLabel = "연결")
            if (state.readiness.blockedPresetChannels.isNotEmpty()) {
                StatusLine("진동 채널", false, "진동이 꺼졌거나 조용한 패턴 ${state.readiness.blockedPresetChannels.size}개", onOpenNotificationSettings)
            }
            StatusLine("로컬 저장소", state.storageHealth?.let { it.databaseReadable && it.cryptoReady } == true,
                when {
                    state.storageHealth == null -> "저장소·암호화 검사 중"
                    state.storageHealth.databaseReadable && state.storageHealth.cryptoReady -> "저장소 읽기와 암호화 왕복 검사 통과"
                    else -> "저장소 또는 암호화 검사 실패"
                }, onOpenDiagnostics, actionLabel = "진단", successLabel = "정상")
            PhoneSilentNote()
        }
        state.lastWatchTestConfirmedAt?.let { epoch ->
            item {
                Text("워치 진동 확인 · 사용자가 직접 확인", color = Mint, fontWeight = FontWeight.SemiBold)
                Text(formatInstant(epoch), color = Muted, style = MaterialTheme.typography.bodyMedium)
            }
        }
        item {
            PrivacyNote()
        }
    }
}

@Composable
private fun SignalPanel(state: AppUiState) {
    val active = state.activeSession != null
    Row(
        Modifier.fillMaxWidth().shadow(5.dp, RoundedCornerShape(24.dp)).background(NavySurface, RoundedCornerShape(24.dp)).padding(20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(width = 5.dp, height = 54.dp).background(
            if (state.readyToCapture) Color(0xFF4C7054) else Mint, RoundedCornerShape(3.dp)))
        Column(Modifier.padding(start = 16.dp)) {
            Text(when {
                active -> "알림 감지 중"
                state.readyToCapture -> "알림 전달 준비됨"
                state.storageHealth == null -> "저장소 확인 중"
                !state.storageHealth.databaseReadable || !state.storageHealth.cryptoReady -> "저장소 검사 실패"
                else -> "설정 확인 필요"
            }, style = MaterialTheme.typography.titleLarge)
            Text(
                if (active) formatRemaining(state.remainingSeconds) else "저장된 규칙 ${state.rules.count { it.enabled }}개",
                color = Muted,
            )
        }
    }
}

@Composable
private fun StatusLine(label: String, ok: Boolean, detail: String, onAction: () -> Unit,
                       actionLabel: String = "설정", successLabel: String = "허용") {
    Row(
        Modifier.fillMaxWidth().clickable(enabled = !ok, onClick = onAction).padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(10.dp).background(if (ok) Color(0xFF4C7054) else Danger, CircleShape),
        )
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(label, fontWeight = FontWeight.SemiBold)
            Text(detail, color = Muted, style = MaterialTheme.typography.bodyMedium)
        }
        Text(if (ok) successLabel else actionLabel, color = if (ok) Color(0xFF36563E) else Danger, fontWeight = FontWeight.SemiBold)
    }
}

private fun listenerStatusDetail(state: AppUiState): String = when {
    state.readiness.listenerConnected -> "Android 리스너 연결 콜백을 받음"
    !state.readiness.listenerAccessGranted -> "알림 접근 권한이 없어 연결할 수 없음"
    state.listenerEvent == ListenerEvent.DISCONNECTED -> "연결 해제 또는 서비스 종료 · 재연결 필요"
    state.listenerRebindRequestedAt != null -> "재연결 요청함 · 실제 연결 콜백 대기 중"
    else -> "권한은 허용됐지만 연결 콜백이 아직 없음"
}

@Composable
private fun CaptureScreen(
    state: AppUiState,
    onCancelCapture: () -> Unit,
    onChooseCaptured: (String) -> Unit,
) {
    Column(Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 20.dp)) {
        Row(Modifier.fillMaxWidth().padding(top = 18.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("새 알림 감지 중", style = MaterialTheme.typography.headlineMedium)
                Text(formatRemaining(state.remainingSeconds), color = Mint, style = MaterialTheme.typography.titleLarge)
            }
            TextButton(onClick = onCancelCapture) { Text("취소·삭제", color = Danger) }
        }
        Text(
            "이 버튼을 누른 뒤 생긴 알림만 표시합니다. 같은 상시 알림의 반복 갱신은 한 번만 기록합니다.",
            color = Muted,
            modifier = Modifier.padding(vertical = 16.dp),
        )
        HorizontalDivider(color = NavyRaised)
        if (state.captured.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    SignalDots()
                    Text("알림을 기다리고 있습니다", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 18.dp))
                    Text("자동차 문을 열거나 닫아 디지털키 알림을 발생시켜 보세요.", color = Muted, modifier = Modifier.padding(top = 6.dp))
                }
            }
        } else {
            LazyColumn(contentPadding = PaddingValues(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(state.captured, key = { it.id }) { record -> CapturedRow(record) { onChooseCaptured(record.id) } }
            }
        }
    }
}

@Composable
private fun CapturedRow(record: CapturedNotificationRecord, onClick: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().background(NavySurface, RoundedCornerShape(18.dp)).clickable(onClick = onClick).padding(16.dp),
    ) {
        Text(record.notification.title.ifBlank { "제목 없는 알림" }, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(record.notification.body.ifBlank { "본문 없음" }, color = Muted, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
        Text("이 알림으로 스마트 조건 만들기", color = Mint, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 12.dp))
    }
}

@Composable
private fun RuleEditorScreen(
    state: AppUiState,
    onUpdateDraft: ((RuleDraft) -> RuleDraft) -> Unit,
    onSendWatchTest: () -> Unit,
    onConfirmWatchTest: () -> Unit,
    onSaveRule: () -> Unit,
    onCancel: () -> Unit,
) {
    val draft = state.draft ?: return
    LazyColumn(
        Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            TextButton(onClick = onCancel, contentPadding = PaddingValues(0.dp)) {
                Text(if (state.editingRuleId == null) "감지 목록으로" else "규칙 목록으로")
            }
            Text(if (state.editingRuleId == null) "스마트 조건" else "규칙 수정", style = MaterialTheme.typography.headlineMedium)
            Text("기기 코드가 달라도 제목·본문·채널을 조합해 찾습니다.", color = Muted, modifier = Modifier.padding(top = 6.dp))
        }
        item {
            OutlinedTextField(
                value = draft.name,
                onValueChange = { value -> onUpdateDraft { it.copy(name = value.take(80)) } },
                label = { Text("규칙 이름") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
        }
        item {
            ConditionField(
                label = "알림 채널",
                value = draft.channelId.orEmpty(),
                enabled = draft.useChannel,
                onEnabled = { enabled -> onUpdateDraft { it.copy(useChannel = enabled) } },
                onValue = { value -> onUpdateDraft { it.copy(channelId = value) } },
            )
        }
        item {
            ConditionField(
                label = "제목 포함",
                value = draft.titlePhrase.orEmpty(),
                enabled = draft.useTitle,
                onEnabled = { enabled -> onUpdateDraft { it.copy(useTitle = enabled) } },
                onValue = { value -> onUpdateDraft { it.copy(titlePhrase = value) } },
            )
        }
        item {
            ConditionField(
                label = "본문 포함",
                value = draft.bodyPhrase.orEmpty(),
                enabled = draft.useBody,
                onEnabled = { enabled -> onUpdateDraft { it.copy(useBody = enabled) } },
                onValue = { value -> onUpdateDraft { it.copy(bodyPhrase = value) } },
            )
        }
        item {
            Text("워치 진동", style = MaterialTheme.typography.titleLarge)
            Text("워치는 자체 알림 진동 설정으로 울립니다. 휴대폰 무음은 Galaxy Wearable에서 설정하세요.", color = Muted, modifier = Modifier.padding(top = 4.dp, bottom = 10.dp))
            VibrationPresets(draft.preset) { preset -> onUpdateDraft { it.copy(preset = preset) } }
        }
        item {
            OutlinedButton(onClick = onSendWatchTest, modifier = Modifier.fillMaxWidth().height(54.dp)) {
                Text("이 패턴으로 테스트 알림 보내기")
            }
        }
        if (state.watchTestSent && !state.watchTestConfirmed) {
            item {
                Column(Modifier.fillMaxWidth().border(1.dp, Mint, RoundedCornerShape(18.dp)).padding(16.dp)) {
                    Text("워치에서 진동했나요?", style = MaterialTheme.typography.titleMedium)
                    Button(onClick = onConfirmWatchTest, modifier = Modifier.fillMaxWidth().padding(top = 10.dp)) { Text("네, 확인했습니다") }
                }
            }
        }
        if (state.watchTestConfirmed) {
            item { Text("워치 진동 확인 · 사용자가 직접 확인", color = Mint, fontWeight = FontWeight.SemiBold) }
        }
        item {
            Button(
                onClick = onSaveRule,
                enabled = state.watchTestConfirmed && draft.name.isNotBlank() && draft.hasUsableCondition(),
                modifier = Modifier.fillMaxWidth().height(58.dp),
            ) { Text(if (state.editingRuleId == null) "규칙 저장" else "수정 내용 저장") }
        }
        item { PrivacyNote() }
    }
}

@Composable
private fun ConditionField(
    label: String,
    value: String,
    enabled: Boolean,
    onEnabled: (Boolean) -> Unit,
    onValue: ((String) -> Unit)?,
) {
    Column(Modifier.fillMaxWidth().background(NavySurface, RoundedCornerShape(18.dp)).padding(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
            Switch(
                checked = enabled,
                onCheckedChange = onEnabled,
                modifier = Modifier.semantics {
                    contentDescription = "$label 조건 사용"
                    stateDescription = if (enabled) "사용 중" else "사용 안 함"
                },
            )
        }
        if (onValue != null) {
            OutlinedTextField(value = value, onValueChange = { onValue(it.take(200)) }, enabled = enabled, modifier = Modifier.fillMaxWidth(), singleLine = true)
        } else {
            Text(value.ifBlank { "채널 정보 없음" }, color = Muted, modifier = Modifier.padding(top = 4.dp))
        }
    }
}

@Composable
private fun VibrationPresets(selected: VibrationPreset, onSelect: (VibrationPreset) -> Unit) {
    val labels = listOf(
        VibrationPreset.SHORT_ONCE to "짧게 1회",
        VibrationPreset.SHORT_TWICE to "짧게 2회",
        VibrationPreset.LONG_ONCE to "길게 1회",
        VibrationPreset.EMPHASIZED_THREE to "강조 3회",
    )
    labels.chunked(2).forEach { row ->
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            row.forEach { (preset, label) ->
                OutlinedButton(
                    onClick = { onSelect(preset) },
                    modifier = Modifier.weight(1f).height(50.dp),
                    colors = if (selected == preset) ButtonDefaults.outlinedButtonColors(containerColor = Mint, contentColor = DeepNavy) else ButtonDefaults.outlinedButtonColors(),
                ) { Text(label) }
            }
        }
        Spacer(Modifier.height(10.dp))
    }
}

@Composable
private fun RulesScreen(
    state: AppUiState,
    onSetEnabled: (String, Boolean) -> Unit,
    onEdit: (String) -> Unit,
    onDelete: (String) -> Unit,
    onStartCapture: () -> Unit,
) {
    Column(Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 20.dp)) {
        Text("전달 규칙", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(top = 22.dp))
        Text("조건이 맞을 때만 무음 알림을 게시해 워치로 전달합니다.", color = Muted, modifier = Modifier.padding(top = 6.dp, bottom = 16.dp))
        if (state.rules.isEmpty()) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("아직 규칙이 없습니다", style = MaterialTheme.typography.titleLarge)
                    Button(onClick = onStartCapture, modifier = Modifier.padding(top = 14.dp)) { Text("첫 알림 감지") }
                }
            }
        } else {
            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(state.rules, key = { it.id }) { rule -> RuleRow(rule, onSetEnabled, onEdit, onDelete) }
            }
        }
    }
}

@Composable
private fun RuleRow(rule: SmartRule, onSetEnabled: (String, Boolean) -> Unit, onEdit: (String) -> Unit, onDelete: (String) -> Unit) {
    var confirmDelete by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().background(NavySurface, RoundedCornerShape(18.dp)).padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(rule.name, style = MaterialTheme.typography.titleMedium)
                Text(presetLabel(rule.preset), color = Mint, style = MaterialTheme.typography.labelLarge)
            }
            Switch(
                checked = rule.enabled,
                onCheckedChange = { onSetEnabled(rule.id, it) },
                modifier = Modifier.semantics {
                    contentDescription = "${rule.name} 규칙 사용"
                    stateDescription = if (rule.enabled) "사용 중" else "사용 안 함"
                },
            )
        }
        Text(smartConditionSummary(rule), color = Muted, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            TextButton(onClick = { onEdit(rule.id) }) { Text("수정") }
            TextButton(onClick = { confirmDelete = true }) { Text("삭제", color = Danger) }
        }
    }
    if (confirmDelete) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("규칙을 삭제할까요?") },
            text = { Text("이 알림은 더 이상 워치로 전달되지 않습니다.") },
            confirmButton = { TextButton(onClick = { confirmDelete = false; onDelete(rule.id) }) { Text("삭제", color = Danger) } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("취소") } },
        )
    }
}

@Composable
private fun DiagnosticsScreen(
    state: AppUiState,
    onOpenListenerSettings: () -> Unit,
    onOpenNotificationSettings: () -> Unit,
    onRequestListenerReconnect: () -> Unit,
    onShareDiagnostics: () -> Unit,
) {
    LazyColumn(
        Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Text("진단", style = MaterialTheme.typography.headlineMedium)
            Text("개인 알림 내용은 표시하거나 공유하지 않습니다.", color = Muted, modifier = Modifier.padding(top = 6.dp))
        }
        item {
            DiagnosticLine("알림 접근", if (state.readiness.listenerAccessGranted) "허용" else "차단", state.readiness.listenerAccessGranted)
            DiagnosticLine("알림 게시", if (state.readiness.postNotificationsGranted) "허용" else "차단", state.readiness.postNotificationsGranted)
            DiagnosticLine("앱 알림", if (state.readiness.appNotificationsEnabled) "허용" else "차단", state.readiness.appNotificationsEnabled)
            DiagnosticLine("리스너 연결", if (state.readiness.listenerConnected) "연결됨" else "연결 안 됨", state.readiness.listenerConnected)
            Text(listenerStatusDetail(state), color = Muted, style = MaterialTheme.typography.bodyMedium)
            Text("마지막 이벤트: ${when (state.listenerEvent) {
                ListenerEvent.NEVER -> "이 실행 중 콜백 없음"
                ListenerEvent.CONNECTED -> "연결 콜백"
                ListenerEvent.DISCONNECTED -> "연결 해제 또는 서비스 종료"
            }}", color = Muted, style = MaterialTheme.typography.bodyMedium)
            DiagnosticLine("진동 채널", "진동 불가 ${state.readiness.blockedPresetChannels.size}개", state.readiness.blockedPresetChannels.isEmpty())
            DiagnosticLine("저장소 읽기", state.storageHealth?.databaseReadable?.let { if (it) "검사 통과" else "검사 실패" } ?: "확인 중", state.storageHealth?.databaseReadable)
            DiagnosticLine("암호화 왕복", state.storageHealth?.cryptoReady?.let { if (it) "검사 통과" else "검사 실패" } ?: "확인 중", state.storageHealth?.cryptoReady)
            state.storageHealth?.databaseFailureType?.let { Text("저장소 읽기 오류: $it", color = Danger) }
            state.storageHealth?.cryptoFailureType?.let { Text("암호화 오류: $it", color = Danger) }
            state.listenerFailureType?.let { Text("리스너 오류: $it", color = Danger) }
            DiagnosticLine("저장된 규칙", "${state.rules.size}개", null)
            DiagnosticLine("임시 기록", "${state.captured.size}개", null)
        }
        item {
            OutlinedButton(onClick = onOpenListenerSettings, modifier = Modifier.fillMaxWidth()) { Text("알림 접근 설정") }
            OutlinedButton(onClick = onRequestListenerReconnect, enabled = state.readiness.listenerAccessGranted && !state.readiness.listenerConnected,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) { Text("감지 서비스 재연결 요청") }
            OutlinedButton(onClick = onOpenNotificationSettings, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) { Text("앱 알림·채널 설정") }
        }
        item {
            Button(onClick = onShareDiagnostics, modifier = Modifier.fillMaxWidth().height(54.dp)) { Text("개인정보 제외 진단 공유") }
        }
        item { PrivacyNote() }
    }
}

@Composable
private fun DiagnosticLine(label: String, value: String, ok: Boolean?) {
    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f))
        Text(value, color = when (ok) { true -> Color(0xFF36563E); false -> Danger; null -> Muted }, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun AppNavigation(selected: AppScreen, onNavigate: (AppScreen) -> Unit) {
    Row(
        Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 20.dp, vertical = 12.dp)
            .shadow(12.dp, RoundedCornerShape(30.dp))
            .background(DeepNavy, RoundedCornerShape(30.dp)).padding(6.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        listOf(AppScreen.HOME to "홈", AppScreen.RULES to "규칙", AppScreen.DIAGNOSTICS to "진단").forEach { (screen, label) ->
            val active = selected == screen
            Column(
                Modifier.weight(1f).background(if (active) Color(0xFF45453E) else Color.Transparent, RoundedCornerShape(24.dp))
                    .clickable { onNavigate(screen) }.padding(vertical = 14.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(label, color = if (active) Color.White else Color(0xFFD0CDC5), style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

@Composable
private fun NavGlyph(active: Boolean) {
    Canvas(Modifier.size(22.dp)) {
        val color = if (active) Mint else Muted
        drawCircle(color.copy(alpha = if (active) 0.22f else 0.08f), size.minDimension / 2)
        drawCircle(color, size.minDimension / 5)
    }
}

@Composable
private fun SignalDots() {
    Canvas(Modifier.size(68.dp)) {
        drawCircle(Mint.copy(alpha = 0.10f), radius = 34.dp.toPx())
        drawCircle(Mint.copy(alpha = 0.25f), radius = 22.dp.toPx())
        drawCircle(Mint, radius = 8.dp.toPx())
    }
}

@Composable
private fun PrivacyNote() {
    Column(Modifier.fillMaxWidth().background(NavySurface, RoundedCornerShape(18.dp)).padding(16.dp)) {
        Text("최소 기록", color = Mint, fontWeight = FontWeight.SemiBold)
        Text("감지 중에만 로컬 암호화 저장합니다. 규칙 설정 뒤 선택 기록은 30분 후, 나머지는 즉시 삭제됩니다.", color = Muted, modifier = Modifier.padding(top = 4.dp))
    }
}

@Composable
private fun PhoneSilentNote() {
    Column(Modifier.fillMaxWidth().background(NavySurface, RoundedCornerShape(18.dp)).padding(16.dp)) {
        Text("휴대폰은 조용히", color = Mint, fontWeight = FontWeight.SemiBold)
        Text(
            "Galaxy Wearable › 워치 설정 › 알림에서 ‘휴대전화 알림 무음’을 켜면 착용 중에는 워치만 울립니다.",
            color = Muted,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

@Composable
private fun MessageBar(message: String, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier.padding(16.dp).background(Mist, RoundedCornerShape(16.dp)).clickable(onClick = onDismiss).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(message, color = DeepNavy, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Text("닫기", color = MintDark, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 10.dp))
    }
}

private fun formatRemaining(totalSeconds: Long): String {
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return "%02d:%02d:%02d 남음".format(hours, minutes, seconds)
}

private fun formatInstant(epochMillis: Long): String = DateTimeFormatter.ofPattern("yyyy.MM.dd HH:mm")
    .withZone(ZoneId.systemDefault())
    .format(Instant.ofEpochMilli(epochMillis))

private fun presetLabel(preset: VibrationPreset): String = when (preset) {
    VibrationPreset.SHORT_ONCE -> "짧게 1회"
    VibrationPreset.SHORT_TWICE -> "짧게 2회"
    VibrationPreset.LONG_ONCE -> "길게 1회"
    VibrationPreset.EMPHASIZED_THREE -> "강조 3회"
}

private fun smartConditionSummary(rule: SmartRule): String = buildList {
    if (rule.useChannel) add("채널 일치")
    if (rule.useTitle) add("제목 ‘${rule.titlePhrase.orEmpty()}’")
    if (rule.useBody) add("본문 ‘${rule.bodyPhrase.orEmpty()}’")
}.joinToString(" · ")
