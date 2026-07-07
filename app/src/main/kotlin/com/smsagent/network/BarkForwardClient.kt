package com.smsagent.network

import android.os.SystemClock
import android.util.Log
import com.smsagent.sms.IncomingSms
import com.smsagent.sms.SmsForwardMetadata
import com.smsagent.util.TimeFormatter
import java.io.IOException
import java.util.concurrent.TimeUnit
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

object BarkForwardClient {

    private const val TAG = "BarkForwardClient"
    private const val TITLE = "SMS Agent"

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(4, TimeUnit.SECONDS)
        .readTimeout(6, TimeUnit.SECONDS)
        .callTimeout(8, TimeUnit.SECONDS)
        .build()

    fun forwardSms(
        barkApiUrl: String,
        sms: IncomingSms,
        metadata: SmsForwardMetadata,
        onResult: (BarkForwardResult) -> Unit,
    ) {
        val requestUrl = buildRequestUrl(barkApiUrl, sms)
        if (requestUrl == null) {
            val result = BarkForwardResult(
                successful = false,
                message = "Bark API 地址无效",
                durationMillis = 0L,
            )
            Log.w(TAG, metadata.logLine(result.message, result.durationMillis))
            onResult(result)
            return
        }

        val request = Request.Builder()
            .url(requestUrl)
            .get()
            .build()
        val startedAtNanos = SystemClock.elapsedRealtimeNanos()

        try {
            client.newCall(request).enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    val result = BarkForwardResult(
                        successful = false,
                        message = "异常：${e.javaClass.name}",
                        durationMillis = elapsedMillis(startedAtNanos),
                    )
                    Log.e(TAG, metadata.logLine(result.message, result.durationMillis), e)
                    onResult(result)
                }

                override fun onResponse(call: Call, response: Response) {
                    response.use {
                        val result = BarkForwardResult(
                            successful = it.isSuccessful,
                            message = "HTTP ${it.code} ${it.message}",
                            durationMillis = elapsedMillis(startedAtNanos),
                        )

                        if (it.isSuccessful) {
                            Log.i(TAG, "SMS Forward Success")
                            Log.i(TAG, metadata.logLine(result.message, result.durationMillis))
                        } else {
                            Log.w(TAG, metadata.logLine(result.message, result.durationMillis))
                        }

                        onResult(result)
                    }
                }
            })
        } catch (throwable: Throwable) {
            val result = BarkForwardResult(
                successful = false,
                message = "异常：${throwable.javaClass.name}",
                durationMillis = elapsedMillis(startedAtNanos),
            )
            Log.e(TAG, metadata.logLine(result.message, result.durationMillis), throwable)
            onResult(result)
        }
    }

    private fun buildRequestUrl(barkApiUrl: String, sms: IncomingSms): HttpUrl? {
        val baseUrl = barkApiUrl.trim().trimEnd('/').toHttpUrlOrNull() ?: return null
        val body = buildString {
            append("发件人：")
            appendLine(sms.sender)
            append("时间：")
            appendLine(TimeFormatter.format(sms.receivedAtMillis))
            append("内容：")
            append(sms.body)
        }

        return baseUrl.newBuilder()
            .addPathSegment(TITLE)
            .addPathSegment(body)
            .build()
    }

    private fun elapsedMillis(startedAtNanos: Long): Long {
        return TimeUnit.NANOSECONDS.toMillis(SystemClock.elapsedRealtimeNanos() - startedAtNanos)
    }
}
