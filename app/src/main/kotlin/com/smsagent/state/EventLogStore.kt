package com.smsagent.state

import android.content.Context
import com.smsagent.util.TimeFormatter
import java.util.Locale

object EventLogStore {

    private const val PREFS_NAME = "sms_agent_event_log"
    private const val KEY_EVENTS = "events"
    private const val KEY_EVENT_SEQUENCE = "event_sequence"
    private const val MAX_EVENTS = 300
    private const val SEPARATOR = "\n\n"
    private const val EXPORT_SEPARATOR = "\n\n============================================================\n\n"

    fun append(context: Context, level: String, message: String) {
        val sequence = nextSequence(context)
        val event = buildEvent(sequence, level, message)
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
        return getEvents(context).asReversed().joinToString(SEPARATOR)
    }

    fun getExportText(context: Context): String {
        val events = getEvents(context).asReversed()
        if (events.isEmpty()) {
            return ""
        }

        return buildString {
            appendLine("SMS Agent 调试日志导出")
            appendLine("导出时间：${TimeFormatter.format(System.currentTimeMillis())}")
            appendLine("事件数量：${events.size}")
            append(EXPORT_SEPARATOR.trimStart())
            append(events.joinToString(EXPORT_SEPARATOR))
        }
    }

    fun clear(context: Context) {
        prefs(context).edit()
            .remove(KEY_EVENTS)
            .remove(KEY_EVENT_SEQUENCE)
            .commit()
    }

    private fun getEvents(context: Context): List<String> {
        return prefs(context).getString(KEY_EVENTS, "").orEmpty()
            .split(SEPARATOR)
            .filter { it.isNotBlank() }
    }

    private fun nextSequence(context: Context): Long {
        val prefs = prefs(context)
        val nextValue = prefs.getLong(KEY_EVENT_SEQUENCE, 0L) + 1L
        prefs.edit()
            .putLong(KEY_EVENT_SEQUENCE, nextValue)
            .commit()
        return nextValue
    }

    private fun buildEvent(sequence: Long, level: String, message: String): String {
        val header = String.format(
            Locale.US,
            "#%05d %s [%s]",
            sequence,
            TimeFormatter.format(System.currentTimeMillis()),
            level,
        )
        val body = message
            .trimEnd()
            .ifBlank { "-" }
            .lines()
            .joinToString(separator = "\n") { line ->
                "  $line"
            }
        return "$header\n$body"
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}
