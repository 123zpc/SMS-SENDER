package com.smsagent.notification

import android.app.Notification
import android.content.Context
import android.os.Bundle
import android.service.notification.StatusBarNotification
import com.smsagent.util.TimeFormatter

object NotificationSnapshotFormatter {

    fun format(
        context: Context,
        sbn: StatusBarNotification,
        receivedAtMillis: Long,
    ): String {
        val notification = sbn.notification
        val extras = notification.extras ?: Bundle.EMPTY

        return buildString {
            appendLine("ReceivedTime=${TimeFormatter.format(receivedAtMillis)}")
            appendLine("Package=${valueOrNull(sbn.packageName)}")
            appendLine("ApplicationLabel=${valueOrNull(loadApplicationLabel(context, sbn.packageName))}")
            appendLine("NotificationId=${sbn.id}")
            appendLine("PostTime=${TimeFormatter.format(sbn.postTime)}")
            appendLine("Category=${valueOrNull(notification.category)}")
            appendLine("ChannelId=${valueOrNull(notification.channelId)}")
            appendLine("Visibility=${visibilityName(notification.visibility)}")
            appendLine("Priority=${priorityName(notification.priority)}")
            appendLine("Flags=${notification.flags}")
            appendLine("TickerText=${valueOrNull(notification.tickerText)}")
            appendLine("Title=${valueOrNull(extras.getCharSequence(Notification.EXTRA_TITLE))}")
            appendLine("Text=${valueOrNull(extras.getCharSequence(Notification.EXTRA_TEXT))}")
            appendLine("BigText=${valueOrNull(extras.getCharSequence(Notification.EXTRA_BIG_TEXT))}")
            appendLine("SubText=${valueOrNull(extras.getCharSequence(Notification.EXTRA_SUB_TEXT))}")
            appendLine("SummaryText=${valueOrNull(extras.getCharSequence(Notification.EXTRA_SUMMARY_TEXT))}")
            appendLine("InfoText=${valueOrNull(extras.getCharSequence(Notification.EXTRA_INFO_TEXT))}")
            appendLine("ExtrasKeys=${extrasKeys(extras)}")
            appendLine("Extras=${extrasValues(extras)}")
        }.trimEnd()
    }

    private fun loadApplicationLabel(context: Context, packageName: String): CharSequence? {
        return runCatching {
            val applicationInfo = context.packageManager.getApplicationInfo(packageName, 0)
            context.packageManager.getApplicationLabel(applicationInfo)
        }.getOrNull()
    }

    private fun extrasKeys(extras: Bundle): String {
        val keys = extras.keySet().sorted()
        return if (keys.isEmpty()) {
            "NULL"
        } else {
            keys.joinToString(prefix = "[", postfix = "]")
        }
    }

    private fun extrasValues(extras: Bundle): String {
        val keys = extras.keySet().sorted()
        if (keys.isEmpty()) {
            return "NULL"
        }

        return keys.joinToString(separator = "\n", prefix = "{\n", postfix = "\n}") { key ->
            val value = runCatching { extras.get(key) }.getOrNull()
            "  $key=${formatValue(value)}"
        }
    }

    private fun formatValue(value: Any?): String {
        return when (value) {
            null -> "NULL"
            is CharSequence -> valueOrNull(value)
            is Array<*> -> value.joinToString(prefix = "[", postfix = "]") { formatValue(it) }
            is BooleanArray -> value.joinToString(prefix = "[", postfix = "]")
            is ByteArray -> value.joinToString(prefix = "[", postfix = "]")
            is CharArray -> value.joinToString(prefix = "[", postfix = "]")
            is DoubleArray -> value.joinToString(prefix = "[", postfix = "]")
            is FloatArray -> value.joinToString(prefix = "[", postfix = "]")
            is IntArray -> value.joinToString(prefix = "[", postfix = "]")
            is LongArray -> value.joinToString(prefix = "[", postfix = "]")
            is ShortArray -> value.joinToString(prefix = "[", postfix = "]")
            is Bundle -> extrasValues(value)
            else -> value.toString().ifBlank { "NULL" }
        }
    }

    private fun valueOrNull(value: CharSequence?): String {
        return value?.toString()?.takeIf { it.isNotBlank() } ?: "NULL"
    }

    private fun visibilityName(visibility: Int): String {
        return when (visibility) {
            Notification.VISIBILITY_PUBLIC -> "PUBLIC"
            Notification.VISIBILITY_PRIVATE -> "PRIVATE"
            Notification.VISIBILITY_SECRET -> "SECRET"
            else -> visibility.toString()
        }
    }

    private fun priorityName(priority: Int): String {
        return when (priority) {
            Notification.PRIORITY_MIN -> "MIN"
            Notification.PRIORITY_LOW -> "LOW"
            Notification.PRIORITY_DEFAULT -> "DEFAULT"
            Notification.PRIORITY_HIGH -> "HIGH"
            Notification.PRIORITY_MAX -> "MAX"
            else -> priority.toString()
        }
    }
}
