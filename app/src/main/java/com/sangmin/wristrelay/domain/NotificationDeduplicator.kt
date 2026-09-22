package com.sangmin.wristrelay.domain

import java.time.Instant

class NotificationDeduplicator(
    private val selfPackageName: String,
    private val maxTrackedKeys: Int = 2_048,
) {
    private val lock = Any()
    private val activeKeys = linkedMapOf<String, Instant>()

    fun shouldAccept(event: NotificationEnvelope, now: Instant): Boolean = synchronized(lock) {
        if (
            event.packageName == selfPackageName ||
            event.isOngoing ||
            event.isGroupSummary ||
            event.notificationKey.isBlank()
        ) {
            return@synchronized false
        }

        if (activeKeys.containsKey(event.notificationKey)) return@synchronized false

        activeKeys[event.notificationKey] = now
        trimToCapacity()
        true
    }

    fun onRemoved(notificationKey: String) = synchronized(lock) {
        activeKeys.remove(notificationKey)
        Unit
    }

    fun reset() = synchronized(lock) {
        activeKeys.clear()
    }

    private fun trimToCapacity() {
        while (activeKeys.size > maxTrackedKeys) {
            activeKeys.remove(activeKeys.keys.first())
        }
    }
}
