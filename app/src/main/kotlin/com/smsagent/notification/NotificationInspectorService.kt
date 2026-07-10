package com.smsagent.notification

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.smsagent.dispatcher.SmsDispatcher
import com.smsagent.dispatcher.SmsSource
import com.smsagent.sms.IncomingSms
import com.smsagent.state.AgentStateStore
import com.smsagent.state.EventLogStore
import com.smsagent.state.NotificationDispatchState

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

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        if (sbn.packageName !in SMS_PACKAGES) {
            return
        }

        val slotKey = NotificationSmsNormalizer.buildSlotKey(sbn)
        AgentStateStore.clearNotificationDispatchState(this, slotKey)
        EventLogStore.append(
            this,
            "Notification",
            "短信通知轨道移除：package=${sbn.packageName}，slot=$slotKey",
        )
    }

    private fun dispatchSmsNotificationIfPossible(
        sbn: StatusBarNotification,
        receivedAtMillis: Long,
    ) {
        if (sbn.packageName !in SMS_PACKAGES) {
            return
        }

        val normalizedSms = NotificationSmsNormalizer.normalize(sbn)
        if (normalizedSms == null) {
            EventLogStore.append(
                this,
                "Notification",
                "短信通知轨道跳过：package=${sbn.packageName}，正文为空",
            )
            return
        }

        val previousState = AgentStateStore.getNotificationDispatchState(this, normalizedSms.slotKey)
        val skipReason = skipReason(normalizedSms, previousState)
        if (skipReason != null) {
            AgentStateStore.saveNotificationDispatchState(
                context = this,
                slotKey = normalizedSms.slotKey,
                state = NotificationDispatchState(
                    messageCount = normalizedSms.messageCount,
                    messageSignature = normalizedSms.messageSignature,
                    updatedAtMillis = receivedAtMillis,
                ),
            )
            EventLogStore.append(
                this,
                "Notification",
                "短信通知轨道去重：package=${sbn.packageName}，sender=${normalizedSms.sender}，count=${normalizedSms.messageCount}，slot=${normalizedSms.slotKey}，reason=$skipReason",
            )
            return
        }

        EventLogStore.append(
            this,
            "Notification",
            "短信通知轨道提取：package=${sbn.packageName}，sender=${normalizedSms.sender}，count=${normalizedSms.messageCount}，length=${normalizedSms.normalizedBody.length}",
        )
        SmsDispatcher.dispatch(
            context = this,
            sms = IncomingSms.fromRaw(
                normalizedSms.sender,
                normalizedSms.normalizedBody,
                receivedAtMillis,
            ),
            source = SmsSource.NOTIFICATION,
        )
        AgentStateStore.saveNotificationDispatchState(
            context = this,
            slotKey = normalizedSms.slotKey,
            state = NotificationDispatchState(
                messageCount = normalizedSms.messageCount,
                messageSignature = normalizedSms.messageSignature,
                updatedAtMillis = receivedAtMillis,
            ),
        )
    }

    private fun skipReason(
        normalizedSms: NormalizedNotificationSms,
        previousState: NotificationDispatchState?,
    ): String? {
        if (previousState == null) {
            return null
        }
        if (normalizedSms.messageCount > previousState.messageCount) {
            return null
        }
        if (normalizedSms.messageCount < previousState.messageCount) {
            return null
        }
        if (normalizedSms.messageSignature != previousState.messageSignature) {
            return null
        }
        return "same_slot_state"
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
