package com.sangmin.wristrelay.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface CaptureDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertSession(session: CaptureSessionEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertRecord(record: CapturedNotificationEntity)

    @Query(
        """
        DELETE FROM captured_notifications
        WHERE sessionId = :sessionId
          AND id NOT IN (
              SELECT id FROM captured_notifications
              WHERE sessionId = :sessionId
              ORDER BY capturedAtEpochMs DESC, id DESC
              LIMIT :maximumRecords
          )
        """,
    )
    suspend fun trimSessionRecords(sessionId: String, maximumRecords: Int): Int

    @Query(
        """
        SELECT * FROM capture_sessions
        WHERE status = 'ACTIVE'
          AND startedAtEpochMs <= :nowEpochMs
          AND endsAtEpochMs > :nowEpochMs
        ORDER BY startedAtEpochMs DESC
        LIMIT 1
        """,
    )
    suspend fun findActiveSession(nowEpochMs: Long): CaptureSessionEntity?

    @Query("SELECT * FROM captured_notifications WHERE id = :recordId LIMIT 1")
    suspend fun findRecord(recordId: String): CapturedNotificationEntity?

    @Query(
        """
        SELECT * FROM captured_notifications
        WHERE expiresAtEpochMs > :nowEpochMs
        ORDER BY capturedAtEpochMs DESC, id ASC
        """,
    )
    fun observeUnexpired(nowEpochMs: Long): Flow<List<CapturedNotificationEntity>>

    @Query("UPDATE captured_notifications SET expiresAtEpochMs = :expiresAtEpochMs WHERE id = :recordId")
    suspend fun updateRecordExpiry(recordId: String, expiresAtEpochMs: Long): Int

    @Query("DELETE FROM captured_notifications WHERE sessionId = :sessionId AND id != :recordIdToKeep")
    suspend fun deleteSessionRecordsExcept(sessionId: String, recordIdToKeep: String): Int

    @Query(
        """
        UPDATE capture_sessions
        SET status = 'COMPLETED', cleanupAtEpochMs = :cleanupAtEpochMs
        WHERE id = :sessionId
        """,
    )
    suspend fun markSessionCompleted(sessionId: String, cleanupAtEpochMs: Long): Int

    @Query("DELETE FROM capture_sessions WHERE id = :sessionId")
    suspend fun deleteSession(sessionId: String): Int

    @Query("DELETE FROM capture_sessions WHERE status = 'ACTIVE'")
    suspend fun deleteActiveSessions(): Int

    @Query("DELETE FROM captured_notifications WHERE expiresAtEpochMs <= :nowEpochMs")
    suspend fun deleteExpiredRecords(nowEpochMs: Long): Int

    @Query(
        """
        DELETE FROM capture_sessions
        WHERE (status = 'ACTIVE' AND endsAtEpochMs <= :nowEpochMs)
           OR (cleanupAtEpochMs IS NOT NULL AND cleanupAtEpochMs <= :nowEpochMs)
        """,
    )
    suspend fun deleteExpiredSessions(nowEpochMs: Long): Int

    @Query(
        """
        SELECT MIN(expiryEpochMs)
        FROM (
            SELECT expiresAtEpochMs AS expiryEpochMs
            FROM captured_notifications
            WHERE expiresAtEpochMs > :nowEpochMs
            UNION ALL
            SELECT endsAtEpochMs AS expiryEpochMs
            FROM capture_sessions
            WHERE status = 'ACTIVE' AND endsAtEpochMs > :nowEpochMs
            UNION ALL
            SELECT cleanupAtEpochMs AS expiryEpochMs
            FROM capture_sessions
            WHERE cleanupAtEpochMs IS NOT NULL AND cleanupAtEpochMs > :nowEpochMs
        )
        """,
    )
    suspend fun findNearestExpiry(nowEpochMs: Long): Long?

    @Query("SELECT COUNT(*) FROM captured_notifications")
    suspend fun countAllRecords(): Int

    @Query("SELECT COUNT(*) FROM capture_sessions")
    suspend fun countSessions(): Int
}
