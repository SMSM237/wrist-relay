package com.sangmin.wristrelay

import android.app.Application
import android.content.ComponentName
import android.service.notification.NotificationListenerService
import androidx.room.Room
import androidx.core.content.edit
import com.sangmin.wristrelay.data.ActiveCaptureSession
import com.sangmin.wristrelay.data.AppDatabase
import com.sangmin.wristrelay.data.CapturedNotificationRecord
import com.sangmin.wristrelay.data.CleanupScheduler
import com.sangmin.wristrelay.data.CryptoManager
import com.sangmin.wristrelay.data.EncryptedRepository
import com.sangmin.wristrelay.data.EncryptedRepositoryProvider
import com.sangmin.wristrelay.data.WorkManagerCleanupScheduler
import com.sangmin.wristrelay.domain.NormalizedNotification
import com.sangmin.wristrelay.domain.SmartRule
import com.sangmin.wristrelay.notifications.ListenerPipeline
import com.sangmin.wristrelay.notifications.NotificationAccess
import com.sangmin.wristrelay.notifications.NotificationAccessState
import com.sangmin.wristrelay.notifications.NotificationRuntimeProvider
import com.sangmin.wristrelay.notifications.RelayNotificationPublisher
import com.sangmin.wristrelay.notifications.WristNotificationListenerService
import com.sangmin.wristrelay.ui.AppBackend
import com.sangmin.wristrelay.ui.ListenerEvent
import com.sangmin.wristrelay.ui.StorageHealth
import java.time.Clock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class WristRelayApplication : Application(),
    EncryptedRepositoryProvider,
    NotificationRuntimeProvider,
    AppBackend {

    override val appVersion: String = BuildConfig.VERSION_NAME
    private val clock = Clock.systemUTC()
    private val applicationJob = SupervisorJob()
    override val notificationScope: CoroutineScope = CoroutineScope(applicationJob + Dispatchers.Default)
    private val listenerConnectedMutable = MutableStateFlow(false)
    override val listenerConnected: StateFlow<Boolean> = listenerConnectedMutable
    private val listenerFailureMutable = MutableStateFlow<String?>(null)
    override val listenerFailure: StateFlow<String?> = listenerFailureMutable
    private val listenerEventMutable = MutableStateFlow(ListenerEvent.NEVER)
    override val listenerEvent: StateFlow<ListenerEvent> = listenerEventMutable
    private val listenerRebindRequestedAtMutable = MutableStateFlow<Long?>(null)
    override val listenerRebindRequestedAt: StateFlow<Long?> = listenerRebindRequestedAtMutable

    private val database: AppDatabase by lazy {
        Room.databaseBuilder(this, AppDatabase::class.java, DATABASE_NAME).build()
    }
    private val cleanupScheduler: CleanupScheduler by lazy {
        WorkManagerCleanupScheduler(this, clock)
    }
    override val encryptedRepository: EncryptedRepository by lazy {
        EncryptedRepository(database, CryptoManager(), clock, cleanupScheduler)
    }
    private val publisher by lazy { RelayNotificationPublisher(this) }
    private val access by lazy { NotificationAccess(this) }
    private val preferences by lazy {
        getSharedPreferences(PREFERENCES_NAME, MODE_PRIVATE)
    }

    override val listenerPipeline: ListenerPipeline by lazy {
        ListenerPipeline(
            selfPackageName = packageName,
            canCapture = { postedAt -> encryptedRepository.canCapture(postedAt) },
            capture = { event -> encryptedRepository.capture(event) },
            findEnabledRules = encryptedRepository::findEnabledRules,
            relay = publisher::publish,
            clock = clock,
        )
    }

    override fun onCreate() {
        super.onCreate()
        publisher.ensureChannels()
        if (access.inspect().listenerAccessGranted) {
            requestListenerReconnect()
        }
        notificationScope.launch {
            runCatching { encryptedRepository.purgeExpired() }
                .onFailure(::onListenerFailure)
        }
    }

    override fun onListenerConnectionChanged(connected: Boolean) {
        listenerConnectedMutable.value = connected
        listenerEventMutable.value = if (connected) ListenerEvent.CONNECTED else ListenerEvent.DISCONNECTED
        if (connected) listenerFailureMutable.value = null
    }

    override fun onListenerFailure(error: Throwable) {
        listenerFailureMutable.value = error.javaClass.simpleName.ifBlank { "RuntimeError" }
    }

    override fun observeCaptured(): Flow<List<CapturedNotificationRecord>> =
        encryptedRepository.observeCaptured()

    override fun observeRules(): Flow<List<SmartRule>> = encryptedRepository.observeRules()

    override fun notificationAccess(): NotificationAccessState = access.inspect()

    override fun requestListenerReconnect() {
        if (!access.inspect().listenerAccessGranted || listenerConnectedMutable.value) return
        val now = clock.millis()
        if (listenerRebindRequestedAtMutable.value?.let { now - it < REBIND_COOLDOWN_MS } == true) return
        listenerRebindRequestedAtMutable.value = now
        runCatching {
            NotificationListenerService.requestRebind(
                ComponentName(this, WristNotificationListenerService::class.java),
            )
        }.onFailure(::onListenerFailure)
    }

    override suspend fun checkStorageHealth(): StorageHealth = withContext(Dispatchers.IO) {
        val databaseCheck = runCatching { encryptedRepository.checkDatabaseReadable() }
        val cryptoCheck = runCatching { encryptedRepository.checkCryptoRoundTrip() }
        StorageHealth(
            databaseReadable = databaseCheck.getOrDefault(false),
            cryptoReady = cryptoCheck.getOrDefault(false),
            databaseFailureType = databaseCheck.exceptionOrNull()?.javaClass?.simpleName,
            cryptoFailureType = cryptoCheck.exceptionOrNull()?.javaClass?.simpleName,
        )
    }

    override suspend fun startCapture(): ActiveCaptureSession {
        encryptedRepository.startSession()
        return checkNotNull(encryptedRepository.activeSession())
    }

    override suspend fun activeCapture(): ActiveCaptureSession? = encryptedRepository.activeSession()

    override suspend fun cancelCapture(sessionId: String) = encryptedRepository.cancelSession(sessionId)

    override suspend fun saveRule(sourceRecordId: String, rule: SmartRule) =
        encryptedRepository.completeRuleSetup(sourceRecordId, rule)

    override suspend fun updateRule(rule: SmartRule) = encryptedRepository.updateRule(rule)

    override suspend fun setRuleEnabled(ruleId: String, enabled: Boolean) =
        encryptedRepository.setRuleEnabled(ruleId, enabled)

    override suspend fun deleteRule(ruleId: String) = encryptedRepository.deleteRule(ruleId)

    override fun sendWatchTest(rule: SmartRule, event: NormalizedNotification) {
        publisher.publish(rule, event)
    }

    override fun recordWatchTestConfirmed() {
        preferences.edit { putLong(KEY_LAST_WATCH_TEST, clock.millis()) }
    }

    override fun lastWatchTestConfirmedAt(): Long? = preferences
        .getLong(KEY_LAST_WATCH_TEST, Long.MIN_VALUE)
        .takeUnless { it == Long.MIN_VALUE }

    private companion object {
        const val DATABASE_NAME = "wrist_relay.db"
        const val PREFERENCES_NAME = "wrist_relay_non_sensitive"
        const val KEY_LAST_WATCH_TEST = "last_watch_test_confirmed_at"
        const val REBIND_COOLDOWN_MS = 15_000L
    }
}
