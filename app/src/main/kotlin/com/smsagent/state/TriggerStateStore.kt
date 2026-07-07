package com.smsagent.state

import android.content.Context

object TriggerStateStore {

    private const val PREFS_NAME = "sms_agent_state"
    private const val KEY_LAST_TRIGGER_TIME = "last_sms_received_at"

    fun saveLastTriggerTime(context: Context, timeMillis: Long) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putLong(KEY_LAST_TRIGGER_TIME, timeMillis)
            .apply()
    }

    fun getLastTriggerTime(context: Context): Long {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getLong(KEY_LAST_TRIGGER_TIME, 0L)
    }
}
