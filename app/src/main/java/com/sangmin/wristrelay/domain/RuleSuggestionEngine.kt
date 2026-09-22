package com.sangmin.wristrelay.domain

object RuleSuggestionEngine {
    fun suggest(event: NormalizedNotification): RuleDraft {
        val title = event.title.takeIf(String::isNotEmpty)?.take(MAX_MATCH_PHRASE_LENGTH)
        val body = event.body.takeIf(String::isNotEmpty)?.take(MAX_MATCH_PHRASE_LENGTH)
        val channel = event.channelId?.takeIf(String::isNotEmpty)
        val displayName = title?.take(MAX_RULE_NAME_LENGTH)
            ?: body?.take(MAX_RULE_NAME_LENGTH)
            ?: "선택한 알림"

        return RuleDraft(
            name = displayName,
            packageName = event.packageName,
            channelId = channel,
            useChannel = channel != null,
            titlePhrase = title,
            useTitle = title != null,
            bodyPhrase = body,
            useBody = body != null,
            preset = VibrationPreset.SHORT_TWICE,
        )
    }

    private const val MAX_RULE_NAME_LENGTH = 40
    private const val MAX_MATCH_PHRASE_LENGTH = 200
}
