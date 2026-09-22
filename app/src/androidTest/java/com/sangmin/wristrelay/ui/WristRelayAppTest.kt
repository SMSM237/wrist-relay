package com.sangmin.wristrelay.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.sangmin.wristrelay.domain.SmartRule
import com.sangmin.wristrelay.domain.VibrationPreset
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.assertEquals

class WristRelayAppTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun homeShowsReadinessTruthAndSinglePrimaryCaptureAction() {
        render(
            AppUiState(
                    readiness = Readiness(
                        listenerAccessGranted = true,
                        postNotificationsGranted = true,
                        appNotificationsEnabled = true,
                        listenerConnected = true,
                    ),
                    storageHealth = StorageHealth(true, true),
            ),
        )

        composeRule.onNodeWithTag("home_screen").assertIsDisplayed()
        composeRule.onNodeWithText("Wrist Relay").assertIsDisplayed()
        composeRule.onNodeWithText("  ·  원하는 알림을 손목으로").assertIsDisplayed()
        composeRule.onNodeWithTag("start_capture").assertIsDisplayed()
        composeRule.onNodeWithText("알림 전달 준비됨").assertIsDisplayed()
    }

    @Test
    fun savedRuleOffersEditAction() {
        var editedId: String? = null
        val rule = SmartRule(
            id = "saved-rule",
            name = "차량 문 열림",
            packageName = "com.example.wallet",
            channelId = "digital-key",
            useChannel = true,
            titlePhrase = "문 열림",
            useTitle = true,
            bodyPhrase = null,
            useBody = false,
            preset = VibrationPreset.SHORT_TWICE,
            enabled = true,
        )
        render(AppUiState(screen = AppScreen.RULES, rules = listOf(rule))) { editedId = it }

        composeRule.onNodeWithText("수정").performClick()
        composeRule.runOnIdle { assertEquals(rule.id, editedId) }
    }

    private fun render(state: AppUiState, onEditRule: (String) -> Unit = {}) {
        composeRule.setContent {
            WristRelayApp(
                state = state,
                onNavigate = {},
                onStartCapture = {},
                onCancelCapture = {},
                onChooseCaptured = {},
                onUpdateDraft = {},
                onSendWatchTest = {},
                onConfirmWatchTest = {},
                onSaveRule = {},
                onSetRuleEnabled = { _, _ -> },
                onEditRule = onEditRule,
                onDeleteRule = {},
                onRequestListenerReconnect = {},
                onOpenListenerSettings = {},
                onRequestNotificationPermission = {},
                onOpenNotificationSettings = {},
                onShareDiagnostics = {},
                onDismissMessage = {},
            )
        }
    }
}
