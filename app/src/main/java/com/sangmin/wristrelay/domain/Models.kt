package com.sangmin.wristrelay.domain

import java.time.Instant

data class NotificationEnvelope(
    val packageName: String,
    val channelId: String?,
    val title: String?,
    val body: String?,
    val notificationKey: String,
    val postedAt: Instant,
    val isOngoing: Boolean,
    val isGroupSummary: Boolean,
)

data class NormalizedNotification(
    val packageName: String,
    val channelId: String?,
    val title: String,
    val body: String,
    val notificationKey: String,
    val postedAt: Instant,
)

enum class VibrationPreset {
    SHORT_ONCE,
    SHORT_TWICE,
    LONG_ONCE,
    EMPHASIZED_THREE,
}

data class SmartRule(
    val id: String,
    val name: String,
    val packageName: String,
    val channelId: String?,
    val useChannel: Boolean,
    val titlePhrase: String?,
    val useTitle: Boolean,
    val bodyPhrase: String?,
    val useBody: Boolean,
    val preset: VibrationPreset,
    val enabled: Boolean,
)

data class RuleDraft(
    val name: String,
    val packageName: String,
    val channelId: String?,
    val useChannel: Boolean,
    val titlePhrase: String?,
    val useTitle: Boolean,
    val bodyPhrase: String?,
    val useBody: Boolean,
    val preset: VibrationPreset,
) {
    fun hasUsableCondition(): Boolean =
        (useChannel && !channelId.isNullOrBlank()) ||
            (useTitle && !titlePhrase.isNullOrBlank()) ||
            (useBody && !bodyPhrase.isNullOrBlank())
}

enum class CaptureStatus {
    ACTIVE,
    CANCELLED,
    EXPIRED,
}

data class CaptureSession(
    val id: String,
    val startedAt: Instant,
    val endsAt: Instant,
    val status: CaptureStatus,
)
