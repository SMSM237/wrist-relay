package com.sangmin.wristrelay.data

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.concurrent.TimeUnit

fun interface CleanupScheduler {
    fun schedule(nextExpiry: Instant?)
}

class WorkManagerCleanupScheduler(
    context: Context,
    private val clock: Clock = Clock.systemUTC(),
) : CleanupScheduler {
    private val workManager = WorkManager.getInstance(context.applicationContext)

    override fun schedule(nextExpiry: Instant?) {
        if (nextExpiry == null) {
            workManager.cancelUniqueWork(UNIQUE_WORK_NAME)
            return
        }

        val delayMillis = Duration.between(clock.instant(), nextExpiry)
            .toMillis()
            .coerceAtLeast(0L)
        val request = OneTimeWorkRequestBuilder<CleanupWorker>()
            .setInitialDelay(delayMillis, TimeUnit.MILLISECONDS)
            .addTag(UNIQUE_WORK_NAME)
            .build()
        workManager.enqueueUniqueWork(
            UNIQUE_WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            request,
        )
    }

    companion object {
        const val UNIQUE_WORK_NAME = "wrist_relay_sensitive_cleanup"
    }
}

interface EncryptedRepositoryProvider {
    val encryptedRepository: EncryptedRepository
}

class CleanupWorker(
    appContext: Context,
    workerParameters: WorkerParameters,
) : CoroutineWorker(appContext, workerParameters) {
    override suspend fun doWork(): Result {
        val provider = applicationContext as? EncryptedRepositoryProvider
            ?: return Result.failure()
        return try {
            provider.encryptedRepository.purgeExpired()
            Result.success()
        } catch (_: SensitiveDataException) {
            Result.failure()
        } catch (_: Exception) {
            if (runAttemptCount < MAX_RETRY_COUNT) Result.retry() else Result.failure()
        }
    }

    private companion object {
        const val MAX_RETRY_COUNT = 3
    }
}
