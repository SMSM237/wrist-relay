package com.sangmin.wristrelay.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.sangmin.wristrelay.domain.NormalizedNotification
import com.sangmin.wristrelay.domain.SmartRule
import com.sangmin.wristrelay.domain.VibrationPreset
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import javax.crypto.KeyGenerator
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class EncryptedRepositoryTest {
    private lateinit var database: AppDatabase
    private lateinit var crypto: CryptoManager
    private lateinit var clock: MutableClock
    private lateinit var repository: EncryptedRepository
    private val scheduledExpiries = mutableListOf<Instant?>()

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val key = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
        crypto = CryptoManager { key }
        clock = MutableClock(START)
        scheduledExpiries.clear()
        repository = EncryptedRepository(
            database = database,
            crypto = crypto,
            clock = clock,
            cleanupScheduler = CleanupScheduler { expiry ->
                scheduledExpiries.add(expiry)
            },
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun capturedMetadataAndContentAreEncryptedAtRest() = runTest {
        repository.startSession(START)
        val recordId = repository.capture(openDoorEvent(START))

        val stored = checkNotNull(database.captureDao().findRecord(recordId))
        val rawValues = listOf(
            stored.packageNameCiphertext,
            stored.channelIdCiphertext,
            stored.titleCiphertext,
            stored.bodyCiphertext,
        ).map { it.toString(Charsets.UTF_8) }

        assertTrue(rawValues.none { it.contains(WALLET_PACKAGE) })
        assertTrue(rawValues.none { it.contains("digital_key") })
    }

    @Test
    fun captureEligibilitySurvivesRepositoryRecreationAndRejectsOldEvents() = runTest {
        repository.startSession(START)

        assertTrue(repository.canCapture(START))
        assertFalse(repository.canCapture(START.minusSeconds(1)))
        clock.advance(Duration.ofHours(1))
        assertFalse(repository.canCapture(START.plusSeconds(1)))
    }

    @Test
    fun completedSourceRecordExpiresThirtyMinutesAfterSave() = runTest {
        repository.startSession(START)
        val recordId = repository.capture(openDoorEvent(START))
        repository.completeRuleSetup(recordId, openDoorRule(), START)

        clock.advance(Duration.ofMinutes(29).plusSeconds(59))
        assertEquals(1, repository.observeCaptured().first().size)

        clock.advance(Duration.ofSeconds(1))
        assertTrue(repository.observeCaptured().first().isEmpty())
    }

    @Test
    fun emptySessionStillSchedulesCleanupAtOneHour() = runTest {
        repository.startSession(START)

        assertEquals(START.plus(Duration.ofHours(1)), scheduledExpiries.last())
    }

    @Test
    fun expiredRowsAreHiddenBeforePhysicalCleanup() = runTest {
        repository.startSession(START)
        repository.capture(openDoorEvent(START))

        clock.advance(Duration.ofHours(1))

        assertTrue(
            database.captureDao().observeUnexpired(clock.instant().toEpochMilli()).first().isEmpty(),
        )
        assertEquals(1, database.captureDao().countAllRecords())
        repository.purgeExpired()
        assertEquals(0, database.captureDao().countAllRecords())
    }

    @Test
    fun cancellationDeletesSessionRecordsImmediately() = runTest {
        val sessionId = repository.startSession(START)
        repository.capture(openDoorEvent(START))

        repository.cancelSession(sessionId)

        assertTrue(repository.observeCaptured().first().isEmpty())
        assertEquals(0, database.captureDao().countAllRecords())
        assertEquals(0, database.captureDao().countSessions())
    }

    @Test
    fun completingSetupKeepsOnlyTheSelectedSourceRecord() = runTest {
        repository.startSession(START)
        val selectedId = repository.capture(openDoorEvent(START))
        repository.capture(
            openDoorEvent(START).copy(
                notificationKey = "wallet-key-2",
                title = "차량 문이 잠겼습니다",
            ),
        )

        repository.completeRuleSetup(selectedId, openDoorRule(), START)

        assertEquals(1, database.captureDao().countAllRecords())
        assertEquals(selectedId, repository.observeCaptured().first().single().id)
    }

    @Test
    fun rawRoomColumnsNeverContainKnownNotificationText() = runTest {
        repository.startSession(START)
        val recordId = repository.capture(openDoorEvent(START))
        val raw = database.captureDao().findRecord(recordId)!!

        assertFalse(raw.titleCiphertext.containsSubsequence("차량 문이 열렸습니다".encodeToByteArray()))
        assertFalse(raw.bodyCiphertext.containsSubsequence("문이 열렸습니다".encodeToByteArray()))
        assertFalse(raw.titleIv.contentEquals(raw.bodyIv))
    }

    @Test
    fun ciphertextTamperingFailsClosed() {
        val encrypted = crypto.encrypt("rule.title", "rule-1", "차량 문이 열렸습니다")
        val tampered = encrypted.copy(ciphertext = encrypted.ciphertext.clone().also {
            it[it.lastIndex] = (it.last().toInt() xor 0x01).toByte()
        })

        assertThrows(SensitiveDataException::class.java) {
            crypto.decrypt("rule.title", "rule-1", tampered)
        }
        assertThrows(SensitiveDataException::class.java) {
            crypto.decrypt("rule.title", "different-row", encrypted)
        }
    }

    @Test
    fun enabledRulesRoundTripThroughEncryptedColumns() = runTest {
        repository.startSession(START)
        val recordId = repository.capture(openDoorEvent(START))
        val expected = openDoorRule()
        repository.completeRuleSetup(recordId, expected, START)

        assertEquals(listOf(expected), repository.findEnabledRules())
        assertEquals(listOf(expected), repository.observeRules().first())

        val raw = database.ruleDao().findById(expected.id)!!
        assertFalse(raw.nameCiphertext.containsSubsequence(expected.name.encodeToByteArray()))
        assertFalse(raw.titleCiphertext.containsSubsequence(expected.titlePhrase!!.encodeToByteArray()))
    }

    @Test
    fun editingRuleReencryptsFieldsWithoutExtendingCaptureRetention() = runTest {
        repository.startSession(START)
        val recordId = repository.capture(openDoorEvent(START))
        val original = openDoorRule()
        repository.completeRuleSetup(recordId, original, START)
        val expiryBefore = database.captureDao().findRecord(recordId)!!.expiresAtEpochMs
        val createdBefore = database.ruleDao().findById(original.id)!!.createdAtEpochMs

        clock.advance(Duration.ofMinutes(5))
        val updated = original.copy(name = "문 열림 수정", titlePhrase = "잠겼습니다", preset = VibrationPreset.LONG_ONCE)
        repository.updateRule(updated)

        assertEquals(updated, repository.observeRules().first().single())
        assertEquals(expiryBefore, database.captureDao().findRecord(recordId)!!.expiresAtEpochMs)
        val raw = database.ruleDao().findById(original.id)!!
        assertEquals(createdBefore, raw.createdAtEpochMs)
        assertFalse(raw.titleCiphertext.containsSubsequence("잠겼습니다".encodeToByteArray()))
        assertEquals(1, database.ruleDao().countRules())
    }

    private fun openDoorEvent(postedAt: Instant) = NormalizedNotification(
        packageName = WALLET_PACKAGE,
        channelId = "digital_key",
        title = "차량 문이 열렸습니다",
        body = "문이 열렸습니다",
        notificationKey = "wallet-key-1",
        postedAt = postedAt,
    )

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

    private fun ByteArray.containsSubsequence(needle: ByteArray): Boolean = indices.any { start ->
        start + needle.size <= size && needle.indices.all { offset ->
            this[start + offset] == needle[offset]
        }
    }

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
        val START: Instant = Instant.parse("2026-08-15T00:00:00Z")
    }
}
