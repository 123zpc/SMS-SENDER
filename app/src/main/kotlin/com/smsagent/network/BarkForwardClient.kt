package com.smsagent.network

import android.os.SystemClock
import android.util.Log
import com.smsagent.sms.IncomingSms
import com.smsagent.sms.SmsForwardMetadata
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient
import okhttp3.Request

object BarkForwardClient {

    private const val TAG = "BarkForwardClient"

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(4, TimeUnit.SECONDS)
        .readTimeout(6, TimeUnit.SECONDS)
        .callTimeout(8, TimeUnit.SECONDS)
        .build()

    private val callbackExecutor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "sms-forward-callback").apply { isDaemon = false }
    }

    fun forwardSmsSync(
        remoteApiTemplate: String,
        sms: IncomingSms,
        metadata: SmsForwardMetadata,
    ): BarkForwardResult {
        val requestUrl = RemoteApiTemplateRenderer.render(remoteApiTemplate, sms)
        if (requestUrl == null) {
            val result = BarkForwardResult(
                successful = false,
                message = "远程 API 模板无效",
                durationMillis = 0L,
            )
            Log.w(TAG, metadata.logLine(result.message, result.durationMillis))
            return result
        }

        val request = Request.Builder()
            .url(requestUrl)
            .get()
            .build()
        val startedAtNanos = SystemClock.elapsedRealtimeNanos()

        return try {
            client.newCall(request).execute().use { response ->
                val result = BarkForwardResult(
                    successful = response.isSuccessful,
                    message = "HTTP ${response.code} ${response.message}",
                    durationMillis = elapsedMillis(startedAtNanos),
                )

                if (response.isSuccessful) {
                    Log.i(TAG, "SMS Forward Success")
                    Log.i(TAG, metadata.logLine(result.message, result.durationMillis))
                } else {
                    Log.w(TAG, metadata.logLine(result.message, result.durationMillis))
                }

                result
            }
        } catch (throwable: Throwable) {
            val result = BarkForwardResult(
                successful = false,
                message = "异常：${throwable.javaClass.name}",
                durationMillis = elapsedMillis(startedAtNanos),
            )
            Log.e(TAG, metadata.logLine(result.message, result.durationMillis), throwable)
            result
        }
    }

    fun forwardSms(
        remoteApiTemplate: String,
        sms: IncomingSms,
        metadata: SmsForwardMetadata,
        onResult: (BarkForwardResult) -> Unit,
    ) {
        callbackExecutor.execute {
            onResult(forwardSmsSync(remoteApiTemplate, sms, metadata))
        }
    }

    private fun elapsedMillis(startedAtNanos: Long): Long {
        return TimeUnit.NANOSECONDS.toMillis(SystemClock.elapsedRealtimeNanos() - startedAtNanos)
    }
}
