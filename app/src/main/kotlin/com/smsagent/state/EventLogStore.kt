package com.smsagent.state

import android.content.Context
import com.smsagent.util.TimeFormatter

object EventLogStore {

    private const val PREFS_NAME = "sms_agent_event_log"
    private const val KEY_EVENTS = "events"
    private const val MAX_EVENTS = 80
    private const val SEPARATOR = "\n\n"

    fun append(context: Context, level: String, message: String) {
        val event = "${TimeFormatter.format(System.currentTimeMillis())} [$level] $message"
        val events = getEvents(context).toMutableList()
        events.add(0, event)

        prefs(context)
            .edit()
            .putString(KEY_EVENTS, events.take(MAX_EVENTS).joinToString(SEPARATOR))
            .commit()
    }

    fun getEventText(context: Context): String {
        return getEvents(context).joinToString(SEPARATOR)
    }

    fun getConsoleText(context: Context): String {
        return getEvents(context).asReversed().joinToString("\n")
    }

    fun clear(context: Context) {
        prefs(context).edit().remove(KEY_EVENTS).commit()
    }

    private fun getEvents(context: Context): List<String> {
        return prefs(context).getString(KEY_EVENTS, "").orEmpty()
            .split(SEPARATOR)
            .filter { it.isNotBlank() }
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}
