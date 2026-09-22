package com.sangmin.wristrelay.domain

import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.UUID

class CaptureSessionManager(
    private val clock: Clock,
) {
    private var session: CaptureSession? = null

    @Synchronized
    fun start(duration: Duration = MAX_DURATION): CaptureSession {
        require(!duration.isZero && !duration.isNegative) { "Capture duration must be positive" }
        require(duration <= MAX_DURATION) { "Capture duration cannot exceed one hour" }

        val now = clock.instant()
        return CaptureSession(
            id = UUID.randomUUID().toString(),
            startedAt = now,
            endsAt = now.plus(duration),
            status = CaptureStatus.ACTIVE,
        ).also { session = it }
    }

    @Synchronized
    fun cancel(): CaptureSession? {
        refreshStatus()
        val current = session ?: return null
        if (current.status == CaptureStatus.ACTIVE) {
            session = current.copy(status = CaptureStatus.CANCELLED)
        }
        return session
    }

    @Synchronized
    fun canCapture(eventPostedAt: Instant): Boolean {
        val now = clock.instant()
        refreshStatus(now)
        val current = session ?: return false

        return current.status == CaptureStatus.ACTIVE &&
            !eventPostedAt.isBefore(current.startedAt) &&
            eventPostedAt.isBefore(current.endsAt) &&
            !eventPostedAt.isAfter(now)
    }

    @Synchronized
    fun currentSession(): CaptureSession? {
        refreshStatus()
        return session
    }

    private fun refreshStatus(now: Instant = clock.instant()) {
        val current = session ?: return
        if (current.status == CaptureStatus.ACTIVE && !now.isBefore(current.endsAt)) {
            session = current.copy(status = CaptureStatus.EXPIRED)
        }
    }

    companion object {
        val MAX_DURATION: Duration = Duration.ofHours(1)
    }
}
