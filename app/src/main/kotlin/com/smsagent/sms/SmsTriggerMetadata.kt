package com.smsagent.sms

import com.smsagent.util.TimeFormatter

data class SmsTriggerMetadata(
    val receivedAtMillis: Long,
    val pid: Int,
    val processName: String,
) {
    fun logLine(httpResult: String, durationMillis: Long): String {
        return "time=${TimeFormatter.format(System.currentTimeMillis())} " +
            "receivedAt=${TimeFormatter.format(receivedAtMillis)} " +
            "pid=$pid " +
            "processName=$processName " +
            "threadName=${Thread.currentThread().name} " +
            "httpResult=$httpResult " +
            "durationMs=$durationMillis"
    }
}
