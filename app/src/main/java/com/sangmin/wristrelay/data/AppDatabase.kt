package com.sangmin.wristrelay.data

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        CaptureSessionEntity::class,
        CapturedNotificationEntity::class,
        SmartRuleEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun captureDao(): CaptureDao

    abstract fun ruleDao(): RuleDao
}
