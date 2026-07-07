package com.smsagent.sms

import android.telephony.SmsMessage

data class IncomingSms(
    val sender: String,
    val body: String,
    val receivedAtMillis: Long,
) {
    val verificationCode: String = VerificationCodeExtractor.extract(body)

    val title: String
        get() = if (verificationCode.isNotBlank()) {
            "??? $verificationCode"
        } else {
            "???? $sender"
        }

    companion object {
        fun fromMessages(messages: Array<out SmsMessage>, receivedAtMillis: Long): IncomingSms {
            val firstMessage = messages.firstOrNull()
            val sender = firstMessage?.displayOriginatingAddress
                ?: firstMessage?.originatingAddress
                ?: "????"
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
