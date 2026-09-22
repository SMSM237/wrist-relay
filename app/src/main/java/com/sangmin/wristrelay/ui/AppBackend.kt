package com.sangmin.wristrelay.ui

import com.sangmin.wristrelay.data.ActiveCaptureSession
import com.sangmin.wristrelay.data.CapturedNotificationRecord
import com.sangmin.wristrelay.domain.NormalizedNotification
import com.sangmin.wristrelay.domain.SmartRule
import com.sangmin.wristrelay.notifications.NotificationAccessState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

interface AppBackend {
    val appVersion: String
    val listenerConnected: StateFlow<Boolean>
    val listenerFailure: StateFlow<String?>
    val listenerEvent: StateFlow<ListenerEvent>
    val listenerRebindRequestedAt: StateFlow<Long?>

    fun observeCaptured(): Flow<List<CapturedNotificationRecord>>

    fun observeRules(): Flow<List<SmartRule>>

    fun notificationAccess(): NotificationAccessState

    fun requestListenerReconnect()

    suspend fun checkStorageHealth(): StorageHealth

    suspend fun startCapture(): ActiveCaptureSession

    suspend fun activeCapture(): ActiveCaptureSession?

    suspend fun cancelCapture(sessionId: String)

    suspend fun saveRule(sourceRecordId: String, rule: SmartRule)

    suspend fun updateRule(rule: SmartRule)

    suspend fun setRuleEnabled(ruleId: String, enabled: Boolean)

    suspend fun deleteRule(ruleId: String)

    fun sendWatchTest(rule: SmartRule, event: NormalizedNotification)

    fun recordWatchTestConfirmed()

    fun lastWatchTestConfirmedAt(): Long?
}

enum class ListenerEvent { NEVER, CONNECTED, DISCONNECTED }

data class StorageHealth(
    val databaseReadable: Boolean,
    val cryptoReady: Boolean,
    val databaseFailureType: String? = null,
    val cryptoFailureType: String? = null,
)
