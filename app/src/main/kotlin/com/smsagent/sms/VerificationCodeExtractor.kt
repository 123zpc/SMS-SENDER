package com.smsagent.sms

object VerificationCodeExtractor {

    private val digitCodeRegex = Regex("""(?<!\d)\d{4,8}(?!\d)""")

    fun extract(messageBody: String): String {
        return digitCodeRegex.find(messageBody)?.value.orEmpty()
    }
}
