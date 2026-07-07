package com.smsagent.sms

import android.telephony.SmsMessage

data class IncomingSms(
    val sender: String,
    val body: String,
    val receivedAtMillis: Long,
) {
    companion object {
        fun fromMessages(messages: Array<out SmsMessage>, receivedAtMillis: Long): IncomingSms {
            val firstMessage = messages.firstOrNull()
            val sender = firstMessage?.displayOriginatingAddress
                ?: firstMessage?.originatingAddress
                ?: "未知号码"
            val body = messages.joinToString(separator = "") { message ->
                message.displayMessageBody ?: message.messageBody.orEmpty()
            }

            return IncomingSms(
                sender = sender,
                body = body,
                receivedAtMillis = receivedAtMillis,
            )
        }
    }
}
