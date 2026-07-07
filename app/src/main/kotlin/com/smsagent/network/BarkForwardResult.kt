package com.smsagent.network

data class BarkForwardResult(
    val successful: Boolean,
    val message: String,
    val durationMillis: Long,
) {
    fun displayText(): String {
        val status = if (successful) "成功" else "失败"
        return "$status：$message，耗时 ${durationMillis}ms"
    }
}
