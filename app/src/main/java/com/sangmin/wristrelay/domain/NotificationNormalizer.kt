package com.sangmin.wristrelay.domain

import java.text.Normalizer

object NotificationNormalizer {
    private val whitespace = Regex("\\s+")

    fun normalize(envelope: NotificationEnvelope): NormalizedNotification = NormalizedNotification(
        packageName = envelope.packageName.trim(),
        channelId = envelope.channelId?.trim()?.takeIf(String::isNotEmpty),
        title = normalizeText(envelope.title.orEmpty()),
        body = normalizeText(envelope.body.orEmpty()),
        notificationKey = envelope.notificationKey.trim(),
        postedAt = envelope.postedAt,
    )

    fun normalizeText(value: String): String = Normalizer
        .normalize(value, Normalizer.Form.NFC)
        .replace('\u00A0', ' ')
        .trim()
        .replace(whitespace, " ")
}
