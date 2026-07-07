package com.smsagent.notification

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.smsagent.state.EventLogStore

class NotificationInspectorService : NotificationListenerService() {

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val message = NotificationSnapshotFormatter.format(
            context = this,
            sbn = sbn,
            receivedAtMillis = System.currentTimeMillis(),
        )
        Log.i(TAG, message)
        EventLogStore.append(this, "Notification", message)
    }

    private companion object {
        private const val TAG = "NotificationInspector"
    }
}
