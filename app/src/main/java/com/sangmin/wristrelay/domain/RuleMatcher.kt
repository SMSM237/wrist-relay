package com.sangmin.wristrelay.domain

object RuleMatcher {
    fun matches(rule: SmartRule, event: NormalizedNotification): Boolean {
        if (!rule.enabled || rule.packageName.trim() != event.packageName) return false

        val hasSpecificCondition = rule.useChannel || rule.useTitle || rule.useBody
        if (!hasSpecificCondition) return false

        if (rule.useChannel) {
            val expectedChannel = rule.channelId?.trim()?.takeIf(String::isNotEmpty) ?: return false
            if (event.channelId != expectedChannel) return false
        }
        if (rule.useTitle && !containsPhrase(event.title, rule.titlePhrase)) return false
        if (rule.useBody && !containsPhrase(event.body, rule.bodyPhrase)) return false

        return true
    }

    private fun containsPhrase(value: String, phrase: String?): Boolean {
        val normalizedPhrase = NotificationNormalizer.normalizeText(phrase.orEmpty())
        return normalizedPhrase.isNotEmpty() && value.contains(normalizedPhrase, ignoreCase = true)
    }
}
