package com.sangmin.wristrelay.data

import androidx.room.withTransaction
import com.sangmin.wristrelay.domain.NormalizedNotification
import com.sangmin.wristrelay.domain.SmartRule
import com.sangmin.wristrelay.domain.VibrationPreset
import java.security.MessageDigest
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.Dispatchers

class EncryptedRepository(
    private val database: AppDatabase,
    private val crypto: CryptoManager,
    private val clock: Clock,
    private val cleanupScheduler: CleanupScheduler,
) {
    private val captureDao = database.captureDao()
    private val ruleDao = database.ruleDao()

    suspend fun startSession(now: Instant = clock.instant()): String {
        val sessionId = UUID.randomUUID().toString()
        val endsAt = now.plus(CAPTURE_DURATION)
        database.withTransaction {
            captureDao.deleteExpiredRecords(now.toEpochMilli())
            captureDao.deleteExpiredSessions(now.toEpochMilli())
            captureDao.deleteActiveSessions()
            captureDao.insertSession(
                CaptureSessionEntity(
                    id = sessionId,
                    startedAtEpochMs = now.toEpochMilli(),
                    endsAtEpochMs = endsAt.toEpochMilli(),
                    status = SESSION_ACTIVE,
                    cleanupAtEpochMs = null,
                ),
            )
        }
        scheduleNearestCleanup(now)
        return sessionId
    }

    suspend fun activeSession(now: Instant = clock.instant()): ActiveCaptureSession? =
        captureDao.findActiveSession(now.toEpochMilli())?.let { session ->
            ActiveCaptureSession(
                id = session.id,
                startedAt = Instant.ofEpochMilli(session.startedAtEpochMs),
                endsAt = Instant.ofEpochMilli(session.endsAtEpochMs),
            )
        }

    suspend fun canCapture(
        postedAt: Instant,
        now: Instant = clock.instant(),
    ): Boolean {
        val session = captureDao.findActiveSession(now.toEpochMilli()) ?: return false
        return !postedAt.isBefore(Instant.ofEpochMilli(session.startedAtEpochMs)) &&
            postedAt.isBefore(Instant.ofEpochMilli(session.endsAtEpochMs)) &&
            !postedAt.isAfter(now)
    }

    suspend fun capture(
        notification: NormalizedNotification,
        now: Instant = clock.instant(),
    ): String {
        val session = captureDao.findActiveSession(now.toEpochMilli())
            ?: throw IllegalStateException("No active capture session")
        require(!notification.postedAt.isBefore(Instant.ofEpochMilli(session.startedAtEpochMs))) {
            "Notification predates the active capture session"
        }
        require(notification.postedAt.isBefore(Instant.ofEpochMilli(session.endsAtEpochMs))) {
            "Notification is outside the active capture session"
        }
        require(!notification.postedAt.isAfter(now)) { "Notification post time is in the future" }

        val recordId = UUID.randomUUID().toString()
        val encryptedPackage = crypto.encrypt(CAPTURE_PACKAGE_ENTITY, recordId, notification.packageName)
        val encryptedChannel = crypto.encrypt(CAPTURE_CHANNEL_ENTITY, recordId, notification.channelId.orEmpty())
        val encryptedTitle = crypto.encrypt(CAPTURE_TITLE_ENTITY, recordId, notification.title)
        val encryptedBody = crypto.encrypt(CAPTURE_BODY_ENTITY, recordId, notification.body)
        database.withTransaction {
            captureDao.insertRecord(
                CapturedNotificationEntity(
                    id = recordId,
                    sessionId = session.id,
                    packageNameCiphertext = encryptedPackage.ciphertext,
                    packageNameIv = encryptedPackage.iv,
                    channelIdCiphertext = encryptedChannel.ciphertext,
                    channelIdIv = encryptedChannel.iv,
                    notificationKeyHash = sha256(notification.notificationKey),
                    titleCiphertext = encryptedTitle.ciphertext,
                    titleIv = encryptedTitle.iv,
                    bodyCiphertext = encryptedBody.ciphertext,
                    bodyIv = encryptedBody.iv,
                    cryptoSchemaVersion = CURRENT_CRYPTO_SCHEMA_VERSION,
                    postedAtEpochMs = notification.postedAt.toEpochMilli(),
                    capturedAtEpochMs = now.toEpochMilli(),
                    expiresAtEpochMs = session.endsAtEpochMs,
                ),
            )
            captureDao.trimSessionRecords(session.id, MAX_CAPTURE_RECORDS)
        }
        scheduleNearestCleanup(now)
        return recordId
    }

    suspend fun completeRuleSetup(
        sourceRecordId: String,
        rule: SmartRule,
        now: Instant = clock.instant(),
    ) {
        val sourceRecord = captureDao.findRecord(sourceRecordId)
            ?: throw IllegalArgumentException("Source capture record was not found")
        require(sourceRecord.expiresAtEpochMs > now.toEpochMilli()) {
            "Source capture record has expired"
        }

        val encryptedName = crypto.encrypt(RULE_NAME_ENTITY, rule.id, rule.name)
        val encryptedPackage = crypto.encrypt(RULE_PACKAGE_ENTITY, rule.id, rule.packageName)
        val encryptedChannel = crypto.encrypt(RULE_CHANNEL_ENTITY, rule.id, rule.channelId.orEmpty())
        val encryptedTitle = crypto.encrypt(RULE_TITLE_ENTITY, rule.id, rule.titlePhrase.orEmpty())
        val encryptedBody = crypto.encrypt(RULE_BODY_ENTITY, rule.id, rule.bodyPhrase.orEmpty())
        val cleanupAt = now.plus(SETUP_RECORD_RETENTION)

        database.withTransaction {
            val existingRule = ruleDao.findById(rule.id)
            ruleDao.upsert(
                SmartRuleEntity(
                    id = rule.id,
                    nameCiphertext = encryptedName.ciphertext,
                    nameIv = encryptedName.iv,
                    packageNameCiphertext = encryptedPackage.ciphertext,
                    packageNameIv = encryptedPackage.iv,
                    channelIdCiphertext = encryptedChannel.ciphertext,
                    channelIdIv = encryptedChannel.iv,
                    useChannel = rule.useChannel,
                    titleCiphertext = encryptedTitle.ciphertext,
                    titleIv = encryptedTitle.iv,
                    useTitle = rule.useTitle,
                    bodyCiphertext = encryptedBody.ciphertext,
                    bodyIv = encryptedBody.iv,
                    useBody = rule.useBody,
                    cryptoSchemaVersion = CURRENT_CRYPTO_SCHEMA_VERSION,
                    preset = rule.preset.name,
                    enabled = rule.enabled,
                    createdAtEpochMs = existingRule?.createdAtEpochMs ?: now.toEpochMilli(),
                    updatedAtEpochMs = now.toEpochMilli(),
                ),
            )
            check(captureDao.updateRecordExpiry(sourceRecordId, cleanupAt.toEpochMilli()) == 1) {
                "Source capture record changed during setup"
            }
            captureDao.deleteSessionRecordsExcept(sourceRecord.sessionId, sourceRecordId)
            check(
                captureDao.markSessionCompleted(
                    sourceRecord.sessionId,
                    cleanupAt.toEpochMilli(),
                ) == 1,
            ) { "Capture session changed during setup" }
        }
        scheduleNearestCleanup(now)
    }

    suspend fun cancelSession(sessionId: String) {
        captureDao.deleteSession(sessionId)
        scheduleNearestCleanup(clock.instant())
    }

    fun observeCaptured(): Flow<List<CapturedNotificationRecord>> = flow {
        val now = clock.instant()
        purgeExpired(now)
        emitAll(
            captureDao.observeUnexpired(now.toEpochMilli()).map { rows ->
                rows.map(::decryptCapturedRecord)
            },
        )
    }.flowOn(Dispatchers.Default)

    fun observeRules(): Flow<List<SmartRule>> = flow {
        purgeExpired()
        emitAll(ruleDao.observeAll().map { rows -> rows.map(::decryptRule) })
    }.flowOn(Dispatchers.Default)

    suspend fun findEnabledRules(): List<SmartRule> {
        purgeExpired()
        return ruleDao.findEnabled().map(::decryptRule)
    }

    suspend fun purgeExpired(now: Instant = clock.instant()) {
        database.withTransaction {
            captureDao.deleteExpiredRecords(now.toEpochMilli())
            captureDao.deleteExpiredSessions(now.toEpochMilli())
        }
        scheduleNearestCleanup(now)
    }

    suspend fun deleteRule(ruleId: String) {
        ruleDao.deleteById(ruleId)
    }

    suspend fun updateRule(rule: SmartRule, now: Instant = clock.instant()) {
        require(rule.name.isNotBlank() && (
            (rule.useChannel && !rule.channelId.isNullOrBlank()) ||
                (rule.useTitle && !rule.titlePhrase.isNullOrBlank()) ||
                (rule.useBody && !rule.bodyPhrase.isNullOrBlank())
            )) { "Rule needs a name and condition" }
        val existing = ruleDao.findById(rule.id)
            ?: throw IllegalArgumentException("Rule was not found")
        val name = crypto.encrypt(RULE_NAME_ENTITY, rule.id, rule.name)
        val sourcePackage = crypto.encrypt(RULE_PACKAGE_ENTITY, rule.id, rule.packageName)
        val channel = crypto.encrypt(RULE_CHANNEL_ENTITY, rule.id, rule.channelId.orEmpty())
        val title = crypto.encrypt(RULE_TITLE_ENTITY, rule.id, rule.titlePhrase.orEmpty())
        val body = crypto.encrypt(RULE_BODY_ENTITY, rule.id, rule.bodyPhrase.orEmpty())
        // An edit never needs the original capture record and never changes its expiry.
        ruleDao.upsert(
            SmartRuleEntity(
                id = rule.id,
                nameCiphertext = name.ciphertext,
                nameIv = name.iv,
                packageNameCiphertext = sourcePackage.ciphertext,
                packageNameIv = sourcePackage.iv,
                channelIdCiphertext = channel.ciphertext,
                channelIdIv = channel.iv,
                useChannel = rule.useChannel,
                titleCiphertext = title.ciphertext,
                titleIv = title.iv,
                useTitle = rule.useTitle,
                bodyCiphertext = body.ciphertext,
                bodyIv = body.iv,
                useBody = rule.useBody,
                cryptoSchemaVersion = CURRENT_CRYPTO_SCHEMA_VERSION,
                preset = rule.preset.name,
                enabled = existing.enabled,
                createdAtEpochMs = existing.createdAtEpochMs,
                updatedAtEpochMs = now.toEpochMilli(),
            ),
        )
    }

    suspend fun checkDatabaseReadable(): Boolean = ruleDao.countRules() >= 0

    fun checkCryptoRoundTrip(): Boolean {
        val probe = crypto.encrypt("diagnostic.probe", "in-memory", "ok")
        return crypto.decrypt("diagnostic.probe", "in-memory", probe) == "ok"
    }

    suspend fun setRuleEnabled(
        ruleId: String,
        enabled: Boolean,
        now: Instant = clock.instant(),
    ) {
        require(ruleDao.setEnabled(ruleId, enabled, now.toEpochMilli()) == 1) {
            "Rule was not found"
        }
    }

    private suspend fun scheduleNearestCleanup(now: Instant) {
        val nearest = captureDao.findNearestExpiry(now.toEpochMilli())
            ?.let(Instant::ofEpochMilli)
        cleanupScheduler.schedule(nearest)
    }

    private fun decryptCapturedRecord(row: CapturedNotificationEntity): CapturedNotificationRecord {
        val packageName = crypto.decrypt(
            CAPTURE_PACKAGE_ENTITY,
            row.id,
            EncryptedValue(row.packageNameCiphertext, row.packageNameIv, row.cryptoSchemaVersion),
        )
        val channelId = crypto.decrypt(
            CAPTURE_CHANNEL_ENTITY,
            row.id,
            EncryptedValue(row.channelIdCiphertext, row.channelIdIv, row.cryptoSchemaVersion),
        )
        val title = crypto.decrypt(
            CAPTURE_TITLE_ENTITY,
            row.id,
            EncryptedValue(row.titleCiphertext, row.titleIv, row.cryptoSchemaVersion),
        )
        val body = crypto.decrypt(
            CAPTURE_BODY_ENTITY,
            row.id,
            EncryptedValue(row.bodyCiphertext, row.bodyIv, row.cryptoSchemaVersion),
        )
        return CapturedNotificationRecord(
            id = row.id,
            sessionId = row.sessionId,
            notification = NormalizedNotification(
                packageName = packageName,
                channelId = channelId.takeIf(String::isNotEmpty),
                title = title,
                body = body,
                notificationKey = row.notificationKeyHash,
                postedAt = Instant.ofEpochMilli(row.postedAtEpochMs),
            ),
            capturedAt = Instant.ofEpochMilli(row.capturedAtEpochMs),
            expiresAt = Instant.ofEpochMilli(row.expiresAtEpochMs),
        )
    }

    private fun decryptRule(row: SmartRuleEntity): SmartRule {
        val packageName = crypto.decrypt(
            RULE_PACKAGE_ENTITY,
            row.id,
            EncryptedValue(row.packageNameCiphertext, row.packageNameIv, row.cryptoSchemaVersion),
        )
        val channelId = crypto.decrypt(
            RULE_CHANNEL_ENTITY,
            row.id,
            EncryptedValue(row.channelIdCiphertext, row.channelIdIv, row.cryptoSchemaVersion),
        )
        val name = crypto.decrypt(
            RULE_NAME_ENTITY,
            row.id,
            EncryptedValue(row.nameCiphertext, row.nameIv, row.cryptoSchemaVersion),
        )
        val title = crypto.decrypt(
            RULE_TITLE_ENTITY,
            row.id,
            EncryptedValue(row.titleCiphertext, row.titleIv, row.cryptoSchemaVersion),
        )
        val body = crypto.decrypt(
            RULE_BODY_ENTITY,
            row.id,
            EncryptedValue(row.bodyCiphertext, row.bodyIv, row.cryptoSchemaVersion),
        )
        val preset = runCatching { VibrationPreset.valueOf(row.preset) }
            .getOrElse { throw SensitiveDataException("Stored vibration preset is invalid", it) }

        return SmartRule(
            id = row.id,
            name = name,
            packageName = packageName,
            channelId = channelId.takeIf(String::isNotEmpty),
            useChannel = row.useChannel,
            titlePhrase = title.takeIf { row.useTitle },
            useTitle = row.useTitle,
            bodyPhrase = body.takeIf { row.useBody },
            useBody = row.useBody,
            preset = preset,
            enabled = row.enabled,
        )
    }

    private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.encodeToByteArray())
        .joinToString(separator = "") { byte -> "%02x".format(byte.toInt() and 0xff) }

    private companion object {
        val CAPTURE_DURATION: Duration = Duration.ofHours(1)
        val SETUP_RECORD_RETENTION: Duration = Duration.ofMinutes(30)
        const val SESSION_ACTIVE = "ACTIVE"
        const val CAPTURE_TITLE_ENTITY = "capture.title"
        const val CAPTURE_BODY_ENTITY = "capture.body"
        const val CAPTURE_PACKAGE_ENTITY = "capture.package"
        const val CAPTURE_CHANNEL_ENTITY = "capture.channel"
        const val RULE_NAME_ENTITY = "rule.name"
        const val RULE_TITLE_ENTITY = "rule.title"
        const val RULE_BODY_ENTITY = "rule.body"
        const val RULE_PACKAGE_ENTITY = "rule.package"
        const val RULE_CHANNEL_ENTITY = "rule.channel"
        const val MAX_CAPTURE_RECORDS = 250
    }
}

data class ActiveCaptureSession(
    val id: String,
    val startedAt: Instant,
    val endsAt: Instant,
)
