package com.sangmin.wristrelay.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.sangmin.wristrelay.domain.NormalizedNotification
import java.time.Instant

@Entity(tableName = "capture_sessions")
data class CaptureSessionEntity(
    @PrimaryKey val id: String,
    val startedAtEpochMs: Long,
    val endsAtEpochMs: Long,
    val status: String,
    val cleanupAtEpochMs: Long?,
)

@Entity(
    tableName = "captured_notifications",
    foreignKeys = [
        ForeignKey(
            entity = CaptureSessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index("sessionId"),
        Index("expiresAtEpochMs"),
    ],
)
data class CapturedNotificationEntity(
    @PrimaryKey val id: String,
    val sessionId: String,
    val packageNameCiphertext: ByteArray,
    val packageNameIv: ByteArray,
    val channelIdCiphertext: ByteArray,
    val channelIdIv: ByteArray,
    val notificationKeyHash: String,
    val titleCiphertext: ByteArray,
    val titleIv: ByteArray,
    val bodyCiphertext: ByteArray,
    val bodyIv: ByteArray,
    val cryptoSchemaVersion: Int,
    val postedAtEpochMs: Long,
    val capturedAtEpochMs: Long,
    val expiresAtEpochMs: Long,
)

@Entity(
    tableName = "smart_rules",
    indices = [Index("enabled")],
)
data class SmartRuleEntity(
    @PrimaryKey val id: String,
    val nameCiphertext: ByteArray,
    val nameIv: ByteArray,
    val packageNameCiphertext: ByteArray,
    val packageNameIv: ByteArray,
    val channelIdCiphertext: ByteArray,
    val channelIdIv: ByteArray,
    val useChannel: Boolean,
    val titleCiphertext: ByteArray,
    val titleIv: ByteArray,
    val useTitle: Boolean,
    val bodyCiphertext: ByteArray,
    val bodyIv: ByteArray,
    val useBody: Boolean,
    val cryptoSchemaVersion: Int,
    val preset: String,
    val enabled: Boolean,
    val createdAtEpochMs: Long,
    val updatedAtEpochMs: Long,
)

data class CapturedNotificationRecord(
    val id: String,
    val sessionId: String,
    val notification: NormalizedNotification,
    val capturedAt: Instant,
    val expiresAt: Instant,
)
