package com.sangmin.wristrelay.data

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface RuleDao {
    @Upsert
    suspend fun upsert(rule: SmartRuleEntity)

    @Query("SELECT * FROM smart_rules ORDER BY createdAtEpochMs ASC, id ASC")
    fun observeAll(): Flow<List<SmartRuleEntity>>

    @Query("SELECT * FROM smart_rules WHERE enabled = 1 ORDER BY createdAtEpochMs ASC, id ASC")
    suspend fun findEnabled(): List<SmartRuleEntity>

    @Query("SELECT * FROM smart_rules WHERE id = :ruleId LIMIT 1")
    suspend fun findById(ruleId: String): SmartRuleEntity?

    @Query("SELECT COUNT(*) FROM smart_rules")
    suspend fun countRules(): Int

    @Query("DELETE FROM smart_rules WHERE id = :ruleId")
    suspend fun deleteById(ruleId: String): Int

    @Query("UPDATE smart_rules SET enabled = :enabled, updatedAtEpochMs = :updatedAtEpochMs WHERE id = :ruleId")
    suspend fun setEnabled(ruleId: String, enabled: Boolean, updatedAtEpochMs: Long): Int
}
