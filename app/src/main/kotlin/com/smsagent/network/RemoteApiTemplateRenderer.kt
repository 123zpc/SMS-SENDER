package com.smsagent.network

import android.net.Uri
import com.smsagent.sms.IncomingSms
import com.smsagent.sms.SmsTemplateFields
import com.smsagent.util.TimeFormatter
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

object RemoteApiTemplateRenderer {

    fun render(template: String, sms: IncomingSms): String? {
        val trimmedTemplate = template.trim()
        if (trimmedTemplate.isBlank()) {
            return null
        }

        return if (SmsTemplateFields.allTokens.any { token -> trimmedTemplate.contains(token) }) {
            renderTemplateUrl(trimmedTemplate, sms)
        } else {
            renderLegacyBarkUrl(trimmedTemplate, sms)
        }
    }

    private fun renderTemplateUrl(template: String, sms: IncomingSms): String? {
        val rendered = template
            .replace(SmsTemplateFields.TOKEN_TITLE, encode(sms.title))
            .replace(SmsTemplateFields.TOKEN_SENDER, encode(sms.sender))
            .replace(SmsTemplateFields.TOKEN_TIME, encode(TimeFormatter.format(sms.receivedAtMillis)))
            .replace(SmsTemplateFields.TOKEN_BODY, encode(sms.body))
            .replace(SmsTemplateFields.TOKEN_CODE, encode(sms.verificationCode))

        return rendered.takeIf { it.toHttpUrlOrNull() != null }
    }

    private fun renderLegacyBarkUrl(baseApiUrl: String, sms: IncomingSms): String? {
        val baseUrl = baseApiUrl.trimEnd('/').toHttpUrlOrNull() ?: return null
        return baseUrl.newBuilder()
            .addPathSegment(sms.title)
            .addPathSegment(sms.body)
            .build()
            .toString()
    }

    private fun encode(value: String): String {
        return Uri.encode(value)
    }
}
