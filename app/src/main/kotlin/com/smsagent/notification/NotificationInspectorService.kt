package com.smsagent.notification

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.smsagent.dispatcher.SmsDispatcher
import com.smsagent.dispatcher.SmsSource
import com.smsagent.sms.IncomingSms
import com.smsagent.state.EventLogStore

class NotificationInspectorService : NotificationListenerService() {

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val receivedAtMillis = System.currentTimeMillis()
        val message = NotificationSnapshotFormatter.format(
            context = this,
            sbn = sbn,
            receivedAtMillis = receivedAtMillis,
        )
        Log.i(TAG, message)
        EventLogStore.append(this, "Notification", message)
        dispatchSmsNotificationIfPossible(sbn, receivedAtMillis)
    }

    private fun dispatchSmsNotificationIfPossible(
        sbn: StatusBarNotification,
        receivedAtMillis: Long,
    ) {
        if (sbn.packageName !in SMS_PACKAGES) {
            return
        }

        val extras = sbn.notification.extras
        val sender = extras.getCharSequence(Notification.EXTRA_TITLE)
            ?.toString()
            ?.takeIf { it.isNotBlank() }
            ?: sbn.packageName
        val body = extractBody(extras)
        if (body.isBlank()) {
            EventLogStore.append(
                this,
                "Notification",
                "短信通知轨道跳过：package=${sbn.packageName}，正文为空",
            )
            return
        }

        EventLogStore.append(
            this,
            "Notification",
            "短信通知轨道提取：package=${sbn.packageName}，sender=$sender，length=${body.length}",
        )
        SmsDispatcher.dispatch(
            context = this,
            sms = IncomingSms.fromRaw(sender, body, receivedAtMillis),
            source = SmsSource.NOTIFICATION,
        )
    }

    private fun extractBody(extras: android.os.Bundle): String {
        val bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()
        val textLines = extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)
            ?.joinToString(separator = "\n") { it.toString() }

        return listOf(bigText, textLines, text)
            .firstOrNull { !it.isNullOrBlank() }
            .orEmpty()
    }

    private companion object {
        private const val TAG = "NotificationInspector"

        private val SMS_PACKAGES = setOf(
            "com.android.mms",
            "com.android.messaging",
            "com.google.android.apps.messaging",
            "com.samsung.android.messaging",
            "com.miui.mms",
            "com.coloros.mms",
            "com.bbk.mms",
            "com.vivo.messaging",
            "com.huawei.message",
        )
    }
}
