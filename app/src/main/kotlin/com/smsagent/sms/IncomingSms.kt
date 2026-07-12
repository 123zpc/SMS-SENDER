package com.smsagent.sms

import android.telephony.SmsMessage

data class IncomingSms(
    val sender: String,
    val body: String,
    val receivedAtMillis: Long,
    val systemSmsId: Long? = null,
) {
    val verificationCode: String = VerificationCodeExtractor.extract(body)

    val title: String
        get() = if (verificationCode.isNotBlank()) {
            "验证码 $verificationCode"
        } else {
            "短信来自 $sender"
        }

    companion object {
        fun fromMessages(messages: Array<out SmsMessage>, receivedAtMillis: Long): IncomingSms {
            val firstMessage = messages.firstOrNull()
            val sender = firstMessage?.displayOriginatingAddress
                ?: firstMessage?.originatingAddress
                ?: UNKNOWN_SENDER
            val body = messages.joinToString(separator = "") { message ->
                message.displayMessageBody ?: message.messageBody.orEmpty()
            }

            return IncomingSms(
                sender = sender.ifBlank { UNKNOWN_SENDER },
                body = body,
                receivedAtMillis = receivedAtMillis,
            )
        }

        fun fromRaw(sender: String?, body: String?, receivedAtMillis: Long, systemSmsId: Long? = null): IncomingSms {
            return IncomingSms(
                sender = sender?.takeIf { it.isNotBlank() } ?: UNKNOWN_SENDER,
                body = body.orEmpty(),
                receivedAtMillis = receivedAtMillis,
                systemSmsId = systemSmsId,
            )
        }

        private const val UNKNOWN_SENDER = "未知号码"
    }
}
