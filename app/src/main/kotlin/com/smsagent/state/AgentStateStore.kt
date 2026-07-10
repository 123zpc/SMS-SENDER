package com.smsagent.state

import android.content.Context

data class NotificationDispatchState(
    val messageCount: Int,
    val messageSignature: Int,
    val updatedAtMillis: Long,
)

object AgentStateStore {

    private const val PREFS_NAME = "sms_agent_state"
    private const val KEY_REMOTE_API_TEMPLATE = "remote_api_template"
    private const val KEY_BARK_API_URL = "bark_api_url"
    private const val KEY_LAST_TRIGGER_TIME = "last_sms_received_at"
    private const val KEY_LAST_FORWARD_RESULT = "last_forward_result"
    private const val KEY_LAST_OBSERVED_SMS_ID = "last_observed_sms_id"
    private const val KEY_NOTIFICATION_STATE_PREFIX = "notification_dispatch_state:"
    private const val NOTIFICATION_STATE_TTL_MILLIS = 6 * 60 * 60 * 1000L

    fun saveRemoteApiTemplate(context: Context, template: String): Boolean {
        return prefs(context)
            .edit()
            .putString(KEY_REMOTE_API_TEMPLATE, template)
            .commit()
    }

    fun getRemoteApiTemplate(context: Context): String {
        return prefs(context).getString(KEY_REMOTE_API_TEMPLATE, null)
            ?: getBarkApiUrl(context)
    }

    fun saveBarkApiUrl(context: Context, url: String): Boolean {
        return saveRemoteApiTemplate(context, url) && prefs(context)
            .edit()
            .putString(KEY_BARK_API_URL, url)
            .commit()
    }

    fun getBarkApiUrl(context: Context): String {
        return prefs(context).getString(KEY_BARK_API_URL, "").orEmpty()
    }

    fun saveLastTriggerTime(context: Context, timeMillis: Long): Boolean {
        return prefs(context)
            .edit()
            .putLong(KEY_LAST_TRIGGER_TIME, timeMillis)
            .commit()
    }

    fun getLastTriggerTime(context: Context): Long {
        return prefs(context).getLong(KEY_LAST_TRIGGER_TIME, 0L)
    }

    fun saveLastForwardResult(context: Context, result: String): Boolean {
        return prefs(context)
            .edit()
            .putString(KEY_LAST_FORWARD_RESULT, result)
            .commit()
    }

    fun getLastForwardResult(context: Context): String {
        return prefs(context).getString(KEY_LAST_FORWARD_RESULT, "").orEmpty()
    }

    fun saveLastObservedSmsId(context: Context, smsId: Long): Boolean {
        return prefs(context)
            .edit()
            .putLong(KEY_LAST_OBSERVED_SMS_ID, smsId)
            .commit()
    }

    fun getLastObservedSmsId(context: Context): Long {
        return prefs(context).getLong(KEY_LAST_OBSERVED_SMS_ID, 0L)
    }

    fun saveNotificationDispatchState(
        context: Context,
        slotKey: String,
        state: NotificationDispatchState,
    ): Boolean {
        return prefs(context)
            .edit()
            .putString(notificationStateKey(slotKey), encodeNotificationState(state))
            .commit()
    }

    fun getNotificationDispatchState(
        context: Context,
        slotKey: String,
    ): NotificationDispatchState? {
        val prefs = prefs(context)
        val storedValue = prefs.getString(notificationStateKey(slotKey), null) ?: return null
        val state = decodeNotificationState(storedValue) ?: run {
            clearNotificationDispatchState(context, slotKey)
            return null
        }
        if (System.currentTimeMillis() - state.updatedAtMillis > NOTIFICATION_STATE_TTL_MILLIS) {
            clearNotificationDispatchState(context, slotKey)
            return null
        }
        return state
    }

    fun clearNotificationDispatchState(context: Context, slotKey: String): Boolean {
        return prefs(context)
            .edit()
            .remove(notificationStateKey(slotKey))
            .commit()
    }

    private fun notificationStateKey(slotKey: String): String {
        return KEY_NOTIFICATION_STATE_PREFIX + slotKey
    }

    private fun encodeNotificationState(state: NotificationDispatchState): String {
        return listOf(
            state.messageCount.toString(),
            state.messageSignature.toString(),
            state.updatedAtMillis.toString(),
        ).joinToString(separator = "|")
    }

    private fun decodeNotificationState(value: String): NotificationDispatchState? {
        val parts = value.split("|")
        if (parts.size != 3) {
            return null
        }

        val messageCount = parts[0].toIntOrNull() ?: return null
        val messageSignature = parts[1].toIntOrNull() ?: return null
        val updatedAtMillis = parts[2].toLongOrNull() ?: return null
        return NotificationDispatchState(
            messageCount = messageCount,
            messageSignature = messageSignature,
            updatedAtMillis = updatedAtMillis,
        )
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}
