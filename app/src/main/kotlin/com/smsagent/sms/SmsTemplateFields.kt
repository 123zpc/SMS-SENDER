package com.smsagent.sms

object SmsTemplateFields {
    const val TITLE = "title"
    const val SENDER = "sender"
    const val TIME = "time"
    const val BODY = "body"
    const val CODE = "code"

    const val TOKEN_TITLE = "{$TITLE}"
    const val TOKEN_SENDER = "{$SENDER}"
    const val TOKEN_TIME = "{$TIME}"
    const val TOKEN_BODY = "{$BODY}"
    const val TOKEN_CODE = "{$CODE}"

    val allTokens = listOf(TOKEN_TITLE, TOKEN_SENDER, TOKEN_TIME, TOKEN_BODY, TOKEN_CODE)
}
