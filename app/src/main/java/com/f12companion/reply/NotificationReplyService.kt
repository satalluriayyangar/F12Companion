package com.f12companion.reply

import android.app.Notification
import android.content.Intent
import android.os.Bundle
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import androidx.core.app.RemoteInput
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class NotificationReplyService : NotificationListenerService() {

    private val _notificationPosted = MutableStateFlow<Pair<String, String>?>(null)
    val notificationPosted: StateFlow<Pair<String, String>?> = _notificationPosted

    private val _notificationRemoved = MutableStateFlow<String?>(null)
    val notificationRemoved: StateFlow<String?> = _notificationRemoved

    private var latestReplyIntent: Intent? = null

    private var latestReplyPendingIntent: android.app.PendingIntent? = null
    private var latestReplyRemoteInput: android.app.RemoteInput? = null

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val packageName = sbn.packageName ?: return
        val notification = sbn.notification ?: return

        val title = notification.extras?.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: ""
        val text = notification.extras?.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""

        val (pendingIntent, remoteInput) = extractReplyPendingIntent(notification)
        latestReplyPendingIntent = pendingIntent
        latestReplyRemoteInput = remoteInput

        val hasRemoteInput = latestReplyPendingIntent != null
        Log.d("NotificationReplyService", "Posted: $packageName title=$title hasRemoteInput=$hasRemoteInput")
        _notificationPosted.value = packageName to "$title: $text"
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        val packageName = sbn.packageName ?: return
        _notificationRemoved.value = packageName
    }

    fun getLatestReplyPendingIntent(): android.app.PendingIntent? = latestReplyPendingIntent
    fun getLatestReplyRemoteInput(): android.app.RemoteInput? = latestReplyRemoteInput

    private fun extractReplyPendingIntent(notification: Notification): Pair<android.app.PendingIntent?, android.app.RemoteInput?> {
        val actions = notification.actions ?: return null to null
        for (action in actions) {
            val remoteInputs = action.remoteInputs ?: continue
            if (remoteInputs.isNotEmpty() && action.actionIntent != null) {
                return action.actionIntent to remoteInputs[0]
            }
        }
        return null to null
    }

    inner class LocalBinder : android.os.Binder() {
        fun getService(): NotificationReplyService = this@NotificationReplyService
    }

    override fun onBind(intent: Intent): android.os.IBinder = LocalBinder()
}
