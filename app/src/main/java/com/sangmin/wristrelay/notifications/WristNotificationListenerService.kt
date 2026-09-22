package com.sangmin.wristrelay.notifications

import android.content.ComponentName
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

interface NotificationRuntimeProvider {
    val notificationScope: CoroutineScope
    val listenerPipeline: ListenerPipeline

    fun onListenerConnectionChanged(connected: Boolean)

    fun onListenerFailure(error: Throwable)
}

class WristNotificationListenerService : NotificationListenerService() {
    private val runtime: NotificationRuntimeProvider?
        get() = applicationContext as? NotificationRuntimeProvider

    override fun onListenerConnected() {
        super.onListenerConnected()
        runtime?.onListenerConnectionChanged(true)
    }

    override fun onListenerDisconnected() {
        runtime?.onListenerConnectionChanged(false)
        runCatching {
            NotificationListenerService.requestRebind(
                ComponentName(this, WristNotificationListenerService::class.java),
            )
        }.onFailure { runtime?.onListenerFailure(it) }
        super.onListenerDisconnected()
    }

    override fun onDestroy() {
        runtime?.onListenerConnectionChanged(false)
        super.onDestroy()
    }

    override fun onNotificationPosted(statusBarNotification: StatusBarNotification?) {
        val notification = statusBarNotification ?: return
        val provider = runtime ?: return
        provider.notificationScope.launch {
            runCatching { provider.listenerPipeline.onNotificationPosted(notification) }
                .onFailure(provider::onListenerFailure)
        }
    }

    override fun onNotificationRemoved(statusBarNotification: StatusBarNotification?) {
        statusBarNotification?.key?.let { key -> runtime?.listenerPipeline?.onNotificationRemoved(key) }
    }
}
