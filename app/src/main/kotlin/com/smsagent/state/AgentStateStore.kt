package com.smsagent.state

import android.content.Context

object AgentStateStore {

    private const val PREFS_NAME = "sms_agent_state"
    private const val KEY_BARK_API_URL = "bark_api_url"
    private const val KEY_LAST_TRIGGER_TIME = "last_sms_received_at"
    private const val KEY_LAST_FORWARD_RESULT = "last_forward_result"

    fun saveBarkApiUrl(context: Context, url: String): Boolean {
        return prefs(context)
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

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}
