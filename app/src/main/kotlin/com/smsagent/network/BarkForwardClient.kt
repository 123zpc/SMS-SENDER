package com.smsagent.network

import android.os.SystemClock
import android.util.Log
import com.smsagent.sms.IncomingSms
import com.smsagent.sms.SmsForwardMetadata
import java.io.IOException
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient
import okhttp3.Request

object BarkForwardClient {

    private const val TAG = "BarkForwardClient"
    private val executor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "sms-forward-http").apply {
            isDaemon = false
        }
    }

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(4, TimeUnit.SECONDS)
        .readTimeout(6, TimeUnit.SECONDS)
        .callTimeout(8, TimeUnit.SECONDS)
        .build()

    fun forwardSms(
        remoteApiTemplate: String,
        sms: IncomingSms,
        metadata: SmsForwardMetadata,
        onResult: (BarkForwardResult) -> Unit,
    ) {
        val requestUrl = RemoteApiTemplateRenderer.render(remoteApiTemplate, sms)
        if (requestUrl == null) {
            val result = BarkForwardResult(
                successful = false,
                message = "远程 API 模板无效",
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

        val submittedAtNanos = SystemClock.elapsedRealtimeNanos()
        try {
            executor.execute {
                try {
                    client.newCall(request).execute().use { response ->
                        val result = BarkForwardResult(
                            successful = response.isSuccessful,
                            message = "HTTP ${response.code} ${response.message}",
                            durationMillis = elapsedMillis(submittedAtNanos),
                        )

                        if (response.isSuccessful) {
                            Log.i(TAG, "SMS Forward Success")
                            Log.i(TAG, metadata.logLine(result.message, result.durationMillis))
                        } else {
                            Log.w(TAG, metadata.logLine(result.message, result.durationMillis))
                        }

                        onResult(result)
                    }
                } catch (exception: IOException) {
                    val result = BarkForwardResult(
                        successful = false,
                        message = "异常：${exception.javaClass.name}",
                        durationMillis = elapsedMillis(submittedAtNanos),
                    )
                    Log.e(TAG, metadata.logLine(result.message, result.durationMillis), exception)
                    onResult(result)
                } catch (throwable: Throwable) {
                    val result = BarkForwardResult(
                        successful = false,
                        message = "异常：${throwable.javaClass.name}",
                        durationMillis = elapsedMillis(submittedAtNanos),
                    )
                    Log.e(TAG, metadata.logLine(result.message, result.durationMillis), throwable)
                    onResult(result)
                }
            }
        } catch (throwable: Throwable) {
            val result = BarkForwardResult(
                successful = false,
                message = "异常：${throwable.javaClass.name}",
                durationMillis = elapsedMillis(submittedAtNanos),
            )
            Log.e(TAG, metadata.logLine(result.message, result.durationMillis), throwable)
            onResult(result)
        }
    }

    private fun elapsedMillis(startedAtNanos: Long): Long {
        return TimeUnit.NANOSECONDS.toMillis(SystemClock.elapsedRealtimeNanos() - startedAtNanos)
    }
}
