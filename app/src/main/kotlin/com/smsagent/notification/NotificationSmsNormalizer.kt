package com.smsagent.notification

import android.app.Notification
import android.os.Bundle
import android.service.notification.StatusBarNotification
import com.smsagent.sms.VerificationCodeExtractor

data class NormalizedNotificationSms(
    val sender: String,
    val rawBody: String,
    val normalizedBody: String,
    val verificationCode: String,
    val messageCount: Int,
    val slotKey: String,
    val messageSignature: Int,
)

object NotificationSmsNormalizer {

    private val messageCountRegex = Regex("""^\[(\d+)条]""")
    private val groupedPrefixRegex = Regex("""^\[\d+条]\s*[.。…·]*\s*""")
    private val maskedCodeWithLabelRegex = Regex("""(验证码[:：]?)\s*[*＊]{4,8}""")
    private val maskedCodeRegex = Regex("""(?<![0-9A-Za-z])[*＊]{4,8}(?![0-9A-Za-z])""")
    private const val EXTRA_VERIFY_CODE = "verify_code"

    fun normalize(sbn: StatusBarNotification): NormalizedNotificationSms? {
        val extras = sbn.notification.extras ?: Bundle.EMPTY
        val sender = extras.getCharSequence(Notification.EXTRA_TITLE)
            ?.toString()
            ?.takeIf { it.isNotBlank() }
            ?: sbn.packageName
        val rawBody = extractBody(extras)
        if (rawBody.isBlank()) {
            return null
        }

        val verificationCode = extractVerificationCode(extras, rawBody)
        val normalizedBody = normalizeBody(rawBody, verificationCode)
        val slotKey = buildSlotKey(sbn)
        val messageSignature = listOf(sender, normalizedBody, verificationCode)
            .joinToString(separator = "\n")
            .hashCode()

        return NormalizedNotificationSms(
            sender = sender,
            rawBody = rawBody,
            normalizedBody = normalizedBody,
            verificationCode = verificationCode,
            messageCount = extractMessageCount(rawBody),
            slotKey = slotKey,
            messageSignature = messageSignature,
        )
    }

    fun buildSlotKey(sbn: StatusBarNotification): String {
        return listOf(
            sbn.packageName,
            sbn.id.toString(),
            sbn.tag.orEmpty(),
        ).joinToString(separator = "|")
    }

    private fun extractBody(extras: Bundle): String {
        val bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()
        val textLines = extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)
            ?.joinToString(separator = "\n") { it.toString() }

        return listOf(bigText, textLines, text)
            .firstOrNull { !it.isNullOrBlank() }
            .orEmpty()
    }

    private fun extractVerificationCode(extras: Bundle, rawBody: String): String {
        val extraCode = extras.get(EXTRA_VERIFY_CODE)?.toString()?.trim().orEmpty()
        if (extraCode.isNotBlank() && extraCode.any { it.isDigit() }) {
            return extraCode
        }
        return VerificationCodeExtractor.extract(rawBody)
    }

    private fun extractMessageCount(rawBody: String): Int {
        val matched = messageCountRegex.find(rawBody)?.groupValues?.getOrNull(1)
        return matched?.toIntOrNull()?.takeIf { it > 0 } ?: 1
    }

    private fun normalizeBody(rawBody: String, verificationCode: String): String {
        var normalized = groupedPrefixRegex.replaceFirst(rawBody.trim(), "")
        if (verificationCode.isNotBlank()) {
            normalized = maskedCodeWithLabelRegex.replace(normalized) { result ->
                result.groupValues[1] + verificationCode
            }
            normalized = maskedCodeRegex.replace(normalized, verificationCode)
        }
        return normalized
            .replace(Regex("""\s+"""), " ")
            .trim()
    }
}
